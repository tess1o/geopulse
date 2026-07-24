package org.github.tess1o.geopulse.weather.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.weather.model.WeatherSampleTargetEntity;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;
import org.github.tess1o.geopulse.weather.model.WeatherTargetStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class WeatherSampleTargetRepository implements PanacheRepository<WeatherSampleTargetEntity> {

    @Inject
    EntityManager entityManager;

    public Optional<WeatherSampleTargetEntity> findExisting(
            UUID userId,
            String provider,
            double latitudeBucket,
            double longitudeBucket,
            Instant targetAt) {
        return find("""
                user.id = ?1
                and provider = ?2
                and latitudeBucket = ?3
                and longitudeBucket = ?4
                and targetAt = ?5
                """,
                userId, provider, latitudeBucket, longitudeBucket, targetAt)
                .firstResultOptional();
    }

    @Transactional
    public boolean enqueueIfMissing(
            UserEntity user,
            String provider,
            double latitude,
            double longitude,
            double latitudeBucket,
            double longitudeBucket,
            Instant targetAt,
            WeatherTargetSource source,
            int priority) {

        Optional<WeatherSampleTargetEntity> existing = findExisting(
                user.getId(), provider, latitudeBucket, longitudeBucket, targetAt);
        if (existing.isPresent()) {
            WeatherSampleTargetEntity target = existing.get();
            if (target.getStatus() == WeatherTargetStatus.FAILED && target.getAttempts() < 5) {
                target.setStatus(WeatherTargetStatus.PENDING);
                target.setNextAttemptAt(Instant.now());
                target.setPriority(Math.max(target.getPriority(), priority));
            } else if (target.getStatus() == WeatherTargetStatus.PENDING) {
                target.setPriority(Math.max(target.getPriority(), priority));
            }
            return false;
        }

        Instant now = Instant.now();
        int inserted = entityManager.createNativeQuery("""
                INSERT INTO weather_sample_targets (
                    user_id,
                    provider,
                    latitude,
                    longitude,
                    latitude_bucket,
                    longitude_bucket,
                    target_at,
                    source,
                    priority,
                    status,
                    attempts,
                    next_attempt_at,
                    created_at,
                    updated_at
                )
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, 0, ?11, ?11, ?11)
                ON CONFLICT ON CONSTRAINT uq_weather_targets_user_provider_bucket_time DO NOTHING
                """)
                .setParameter(1, user.getId())
                .setParameter(2, provider)
                .setParameter(3, latitude)
                .setParameter(4, longitude)
                .setParameter(5, latitudeBucket)
                .setParameter(6, longitudeBucket)
                .setParameter(7, targetAt)
                .setParameter(8, source.name())
                .setParameter(9, priority)
                .setParameter(10, WeatherTargetStatus.PENDING.name())
                .setParameter(11, now)
                .executeUpdate();
        return inserted > 0;
    }

    @Transactional
    public List<WeatherSampleTargetEntity> claimPendingTargets(int limit) {
        List<Long> ids = claimPendingTargetClaims(limit).stream()
                .map(WeatherSampleTargetClaim::id)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return find("id in ?1", ids).list();
    }

    @Transactional
    public List<WeatherSampleTargetClaim> claimPendingTargetClaims(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        Instant now = Instant.now();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                WITH claimed AS (
                    SELECT id
                    FROM weather_sample_targets
                    WHERE status = ?1
                      AND next_attempt_at <= ?2
                    ORDER BY priority DESC, target_at DESC, created_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?3
                )
                UPDATE weather_sample_targets target
                SET status = ?4,
                    locked_at = ?2,
                    last_error = NULL,
                    updated_at = ?2
                FROM claimed
                WHERE target.id = claimed.id
                RETURNING target.id,
                          target.user_id,
                          target.provider,
                          target.latitude,
                          target.longitude,
                          target.latitude_bucket,
                          target.longitude_bucket,
                          target.target_at,
                          target.source
                """)
                .setParameter(1, WeatherTargetStatus.PENDING.name())
                .setParameter(2, now)
                .setParameter(3, limit)
                .setParameter(4, WeatherTargetStatus.IN_PROGRESS.name())
                .getResultList();

        return rows.stream()
                .map(this::toClaim)
                .toList();
    }

    @Transactional
    public void markAttemptStarted(WeatherSampleTargetEntity target) {
        target.setLastAttemptAt(Instant.now());
        target.setAttempts(target.getAttempts() + 1);
    }

    @Transactional
    public void markAttemptStarted(long targetId) {
        findByIdOptional(targetId).ifPresent(this::markAttemptStarted);
    }

    @Transactional
    public void releaseForQuota(WeatherSampleTargetEntity target) {
        target.setStatus(WeatherTargetStatus.PENDING);
        target.setLockedAt(null);
        target.setNextAttemptAt(Instant.now().plusSeconds(10 * 60L));
        target.setLastError("Daily weather quota reserve reached");
    }

    @Transactional
    public void releaseForQuota(long targetId) {
        findByIdOptional(targetId).ifPresent(this::releaseForQuota);
    }

    @Transactional
    public void releaseUntil(long targetId, Instant nextAttemptAt, String reason) {
        findByIdOptional(targetId).ifPresent(target -> {
            target.setStatus(WeatherTargetStatus.PENDING);
            target.setLockedAt(null);
            target.setNextAttemptAt(nextAttemptAt == null ? Instant.now().plusSeconds(10 * 60L) : nextAttemptAt);
            target.setLastError(limitError(reason));
        });
    }

    @Transactional
    public void markCompleted(WeatherSampleTargetEntity target) {
        target.setStatus(WeatherTargetStatus.COMPLETED);
        target.setLockedAt(null);
        target.setCompletedAt(Instant.now());
        target.setLastError(null);
    }

    @Transactional
    public void markCompleted(long targetId) {
        findByIdOptional(targetId).ifPresent(this::markCompleted);
    }

    @Transactional
    public void markSkipped(WeatherSampleTargetEntity target, String reason) {
        target.setStatus(WeatherTargetStatus.SKIPPED);
        target.setLockedAt(null);
        target.setCompletedAt(Instant.now());
        target.setLastError(limitError(reason));
    }

    @Transactional
    public void markSkipped(long targetId, String reason) {
        findByIdOptional(targetId).ifPresent(target -> markSkipped(target, reason));
    }

    @Transactional
    public void markFailedOrRetry(WeatherSampleTargetEntity target, String errorSummary) {
        target.setLockedAt(null);
        target.setLastError(limitError(errorSummary));

        if (target.getAttempts() >= 5) {
            target.setStatus(WeatherTargetStatus.FAILED);
            return;
        }

        long delayMinutes = Math.min(180, (long) Math.pow(target.getAttempts(), 2) * 10L);
        target.setStatus(WeatherTargetStatus.PENDING);
        target.setNextAttemptAt(Instant.now().plusSeconds(delayMinutes * 60));
    }

    @Transactional
    public void markFailedOrRetry(long targetId, String errorSummary) {
        findByIdOptional(targetId).ifPresent(target -> markFailedOrRetry(target, errorSummary));
    }

    @Transactional
    public long cleanupCompletedTargets(Instant completedBefore, Instant failedBefore) {
        long completedDeleted = delete("""
                status in (?1, ?2)
                and completedAt is not null
                and completedAt < ?3
                """,
                WeatherTargetStatus.COMPLETED, WeatherTargetStatus.SKIPPED, completedBefore);
        long failedDeleted = delete("""
                status = ?1
                and updatedAt < ?2
                """,
                WeatherTargetStatus.FAILED, failedBefore);
        return completedDeleted + failedDeleted;
    }

    @Transactional
    public long resetFailedTargetsForRetry(Instant retryBefore) {
        if (retryBefore == null) {
            return 0;
        }

        Instant now = Instant.now();
        return entityManager.createNativeQuery("""
                UPDATE weather_sample_targets
                SET status = ?1,
                    locked_at = NULL,
                    completed_at = NULL,
                    next_attempt_at = ?2,
                    last_error = ?3,
                    updated_at = ?2
                WHERE status = ?4
                  AND COALESCE(last_attempt_at, updated_at) < ?5
                """)
                .setParameter(1, WeatherTargetStatus.PENDING.name())
                .setParameter(2, now)
                .setParameter(3, "Retrying stale failed weather target after cooldown")
                .setParameter(4, WeatherTargetStatus.FAILED.name())
                .setParameter(5, retryBefore)
                .executeUpdate();
    }

    @Transactional
    public long resetStaleInProgressTargets(Instant lockedBefore) {
        if (lockedBefore == null) {
            return 0;
        }

        Instant now = Instant.now();
        return entityManager.createNativeQuery("""
                UPDATE weather_sample_targets
                SET status = ?1,
                    locked_at = NULL,
                    next_attempt_at = ?2,
                    last_error = ?3,
                    updated_at = ?2
                WHERE status = ?4
                  AND locked_at IS NOT NULL
                  AND locked_at < ?5
                """)
                .setParameter(1, WeatherTargetStatus.PENDING.name())
                .setParameter(2, now)
                .setParameter(3, "Recovered stale in-progress weather target")
                .setParameter(4, WeatherTargetStatus.IN_PROGRESS.name())
                .setParameter(5, lockedBefore)
                .executeUpdate();
    }

    public long countAttemptsToday(Instant startOfDay) {
        return count("lastAttemptAt >= ?1", startOfDay);
    }

    public Map<String, Long> countByStatus() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (WeatherTargetStatus status : WeatherTargetStatus.values()) {
            result.put(status.name(), count("status = ?1", status));
        }
        return result;
    }

    public Instant oldestPendingTargetAt() {
        return find("status = ?1 order by targetAt asc", WeatherTargetStatus.PENDING)
                .firstResultOptional()
                .map(WeatherSampleTargetEntity::getTargetAt)
                .orElse(null);
    }

    public Instant newestPendingTargetAt() {
        return find("status = ?1 order by targetAt desc", WeatherTargetStatus.PENDING)
                .firstResultOptional()
                .map(WeatherSampleTargetEntity::getTargetAt)
                .orElse(null);
    }

    private String limitError(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private WeatherSampleTargetClaim toClaim(Object[] row) {
        return new WeatherSampleTargetClaim(
                ((Number) row[0]).longValue(),
                row[1] instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(row[1])),
                (String) row[2],
                ((Number) row[3]).doubleValue(),
                ((Number) row[4]).doubleValue(),
                ((Number) row[5]).doubleValue(),
                ((Number) row[6]).doubleValue(),
                TimestampUtils.getInstantSafe(row[7]),
                WeatherTargetSource.valueOf((String) row[8])
        );
    }
}

package org.github.tess1o.geopulse.mapmatching.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingStatus;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingSource;
import org.github.tess1o.geopulse.mapmatching.model.TimelineTripPathMatchEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TimelineTripPathMatchRepository implements PanacheRepository<TimelineTripPathMatchEntity> {

    @Inject
    EntityManager entityManager;

    public Optional<TimelineTripPathMatchEntity> findCurrent(UUID userId,
                                                            String provider,
                                                            String profile,
                                                            String configHash,
                                                            String inputHash) {
        return find("""
                user.id = ?1
                and provider = ?2
                and profile = ?3
                and configHash = ?4
                and inputHash = ?5
                """, userId, provider, profile, configHash, inputHash)
                .firstResultOptional();
    }

    @Transactional
    public void attachToTrip(TimelineTripPathMatchEntity target, TimelineTripEntity trip, MapMatchingSource source) {
        if (target == null || target.getId() == null || trip == null) {
            return;
        }
        MapMatchingSource effectiveSource = source == null ? MapMatchingSource.ON_DEMAND : source;
        entityManager.createNativeQuery("""
                UPDATE timeline_trip_path_matches
                SET trip_id = ?2,
                    priority = GREATEST(priority, ?3),
                    source = CASE
                        WHEN ?3 > priority THEN ?4
                        ELSE source
                    END,
                    updated_at = now()
                WHERE id = ?1
                """)
                .setParameter(1, target.getId())
                .setParameter(2, trip.getId())
                .setParameter(3, effectiveSource.priority())
                .setParameter(4, effectiveSource.name())
                .executeUpdate();
        target.setTrip(trip);
    }

    @Transactional
    public TimelineTripPathMatchEntity enqueueIfMissing(UserEntity user,
                                                        TimelineTripEntity trip,
                                                        String provider,
                                                        String profile,
                                                        String configHash,
                                                        String inputHash,
                                                        MapMatchingSource source) {
        MapMatchingSource effectiveSource = source == null ? MapMatchingSource.ON_DEMAND : source;
        @SuppressWarnings("unchecked")
        List<Number> ids = entityManager.createNativeQuery("""
                INSERT INTO timeline_trip_path_matches
                    (trip_id, user_id, provider, profile, config_hash, input_hash, status,
                     attempts, next_attempt_at, source, priority, created_at, updated_at)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, 0, now(), ?8, ?9, now(), now())
                ON CONFLICT (user_id, provider, profile, config_hash, input_hash) DO UPDATE
                SET trip_id = EXCLUDED.trip_id,
                    priority = GREATEST(timeline_trip_path_matches.priority, EXCLUDED.priority),
                    source = CASE
                        WHEN EXCLUDED.priority > timeline_trip_path_matches.priority THEN EXCLUDED.source
                        ELSE timeline_trip_path_matches.source
                    END,
                    updated_at = now()
                RETURNING id
                """)
                .setParameter(1, trip.getId())
                .setParameter(2, user.getId())
                .setParameter(3, provider)
                .setParameter(4, profile)
                .setParameter(5, configHash)
                .setParameter(6, inputHash)
                .setParameter(7, MapMatchingStatus.PENDING.name())
                .setParameter(8, effectiveSource.name())
                .setParameter(9, effectiveSource.priority())
                .getResultList();
        entityManager.flush();
        entityManager.clear();
        return findById(ids.getFirst().longValue());
    }

    @Transactional
    public List<TimelineTripPathMatchEntity> claimPending(int limit, boolean automaticEnabled, boolean backfillEnabled) {
        if (limit <= 0) {
            return List.of();
        }
        Instant now = Instant.now();
        @SuppressWarnings("unchecked")
        List<Number> ids = entityManager.createNativeQuery("""
                WITH claimed AS (
                    SELECT id
                    FROM timeline_trip_path_matches
                    WHERE status = ?1
                      AND next_attempt_at <= ?2
                      AND trip_id IS NOT NULL
                      AND (source = 'ON_DEMAND'
                           OR (source = 'AUTOMATIC' AND ?5)
                           OR (source = 'HISTORICAL' AND ?6))
                    ORDER BY priority DESC, created_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?3
                )
                UPDATE timeline_trip_path_matches target
                SET status = ?4,
                    locked_at = ?2,
                    last_error = NULL,
                    updated_at = ?2
                FROM claimed
                WHERE target.id = claimed.id
                RETURNING target.id
                """)
                .setParameter(1, MapMatchingStatus.PENDING.name())
                .setParameter(2, now)
                .setParameter(3, limit)
                .setParameter(4, MapMatchingStatus.PROCESSING.name())
                .setParameter(5, automaticEnabled)
                .setParameter(6, backfillEnabled)
                .getResultList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        SELECT target
                        FROM TimelineTripPathMatchEntity target
                        JOIN FETCH target.trip trip
                        JOIN FETCH target.user user
                        WHERE target.id IN :ids
                        """, TimelineTripPathMatchEntity.class)
                .setParameter("ids", ids.stream().map(Number::longValue).toList())
                .getResultList();
    }

    @Transactional
    public void markAttemptStarted(long id) {
        findByIdOptional(id).ifPresent(target -> {
            target.setAttempts(target.getAttempts() + 1);
            target.setLastAttemptAt(Instant.now());
        });
    }

    @Transactional
    public void markMatched(long id, String matchedSegmentsJson) {
        findByIdOptional(id).ifPresent(target -> {
            target.setStatus(MapMatchingStatus.MATCHED);
            target.setMatchedSegmentsJson(matchedSegmentsJson);
            target.setCompletedAt(Instant.now());
            target.setLockedAt(null);
            target.setLastError(null);
        });
    }

    @Transactional
    public void markSkipped(long id, String reason) {
        findByIdOptional(id).ifPresent(target -> {
            target.setStatus(MapMatchingStatus.SKIPPED);
            target.setCompletedAt(Instant.now());
            target.setLockedAt(null);
            target.setLastError(limitError(reason));
        });
    }

    @Transactional
    public void markFailed(long id, String reason) {
        findByIdOptional(id).ifPresent(target -> {
            target.setStatus(MapMatchingStatus.FAILED);
            target.setCompletedAt(Instant.now());
            target.setLockedAt(null);
            target.setLastError(limitError(reason));
        });
    }

    @Transactional
    public void markFailedOrRetry(long id, String reason, int maxAttempts) {
        findByIdOptional(id).ifPresent(target -> {
            target.setLockedAt(null);
            target.setLastError(limitError(reason));
            if (target.getAttempts() >= Math.max(1, maxAttempts)) {
                target.setStatus(MapMatchingStatus.FAILED);
                target.setCompletedAt(Instant.now());
                return;
            }
            long delayMinutes = Math.min(120, Math.max(1, target.getAttempts() * target.getAttempts() * 5L));
            target.setStatus(MapMatchingStatus.PENDING);
            target.setNextAttemptAt(Instant.now().plusSeconds(delayMinutes * 60));
        });
    }

    @Transactional
    public long resetStaleProcessing(Instant lockedBefore) {
        return entityManager.createNativeQuery("""
                UPDATE timeline_trip_path_matches
                SET status = ?1,
                    locked_at = NULL,
                    next_attempt_at = now(),
                    last_error = ?2,
                    updated_at = now()
                WHERE status = ?3
                  AND locked_at IS NOT NULL
                  AND locked_at < ?4
                """)
                .setParameter(1, MapMatchingStatus.PENDING.name())
                .setParameter(2, "Recovered stale processing map-match target")
                .setParameter(3, MapMatchingStatus.PROCESSING.name())
                .setParameter(4, lockedBefore)
                .executeUpdate();
    }

    public List<TimelineTripPathMatchEntity> findOwnedTargets(UUID userId, List<Long> targetIds) {
        if (userId == null || targetIds == null || targetIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                SELECT target FROM TimelineTripPathMatchEntity target
                LEFT JOIN FETCH target.trip trip
                WHERE target.user.id = :userId AND target.id IN :targetIds
                ORDER BY target.id
                """, TimelineTripPathMatchEntity.class)
                .setParameter("userId", userId)
                .setParameter("targetIds", targetIds)
                .getResultList();
    }

    public java.util.Map<String, Long> countByStatus() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(
                "SELECT status, count(*) FROM timeline_trip_path_matches GROUP BY status").getResultList();
        java.util.Map<String, Long> result = new java.util.LinkedHashMap<>();
        rows.forEach(row -> result.put(String.valueOf(row[0]), ((Number) row[1]).longValue()));
        return result;
    }

    public java.util.Map<String, Long> countBySource() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(
                "SELECT source, count(*) FROM timeline_trip_path_matches GROUP BY source").getResultList();
        java.util.Map<String, Long> result = new java.util.LinkedHashMap<>();
        rows.forEach(row -> result.put(String.valueOf(row[0]), ((Number) row[1]).longValue()));
        return result;
    }

    public Instant oldestQueuedAt() {
        Object value = entityManager.createNativeQuery("""
                SELECT min(created_at) FROM timeline_trip_path_matches
                WHERE status IN ('PENDING', 'PROCESSING')
                """).getSingleResult();
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        return null;
    }

    public Instant lastUpdatedAt() {
        Object value = entityManager.createNativeQuery(
                "SELECT max(updated_at) FROM timeline_trip_path_matches").getSingleResult();
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        return null;
    }

    @Transactional
    public long cleanupDetached(Instant terminalCutoff, Instant nonTerminalCutoff) {
        return ((Number) entityManager.createNativeQuery("""
                WITH deleted AS (
                    DELETE FROM timeline_trip_path_matches
                    WHERE trip_id IS NULL
                      AND (
                        (status IN ('MATCHED', 'SKIPPED', 'FAILED')
                         AND COALESCE(completed_at, updated_at, created_at) < ?1)
                        OR
                        (status IN ('PENDING', 'PROCESSING')
                         AND COALESCE(updated_at, created_at) < ?2)
                      )
                    RETURNING id
                )
                SELECT count(*) FROM deleted
                """)
                .setParameter(1, terminalCutoff)
                .setParameter(2, nonTerminalCutoff)
                .getSingleResult()).longValue();
    }

    private String limitError(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}

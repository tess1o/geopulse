package org.github.tess1o.geopulse.weather.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable dirty-range queue for historical weather reconciliation.
 *
 * <p>There is at most one row per user. Enqueueing expands that row to the union of its
 * current and new ranges and rewinds the cursor when necessary. A worker claims one bounded
 * range with {@code FOR UPDATE SKIP LOCKED}; successful completion advances the cursor or
 * removes the row. This makes timeline events cheap, restart-safe, and eventually complete
 * without loading a user's complete history into backend memory.</p>
 */
@ApplicationScoped
public class WeatherBackfillReconciliationRepository {

    @Inject
    EntityManager entityManager;

    public void enqueue(UUID userId, Instant rangeStart, Instant rangeEnd) {
        if (userId == null || rangeStart == null || rangeEnd == null || !rangeEnd.isAfter(rangeStart)) {
            return;
        }

        entityManager.createNativeQuery("""
                INSERT INTO weather_backfill_reconciliations (
                    user_id, range_start, range_end, cursor_at, created_at, updated_at
                )
                VALUES (?1, ?2, ?3, ?2, NOW(), NOW())
                ON CONFLICT (user_id) DO UPDATE
                SET range_start = LEAST(weather_backfill_reconciliations.range_start, EXCLUDED.range_start),
                    range_end = GREATEST(weather_backfill_reconciliations.range_end, EXCLUDED.range_end),
                    cursor_at = LEAST(weather_backfill_reconciliations.cursor_at, EXCLUDED.range_start),
                    updated_at = NOW()
                """)
                .setParameter(1, userId)
                .setParameter(2, rangeStart)
                .setParameter(3, rangeEnd)
                .executeUpdate();
    }

    public int enqueueAllActiveUsers(Instant rangeEnd) {
        if (rangeEnd == null) {
            return 0;
        }

        return entityManager.createNativeQuery("""
                INSERT INTO weather_backfill_reconciliations (
                    user_id, range_start, range_end, cursor_at, created_at, updated_at
                )
                SELECT timeline.user_id,
                       MIN(timeline.started_at),
                       ?1,
                       MIN(timeline.started_at),
                       NOW(),
                       NOW()
                FROM (
                    SELECT user_id, timestamp AS started_at
                    FROM timeline_stays
                    UNION ALL
                    SELECT user_id, timestamp AS started_at
                    FROM timeline_trips
                ) timeline
                JOIN users u ON u.id = timeline.user_id AND u.is_active = TRUE
                GROUP BY timeline.user_id
                HAVING ?1 > MIN(timeline.started_at)
                ON CONFLICT (user_id) DO UPDATE
                SET range_start = LEAST(weather_backfill_reconciliations.range_start, EXCLUDED.range_start),
                    range_end = GREATEST(weather_backfill_reconciliations.range_end, EXCLUDED.range_end),
                    cursor_at = LEAST(weather_backfill_reconciliations.cursor_at, EXCLUDED.range_start),
                    updated_at = NOW()
                """)
                .setParameter(1, rangeEnd)
                .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    public Optional<TimelineWeatherBounds> findTimelineBounds(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }

        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT MIN(started_at), MAX(ended_at)
                FROM (
                    SELECT timestamp AS started_at,
                           timestamp + (stay_duration * INTERVAL '1 second') AS ended_at
                    FROM timeline_stays
                    WHERE user_id = ?1
                    UNION ALL
                    SELECT timestamp AS started_at,
                           timestamp + (trip_duration * INTERVAL '1 second') AS ended_at
                    FROM timeline_trips
                    WHERE user_id = ?1
                ) timeline
                """)
                .setParameter(1, userId)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        Object[] row = rows.getFirst();
        Instant start = TimestampUtils.getInstantSafe(row[0]);
        Instant end = TimestampUtils.getInstantSafe(row[1]);
        if (start == null || end == null || !end.isAfter(start)) {
            return Optional.empty();
        }
        return Optional.of(new TimelineWeatherBounds(start, end));
    }

    @SuppressWarnings("unchecked")
    public WeatherBackfillReconciliation claimNext(Instant eligibleThrough, Duration chunkSize) {
        if (eligibleThrough == null || chunkSize == null || chunkSize.isZero() || chunkSize.isNegative()) {
            return null;
        }

        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT user_id,
                       cursor_at,
                       LEAST(cursor_at + (?1 * INTERVAL '1 second'), range_end, ?2) AS chunk_end,
                       range_end
                FROM weather_backfill_reconciliations
                WHERE cursor_at < LEAST(range_end, ?2)
                ORDER BY updated_at, user_id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
                """)
                .setParameter(1, chunkSize.toSeconds())
                .setParameter(2, eligibleThrough)
                .getResultList();

        if (rows.isEmpty()) {
            return null;
        }

        Object[] row = rows.getFirst();
        return new WeatherBackfillReconciliation(
                toUuid(row[0]),
                TimestampUtils.getInstantSafe(row[1]),
                TimestampUtils.getInstantSafe(row[2]),
                TimestampUtils.getInstantSafe(row[3])
        );
    }

    public void completeChunk(WeatherBackfillReconciliation reconciliation) {
        if (reconciliation.chunkEnd().compareTo(reconciliation.rangeEnd()) >= 0) {
            entityManager.createNativeQuery("""
                    DELETE FROM weather_backfill_reconciliations
                    WHERE user_id = ?1
                      AND cursor_at = ?2
                    """)
                    .setParameter(1, reconciliation.userId())
                    .setParameter(2, reconciliation.chunkStart())
                    .executeUpdate();
            return;
        }

        entityManager.createNativeQuery("""
                UPDATE weather_backfill_reconciliations
                SET cursor_at = ?1,
                    updated_at = NOW()
                WHERE user_id = ?2
                  AND cursor_at = ?3
                """)
                .setParameter(1, reconciliation.chunkEnd())
                .setParameter(2, reconciliation.userId())
                .setParameter(3, reconciliation.chunkStart())
                .executeUpdate();
    }

    public WeatherBackfillReconciliation clampClaimedRange(
            WeatherBackfillReconciliation claimed,
            TimelineWeatherBounds bounds,
            Duration chunkSize) {
        if (claimed == null || bounds == null) {
            return null;
        }
        Instant cursor = claimed.chunkStart().isAfter(bounds.start()) ? claimed.chunkStart() : bounds.start();
        Instant rangeEnd = claimed.rangeEnd().isBefore(bounds.end()) ? claimed.rangeEnd() : bounds.end();
        if (!rangeEnd.isAfter(cursor)) {
            delete(claimed.userId());
            return null;
        }

        entityManager.createNativeQuery("""
                UPDATE weather_backfill_reconciliations
                SET range_start = GREATEST(range_start, ?1),
                    cursor_at = ?2,
                    range_end = ?3,
                    updated_at = NOW()
                WHERE user_id = ?4
                """)
                .setParameter(1, bounds.start())
                .setParameter(2, cursor)
                .setParameter(3, rangeEnd)
                .setParameter(4, claimed.userId())
                .executeUpdate();
        Instant candidateEnd = cursor.plus(chunkSize);
        Instant chunkEnd = candidateEnd.isBefore(rangeEnd) ? candidateEnd : rangeEnd;
        return new WeatherBackfillReconciliation(claimed.userId(), cursor, chunkEnd, rangeEnd);
    }

    public void delete(UUID userId) {
        entityManager.createNativeQuery("DELETE FROM weather_backfill_reconciliations WHERE user_id = ?1")
                .setParameter(1, userId)
                .executeUpdate();
    }

    public boolean hasPendingRanges() {
        return Boolean.TRUE.equals(entityManager.createNativeQuery(
                "SELECT EXISTS (SELECT 1 FROM weather_backfill_reconciliations)")
                .getSingleResult());
    }

    @Transactional
    public long countPendingUserRanges() {
        return ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM weather_backfill_reconciliations")
                .getSingleResult()).longValue();
    }

    public ReconciliationSummary summary() {
        Object[] row = (Object[]) entityManager.createNativeQuery("""
                SELECT COUNT(*), MIN(range_start), MIN(cursor_at), MAX(range_end)
                FROM weather_backfill_reconciliations
                """).getSingleResult();
        return new ReconciliationSummary(
                ((Number) row[0]).longValue(),
                TimestampUtils.getInstantSafe(row[1]),
                TimestampUtils.getInstantSafe(row[2]),
                TimestampUtils.getInstantSafe(row[3])
        );
    }

    @SuppressWarnings("unchecked")
    public Optional<double[]> findNearestTripCoordinate(
            UUID userId,
            Instant tripStart,
            long durationSeconds,
            Instant targetAt) {
        Instant tripEnd = tripStart.plusSeconds(durationSeconds);
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT latitude, longitude
                FROM (
                    SELECT latitude, longitude, timestamp
                    FROM (
                        SELECT ST_Y(coordinates) AS latitude,
                               ST_X(coordinates) AS longitude,
                               timestamp
                        FROM gps_points
                        WHERE user_id = ?1
                          AND coordinates IS NOT NULL
                          AND timestamp >= ?2
                          AND timestamp <= ?4
                        ORDER BY timestamp DESC
                        LIMIT 1
                    ) point_before
                    UNION ALL
                    SELECT latitude, longitude, timestamp
                    FROM (
                        SELECT ST_Y(coordinates) AS latitude,
                               ST_X(coordinates) AS longitude,
                               timestamp
                        FROM gps_points
                        WHERE user_id = ?1
                          AND coordinates IS NOT NULL
                          AND timestamp >= ?4
                          AND timestamp <= ?3
                        ORDER BY timestamp ASC
                        LIMIT 1
                    ) point_after
                ) nearest_points
                ORDER BY ABS(EXTRACT(EPOCH FROM (timestamp - ?4)))
                LIMIT 1
                """)
                .setParameter(1, userId)
                .setParameter(2, tripStart)
                .setParameter(3, tripEnd)
                .setParameter(4, targetAt)
                .getResultList();

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        Object[] row = rows.getFirst();
        return Optional.of(new double[]{
                ((Number) row[0]).doubleValue(),
                ((Number) row[1]).doubleValue()
        });
    }

    private UUID toUuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value));
    }

    public record TimelineWeatherBounds(Instant start, Instant end) {
    }

    public record ReconciliationSummary(
            long pendingUserRanges,
            Instant oldestRangeStart,
            Instant oldestCursorAt,
            Instant newestRangeEnd
    ) {
    }
}

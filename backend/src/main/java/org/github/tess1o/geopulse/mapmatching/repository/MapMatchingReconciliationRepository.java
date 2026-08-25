package org.github.tess1o.geopulse.mapmatching.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingReconciliation;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingBackfillProgress;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class MapMatchingReconciliationRepository {

    @Inject
    EntityManager entityManager;

    @Transactional
    public void enqueue(UUID userId, Instant start, Instant end, MapMatchingSource source, Instant eligibleAt) {
        if (userId == null || start == null || end == null || source == null || !end.isAfter(start)) {
            return;
        }
        entityManager.createNativeQuery("""
                INSERT INTO map_matching_reconciliations
                    (user_id, source, range_start, range_end, cursor_at, cursor_trip_id,
                     eligible_at, total_trips, scanned_trips, completed_at, created_at, updated_at)
                VALUES (?1, ?2, ?3, ?4, ?3, 0, ?5,
                        (SELECT count(*) FROM timeline_trips
                         WHERE user_id = ?1 AND timestamp >= ?3 AND timestamp <= ?4),
                        0, NULL, now(), now())
                ON CONFLICT (user_id, source) DO UPDATE
                SET range_start = LEAST(map_matching_reconciliations.range_start, EXCLUDED.range_start),
                    range_end = GREATEST(map_matching_reconciliations.range_end, EXCLUDED.range_end),
                    cursor_at = LEAST(map_matching_reconciliations.cursor_at, EXCLUDED.range_start),
                    cursor_trip_id = CASE
                        WHEN EXCLUDED.range_start <= map_matching_reconciliations.cursor_at THEN 0
                        ELSE map_matching_reconciliations.cursor_trip_id
                    END,
                    total_trips = (
                        SELECT count(*) FROM timeline_trips
                        WHERE user_id = EXCLUDED.user_id
                          AND timestamp >= LEAST(map_matching_reconciliations.range_start, EXCLUDED.range_start)
                          AND timestamp <= GREATEST(map_matching_reconciliations.range_end, EXCLUDED.range_end)
                    ),
                    scanned_trips = CASE
                        WHEN EXCLUDED.range_start <= map_matching_reconciliations.cursor_at THEN 0
                        ELSE map_matching_reconciliations.scanned_trips
                    END,
                    completed_at = NULL,
                    eligible_at = GREATEST(map_matching_reconciliations.eligible_at, EXCLUDED.eligible_at),
                    updated_at = now()
                """)
                .setParameter(1, userId)
                .setParameter(2, source.name())
                .setParameter(3, start)
                .setParameter(4, end)
                .setParameter(5, eligibleAt == null ? Instant.now() : eligibleAt)
                .executeUpdate();
    }

    @Transactional
    public long enqueueAllTripOwners(MapMatchingSource source, Instant eligibleAt) {
        return entityManager.createNativeQuery("""
                INSERT INTO map_matching_reconciliations
                    (user_id, source, range_start, range_end, cursor_at, cursor_trip_id,
                     eligible_at, total_trips, scanned_trips, completed_at, created_at, updated_at)
                SELECT user_id, ?1, min(timestamp),
                       max(timestamp + (trip_duration * interval '1 second')),
                       min(timestamp), 0, ?2, count(*), 0, NULL, now(), now()
                FROM timeline_trips
                GROUP BY user_id
                ON CONFLICT (user_id, source) DO UPDATE
                SET range_start = LEAST(map_matching_reconciliations.range_start, EXCLUDED.range_start),
                    range_end = GREATEST(map_matching_reconciliations.range_end, EXCLUDED.range_end),
                    cursor_at = CASE
                        WHEN EXCLUDED.range_start < map_matching_reconciliations.range_start THEN EXCLUDED.range_start
                        ELSE map_matching_reconciliations.cursor_at
                    END,
                    cursor_trip_id = CASE
                        WHEN EXCLUDED.range_start < map_matching_reconciliations.range_start THEN 0
                        ELSE map_matching_reconciliations.cursor_trip_id
                    END,
                    total_trips = EXCLUDED.total_trips,
                    scanned_trips = CASE
                        WHEN EXCLUDED.range_start < map_matching_reconciliations.range_start THEN 0
                        ELSE LEAST(map_matching_reconciliations.scanned_trips, EXCLUDED.total_trips)
                    END,
                    completed_at = CASE
                        WHEN EXCLUDED.range_start < map_matching_reconciliations.range_start
                          OR EXCLUDED.range_end > map_matching_reconciliations.range_end THEN NULL
                        ELSE map_matching_reconciliations.completed_at
                    END,
                    eligible_at = LEAST(map_matching_reconciliations.eligible_at, EXCLUDED.eligible_at),
                    updated_at = now()
                """)
                .setParameter(1, source.name())
                .setParameter(2, eligibleAt == null ? Instant.now() : eligibleAt)
                .executeUpdate();
    }

    @Transactional
    public long restartAllTripOwners(MapMatchingSource source, Instant eligibleAt) {
        return entityManager.createNativeQuery("""
                INSERT INTO map_matching_reconciliations
                    (user_id, source, range_start, range_end, cursor_at, cursor_trip_id,
                     eligible_at, total_trips, scanned_trips, completed_at, created_at, updated_at)
                SELECT user_id, ?1, min(timestamp),
                       max(timestamp + (trip_duration * interval '1 second')),
                       min(timestamp), 0, ?2, count(*), 0, NULL, now(), now()
                FROM timeline_trips
                GROUP BY user_id
                ON CONFLICT (user_id, source) DO UPDATE
                SET range_start = EXCLUDED.range_start,
                    range_end = EXCLUDED.range_end,
                    cursor_at = EXCLUDED.cursor_at,
                    cursor_trip_id = 0,
                    eligible_at = EXCLUDED.eligible_at,
                    total_trips = EXCLUDED.total_trips,
                    scanned_trips = 0,
                    completed_at = NULL,
                    locked_at = NULL,
                    updated_at = now()
                """)
                .setParameter(1, source.name())
                .setParameter(2, eligibleAt == null ? Instant.now() : eligibleAt)
                .executeUpdate();
    }

    @Transactional
    public Optional<MapMatchingReconciliation> claimNext(boolean automaticEnabled, boolean backfillEnabled) {
        if (!automaticEnabled && !backfillEnabled) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                WITH candidate AS (
                    SELECT id
                    FROM map_matching_reconciliations
                    WHERE ((source = 'AUTOMATIC' AND ?1) OR (source = 'HISTORICAL' AND ?2))
                      AND completed_at IS NULL
                      AND eligible_at <= now()
                      AND (locked_at IS NULL OR locked_at < now() - interval '15 minutes')
                    ORDER BY CASE source WHEN 'AUTOMATIC' THEN 0 ELSE 1 END, cursor_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE map_matching_reconciliations r
                SET locked_at = now(), updated_at = now()
                FROM candidate
                WHERE r.id = candidate.id
                RETURNING r.id, r.user_id, r.source, r.range_start, r.range_end,
                          r.cursor_at, r.cursor_trip_id, r.eligible_at,
                          r.total_trips, r.scanned_trips, r.completed_at
                """)
                .setParameter(1, automaticEnabled)
                .setParameter(2, backfillEnabled)
                .getResultList();
        return rows.stream().findFirst().map(this::mapRow);
    }

    @Transactional
    public long refreshEligiblePendingTripTotals() {
        return ((Number) entityManager.createNativeQuery("""
                WITH counted AS (
                    SELECT r.id, count(t.id) AS total_trips
                    FROM map_matching_reconciliations r
                    LEFT JOIN timeline_trips t
                      ON t.user_id = r.user_id
                     AND t.timestamp >= r.range_start
                     AND t.timestamp <= r.range_end
                    WHERE r.completed_at IS NULL
                      AND r.eligible_at <= now()
                    GROUP BY r.id
                ),
                refreshed AS (
                    UPDATE map_matching_reconciliations r
                    SET total_trips = counted.total_trips,
                        scanned_trips = CASE
                            WHEN counted.total_trips = 0 THEN 0
                            ELSE LEAST(r.scanned_trips, counted.total_trips)
                        END,
                        completed_at = CASE
                            WHEN counted.total_trips = 0 THEN now()
                            ELSE r.completed_at
                        END,
                        locked_at = CASE
                            WHEN counted.total_trips = 0 THEN NULL
                            ELSE r.locked_at
                        END,
                        updated_at = now()
                    FROM counted
                    WHERE r.id = counted.id
                    RETURNING r.id
                )
                SELECT count(*) FROM refreshed
                """)
                .getSingleResult()).longValue();
    }

    @Transactional
    public void advance(MapMatchingReconciliation reconciliation, Instant nextCursor,
                        long nextCursorTripId, int scannedCount, boolean complete) {
        entityManager.createNativeQuery("""
                UPDATE map_matching_reconciliations
                SET cursor_at = ?2,
                    cursor_trip_id = ?3,
                    scanned_trips = CASE WHEN ?5 THEN total_trips ELSE LEAST(total_trips, scanned_trips + ?4) END,
                    completed_at = CASE WHEN ?5 THEN now() ELSE NULL END,
                    locked_at = NULL,
                    updated_at = now()
                WHERE id = ?1
                """)
                .setParameter(1, reconciliation.id())
                .setParameter(2, nextCursor)
                .setParameter(3, nextCursorTripId)
                .setParameter(4, Math.max(0, scannedCount))
                .setParameter(5, complete)
                .executeUpdate();
    }

    @Transactional
    public void release(long id) {
        entityManager.createNativeQuery("UPDATE map_matching_reconciliations SET locked_at = NULL, updated_at = now() WHERE id = ?1")
                .setParameter(1, id).executeUpdate();
    }

    public long countPending() {
        return ((Number) entityManager.createNativeQuery(
                        "SELECT count(*) FROM map_matching_reconciliations WHERE completed_at IS NULL")
                .getSingleResult()).longValue();
    }

    public java.util.Map<String, Long> countPendingBySource() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT source, count(*)
                FROM map_matching_reconciliations
                WHERE completed_at IS NULL
                GROUP BY source
                """).getResultList();
        java.util.Map<String, Long> result = new java.util.LinkedHashMap<>();
        rows.forEach(row -> result.put(String.valueOf(row[0]), ((Number) row[1]).longValue()));
        return result;
    }

    public Instant nextEligibleAt() {
        Object value = entityManager.createNativeQuery("""
                        SELECT min(eligible_at)
                        FROM map_matching_reconciliations
                        WHERE completed_at IS NULL
                        """)
                .getSingleResult();
        return toInstant(value);
    }

    public Instant oldestCursor() {
        Object value = entityManager.createNativeQuery(
                        "SELECT min(cursor_at) FROM map_matching_reconciliations WHERE completed_at IS NULL")
                .getSingleResult();
        return toInstant(value);
    }

    public MapMatchingBackfillProgress historicalProgress() {
        Object[] row = (Object[]) entityManager.createNativeQuery("""
                SELECT COALESCE(sum(total_trips), 0),
                       COALESCE(sum(LEAST(scanned_trips, total_trips)), 0),
                       count(*),
                       count(*) FILTER (WHERE completed_at IS NOT NULL),
                       max(updated_at)
                FROM map_matching_reconciliations
                WHERE source = 'HISTORICAL'
                """).getSingleResult();
        return new MapMatchingBackfillProgress(
                ((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(),
                ((Number) row[2]).longValue(),
                ((Number) row[3]).longValue(),
                toInstant(row[4]));
    }

    public Instant lastUpdatedAt() {
        Object value = entityManager.createNativeQuery(
                "SELECT max(updated_at) FROM map_matching_reconciliations").getSingleResult();
        return toInstant(value);
    }

    private MapMatchingReconciliation mapRow(Object[] row) {
        return new MapMatchingReconciliation(
                ((Number) row[0]).longValue(),
                (UUID) row[1],
                MapMatchingSource.valueOf(String.valueOf(row[2])),
                toInstant(row[3]), toInstant(row[4]), toInstant(row[5]),
                ((Number) row[6]).longValue(), toInstant(row[7]),
                ((Number) row[8]).longValue(), ((Number) row[9]).longValue(),
                toInstant(row[10]));
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        return null;
    }
}

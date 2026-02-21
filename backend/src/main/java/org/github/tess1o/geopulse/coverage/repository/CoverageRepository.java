package org.github.tess1o.geopulse.coverage.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.coverage.model.CoverageCell;
import org.github.tess1o.geopulse.coverage.model.CoverageProcessingCursor;
import org.github.tess1o.geopulse.coverage.model.CoverageStatusSnapshot;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CoverageRepository {

    private final EntityManager entityManager;

    @Inject
    public CoverageRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public CoverageProcessingCursor findProcessingCursor(UUID userId) {
        try {
            Object[] result = (Object[]) entityManager.createNativeQuery(
                            "SELECT last_processed, last_processed_point_id FROM coverage_state WHERE user_id = :userId")
                    .setParameter("userId", userId)
                    .getSingleResult();
            return new CoverageProcessingCursor(
                    TimestampUtils.getInstantSafe(result[0]),
                    result[1] == null ? null : ((Number) result[1]).longValue()
            );
        } catch (NoResultException e) {
            return null;
        }
    }

    public void upsertLastProcessed(UUID userId, CoverageProcessingCursor cursor) {
        entityManager.createNativeQuery(
                        "INSERT INTO coverage_state (user_id, last_processed, last_processed_point_id, updated_at) " +
                                "VALUES (:userId, :lastProcessed, :lastProcessedPointId, NOW()) " +
                                "ON CONFLICT (user_id) DO UPDATE SET " +
                                "last_processed = EXCLUDED.last_processed, " +
                                "last_processed_point_id = EXCLUDED.last_processed_point_id, " +
                                "updated_at = NOW()")
                .setParameter("userId", userId)
                .setParameter("lastProcessed", cursor.timestamp())
                .setParameter("lastProcessedPointId", cursor.pointId())
                .executeUpdate();
    }

    public List<UUID> findUsersWithNewCoverage(double maxAccuracyMeters, int staleTimeoutSeconds) {
        @SuppressWarnings("unchecked")
        List<Object> results = entityManager.createNativeQuery(
                        "SELECT DISTINCT gp.user_id " +
                                "FROM gps_points gp " +
                                "JOIN users u ON u.id = gp.user_id AND u.is_active = true AND u.coverage_enabled = true " +
                                "LEFT JOIN coverage_state cs ON cs.user_id = gp.user_id " +
                                "WHERE gp.coordinates IS NOT NULL " +
                                "  AND gp.timestamp IS NOT NULL " +
                                "  AND (gp.accuracy IS NULL OR gp.accuracy <= :maxAccuracy) " +
                                "  AND (cs.last_processed IS NULL " +
                                "       OR gp.timestamp > cs.last_processed " +
                                "       OR (gp.timestamp = cs.last_processed AND gp.id > COALESCE(cs.last_processed_point_id, -1))) " +
                                "  AND (cs.processing IS NULL OR cs.processing = false " +
                                "       OR cs.processing_started_at IS NULL " +
                                "       OR cs.processing_started_at < NOW() - (:staleTimeoutSeconds * INTERVAL '1 second'))")
                .setParameter("maxAccuracy", maxAccuracyMeters)
                .setParameter("staleTimeoutSeconds", staleTimeoutSeconds)
                .getResultList();

        return results.stream()
                .map(row -> UUID.fromString(row.toString()))
                .toList();
    }

    public CoverageStatusSnapshot findCoverageStatusSnapshot(UUID userId) {
        try {
            Object[] result = (Object[]) entityManager.createNativeQuery(
                            "SELECT u.coverage_enabled, " +
                                    "COALESCE(cs.processing, false), " +
                                    "EXISTS (SELECT 1 FROM coverage_cells cc WHERE cc.user_id = u.id), " +
                                    "cs.last_processed, " +
                                    "cs.processing_started_at " +
                                    "FROM users u " +
                                    "LEFT JOIN coverage_state cs ON cs.user_id = u.id " +
                                    "WHERE u.id = :userId")
                    .setParameter("userId", userId)
                    .getSingleResult();

            return new CoverageStatusSnapshot(
                    result[0] != null && (Boolean) result[0],
                    result[1] != null && (Boolean) result[1],
                    result[2] != null && (Boolean) result[2],
                    TimestampUtils.getInstantSafe(result[3]),
                    TimestampUtils.getInstantSafe(result[4])
            );
        } catch (NoResultException e) {
            return new CoverageStatusSnapshot(false, false, false, null, null);
        }
    }

    @Transactional
    public boolean tryStartProcessing(UUID userId, int staleTimeoutSeconds) {
        int updated = entityManager.createNativeQuery(
                        "INSERT INTO coverage_state " +
                                "(user_id, last_processed, last_processed_point_id, updated_at, processing, processing_started_at) " +
                                "VALUES (:userId, NULL, NULL, NOW(), true, NOW()) " +
                                "ON CONFLICT (user_id) DO UPDATE SET " +
                                "processing = true, " +
                                "processing_started_at = NOW(), " +
                                "updated_at = NOW() " +
                                "WHERE coverage_state.processing = false OR coverage_state.processing IS NULL " +
                                "   OR coverage_state.processing_started_at IS NULL " +
                                "   OR coverage_state.processing_started_at < NOW() - (:staleTimeoutSeconds * INTERVAL '1 second')")
                .setParameter("userId", userId)
                .setParameter("staleTimeoutSeconds", staleTimeoutSeconds)
                .executeUpdate();

        return updated > 0;
    }

    @Transactional
    public void finishProcessing(UUID userId) {
        entityManager.createNativeQuery(
                        "UPDATE coverage_state " +
                                "SET processing = false, " +
                                "processing_started_at = NULL, " +
                                "updated_at = NOW() " +
                                "WHERE user_id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
    }

    public CoverageProcessingCursor findProcessingUpperBound(UUID userId,
                                                             CoverageProcessingCursor lowerBound,
                                                             double maxAccuracyMeters) {
        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager.createNativeQuery(
                        "SELECT gp.timestamp, gp.id " +
                                "FROM gps_points gp " +
                                "WHERE gp.user_id = :userId " +
                                "  AND gp.coordinates IS NOT NULL " +
                                "  AND gp.timestamp IS NOT NULL " +
                                "  AND (gp.accuracy IS NULL OR gp.accuracy <= :maxAccuracy) " +
                                "  AND (CAST(:lowerTs AS timestamp) IS NULL " +
                                "       OR gp.timestamp > CAST(:lowerTs AS timestamp) " +
                                "       OR (gp.timestamp = CAST(:lowerTs AS timestamp) " +
                                "           AND gp.id > COALESCE(CAST(:lowerPointId AS bigint), -1))) " +
                                "ORDER BY gp.timestamp DESC, gp.id DESC " +
                                "LIMIT 1")
                .setParameter("userId", userId)
                .setParameter("maxAccuracy", maxAccuracyMeters)
                .setParameter("lowerTs", lowerBound == null ? null : lowerBound.timestamp())
                .setParameter("lowerPointId", lowerBound == null ? null : lowerBound.pointId())
                .getResultList();

        if (results.isEmpty()) {
            return null;
        }

        Object[] row = results.get(0);
        return new CoverageProcessingCursor(
                TimestampUtils.getInstantSafe(row[0]),
                row[1] == null ? null : ((Number) row[1]).longValue()
        );
    }

    public int upsertCoverageCells(UUID userId,
                                   CoverageProcessingCursor lowerBound,
                                   CoverageProcessingCursor upperBound,
                                   int gridMeters,
                                   int radiusMeters,
                                   int segmentizeMeters,
                                   int maxGapSeconds,
                                   double maxSpeedMps,
                                   double maxAccuracyMeters) {
        String sql = """
                WITH anchor AS (
                    SELECT gp.timestamp, gp.id, gp.coordinates
                    FROM gps_points gp
                    WHERE gp.user_id = :userId
                      AND gp.coordinates IS NOT NULL
                      AND gp.timestamp IS NOT NULL
                      AND (gp.accuracy IS NULL OR gp.accuracy <= :maxAccuracy)
                      AND CAST(:lowerTs AS timestamp) IS NOT NULL
                      AND (gp.timestamp < CAST(:lowerTs AS timestamp)
                           OR (gp.timestamp = CAST(:lowerTs AS timestamp)
                               AND gp.id <= COALESCE(CAST(:lowerPointId AS bigint), 9223372036854775807)))
                    ORDER BY gp.timestamp DESC, gp.id DESC
                    LIMIT 1
                ),
                new_points AS (
                    SELECT gp.timestamp,
                           gp.id,
                           ST_Transform(gp.coordinates, 3857) AS geom
                    FROM gps_points gp
                    WHERE gp.user_id = :userId
                      AND gp.timestamp IS NOT NULL
                      AND (CAST(:lowerTs AS timestamp) IS NULL
                           OR gp.timestamp > CAST(:lowerTs AS timestamp)
                           OR (gp.timestamp = CAST(:lowerTs AS timestamp)
                               AND gp.id > COALESCE(CAST(:lowerPointId AS bigint), -1)))
                      AND (gp.timestamp < CAST(:upperTs AS timestamp)
                           OR (gp.timestamp = CAST(:upperTs AS timestamp)
                               AND gp.id <= CAST(:upperPointId AS bigint)))
                      AND gp.coordinates IS NOT NULL
                      AND (gp.accuracy IS NULL OR gp.accuracy <= :maxAccuracy)
                ),
                points_for_segments AS (
                    SELECT timestamp, id, geom FROM new_points
                    UNION ALL
                    SELECT a.timestamp, a.id, ST_Transform(a.coordinates, 3857) AS geom FROM anchor a
                ),
                ordered AS (
                    SELECT
                        timestamp,
                        id,
                        geom,
                        LEAD(timestamp) OVER (ORDER BY timestamp, id) AS next_ts,
                        LEAD(geom) OVER (ORDER BY timestamp, id) AS next_geom
                    FROM points_for_segments
                ),
                segments AS (
                    SELECT
                        timestamp AS ts,
                        next_ts,
                        ST_MakeLine(geom, next_geom) AS seg,
                        ST_Distance(geom, next_geom) AS dist_m,
                        EXTRACT(EPOCH FROM (next_ts - timestamp)) AS dt_s
                    FROM ordered
                    WHERE next_ts IS NOT NULL
                ),
                filtered AS (
                    SELECT ts, next_ts, seg
                    FROM segments
                    WHERE dt_s > 0
                      AND dt_s <= :maxGapSeconds
                      AND (dist_m / dt_s) <= :maxSpeedMps
                ),
                segment_samples AS (
                    SELECT ts, next_ts, (ST_DumpPoints(ST_Segmentize(seg, :segmentizeMeters))).geom AS pt
                    FROM filtered
                ),
                segment_cells AS (
                    SELECT
                        floor((ST_X(pt) - :radiusMeters) / :gridMeters)::bigint AS min_x,
                        floor((ST_X(pt) + :radiusMeters) / :gridMeters)::bigint AS max_x,
                        floor((ST_Y(pt) - :radiusMeters) / :gridMeters)::bigint AS min_y,
                        floor((ST_Y(pt) + :radiusMeters) / :gridMeters)::bigint AS max_y,
                        ts,
                        next_ts
                    FROM segment_samples
                ),
                segment_expanded AS (
                    SELECT x AS cell_x, y AS cell_y, ts, next_ts
                    FROM segment_cells
                    JOIN LATERAL generate_series(min_x, max_x) AS x ON true
                    JOIN LATERAL generate_series(min_y, max_y) AS y ON true
                ),
                point_cells AS (
                    SELECT
                        floor((ST_X(geom) - :radiusMeters) / :gridMeters)::bigint AS min_x,
                        floor((ST_X(geom) + :radiusMeters) / :gridMeters)::bigint AS max_x,
                        floor((ST_Y(geom) - :radiusMeters) / :gridMeters)::bigint AS min_y,
                        floor((ST_Y(geom) + :radiusMeters) / :gridMeters)::bigint AS max_y,
                        timestamp AS ts,
                        timestamp AS next_ts
                    FROM new_points
                ),
                point_expanded AS (
                    SELECT x AS cell_x, y AS cell_y, ts, next_ts
                    FROM point_cells
                    JOIN LATERAL generate_series(min_x, max_x) AS x ON true
                    JOIN LATERAL generate_series(min_y, max_y) AS y ON true
                ),
                all_cells AS (
                    SELECT * FROM segment_expanded
                    UNION ALL
                    SELECT * FROM point_expanded
                ),
                aggregated AS (
                    SELECT
                        cell_x,
                        cell_y,
                        MIN(ts) AS first_seen,
                        MAX(next_ts) AS last_seen,
                        COUNT(*) AS seen_count
                    FROM all_cells
                    GROUP BY cell_x, cell_y
                )
                INSERT INTO coverage_cells (user_id, grid_m, cell_x, cell_y, first_seen, last_seen, seen_count)
                SELECT :userId, :gridMeters, cell_x, cell_y, first_seen, last_seen, seen_count
                FROM aggregated
                ON CONFLICT (user_id, grid_m, cell_x, cell_y)
                DO UPDATE SET
                  first_seen = LEAST(coverage_cells.first_seen, EXCLUDED.first_seen),
                  last_seen = GREATEST(coverage_cells.last_seen, EXCLUDED.last_seen),
                  seen_count = coverage_cells.seen_count + EXCLUDED.seen_count
                """;

        return entityManager.createNativeQuery(sql)
                .setParameter("userId", userId)
                .setParameter("lowerTs", lowerBound == null ? null : lowerBound.timestamp())
                .setParameter("lowerPointId", lowerBound == null ? null : lowerBound.pointId())
                .setParameter("upperTs", upperBound.timestamp())
                .setParameter("upperPointId", upperBound.pointId())
                .setParameter("gridMeters", gridMeters)
                .setParameter("radiusMeters", radiusMeters)
                .setParameter("segmentizeMeters", segmentizeMeters)
                .setParameter("maxGapSeconds", maxGapSeconds)
                .setParameter("maxSpeedMps", maxSpeedMps)
                .setParameter("maxAccuracy", maxAccuracyMeters)
                .executeUpdate();
    }

    public List<CoverageCell> findCoverageCells(UUID userId,
                                                double minLon,
                                                double minLat,
                                                double maxLon,
                                                double maxLat,
                                                int gridMeters,
                                                int limit) {
        String sql = """
                WITH bounds AS (
                    SELECT
                        ST_Transform(ST_SetSRID(ST_MakePoint(:minLon, :minLat), 4326), 3857) AS min_pt,
                        ST_Transform(ST_SetSRID(ST_MakePoint(:maxLon, :maxLat), 4326), 3857) AS max_pt
                ),
                filtered AS (
                    SELECT c.cell_x, c.cell_y, c.seen_count
                    FROM coverage_cells c, bounds b
                    WHERE c.user_id = :userId
                      AND c.grid_m = :gridMeters
                      AND c.cell_x BETWEEN floor(ST_X(b.min_pt) / :gridMeters) AND floor(ST_X(b.max_pt) / :gridMeters)
                      AND c.cell_y BETWEEN floor(ST_Y(b.min_pt) / :gridMeters) AND floor(ST_Y(b.max_pt) / :gridMeters)
                )
                SELECT
                    f.seen_count,
                    ST_Y(ST_Transform(ST_SetSRID(ST_MakePoint((f.cell_x + 0.5) * :gridMeters, (f.cell_y + 0.5) * :gridMeters), 3857), 4326)) AS latitude,
                    ST_X(ST_Transform(ST_SetSRID(ST_MakePoint((f.cell_x + 0.5) * :gridMeters, (f.cell_y + 0.5) * :gridMeters), 3857), 4326)) AS longitude
                FROM filtered f
                ORDER BY f.seen_count DESC, f.cell_x ASC, f.cell_y ASC
                LIMIT :limit
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager.createNativeQuery(sql)
                .setParameter("userId", userId)
                .setParameter("gridMeters", gridMeters)
                .setParameter("minLon", minLon)
                .setParameter("minLat", minLat)
                .setParameter("maxLon", maxLon)
                .setParameter("maxLat", maxLat)
                .setParameter("limit", limit)
                .getResultList();

        return results.stream()
                .map(row -> new CoverageCell(
                        ((Number) row[1]).doubleValue(),
                        ((Number) row[2]).doubleValue(),
                        gridMeters,
                        ((Number) row[0]).longValue()
                ))
                .toList();
    }

    @Transactional
    public int resetStuckProcessingStates() {
        return entityManager.createNativeQuery(
                        "UPDATE coverage_state " +
                                "SET processing = false, " +
                                "processing_started_at = NULL, " +
                                "updated_at = NOW() " +
                                "WHERE processing = true")
                .executeUpdate();
    }

    public long countCoverageCells(UUID userId, int gridMeters) {
        Object result = entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM coverage_cells WHERE user_id = :userId AND grid_m = :gridMeters")
                .setParameter("userId", userId)
                .setParameter("gridMeters", gridMeters)
                .getSingleResult();

        return result == null ? 0L : ((Number) result).longValue();
    }
}

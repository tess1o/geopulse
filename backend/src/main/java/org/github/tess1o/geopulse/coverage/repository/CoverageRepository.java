package org.github.tess1o.geopulse.coverage.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.github.tess1o.geopulse.coverage.model.CoverageCell;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CoverageRepository {

    private final EntityManager entityManager;

    @Inject
    public CoverageRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Instant findLastProcessed(UUID userId) {
        try {
            Object result = entityManager.createNativeQuery(
                            "SELECT last_processed FROM coverage_state WHERE user_id = :userId")
                    .setParameter("userId", userId)
                    .getSingleResult();
            return TimestampUtils.getInstantSafe(result);
        } catch (NoResultException e) {
            return null;
        }
    }

    public void upsertLastProcessed(UUID userId, Instant lastProcessed) {
        entityManager.createNativeQuery(
                        "INSERT INTO coverage_state (user_id, last_processed, updated_at) " +
                                "VALUES (:userId, :lastProcessed, NOW()) " +
                                "ON CONFLICT (user_id) DO UPDATE SET " +
                                "last_processed = EXCLUDED.last_processed, " +
                                "updated_at = NOW()")
                .setParameter("userId", userId)
                .setParameter("lastProcessed", lastProcessed)
                .executeUpdate();
    }

    public List<UUID> findUsersWithNewCoverage(double maxAccuracyMeters) {
        @SuppressWarnings("unchecked")
        List<Object> results = entityManager.createNativeQuery(
                        "SELECT DISTINCT gp.user_id " +
                                "FROM gps_points gp " +
                                "JOIN users u ON u.id = gp.user_id AND u.is_active = true " +
                                "LEFT JOIN coverage_state cs ON cs.user_id = gp.user_id " +
                                "WHERE gp.coordinates IS NOT NULL " +
                                "  AND (gp.accuracy IS NULL OR gp.accuracy <= :maxAccuracy) " +
                                "  AND (cs.last_processed IS NULL OR gp.timestamp > cs.last_processed)")
                .setParameter("maxAccuracy", maxAccuracyMeters)
                .getResultList();

        return results.stream()
                .map(row -> UUID.fromString(row.toString()))
                .toList();
    }

    public Instant findMaxGpsTimestamp(UUID userId, Instant since, double maxAccuracyMeters) {
        Object result = entityManager.createNativeQuery(
                        "SELECT MAX(gp.timestamp) " +
                                "FROM gps_points gp " +
                                "WHERE gp.user_id = :userId " +
                                "  AND gp.timestamp > COALESCE(CAST(:since AS timestamp), to_timestamp(0)) " +
                                "  AND gp.coordinates IS NOT NULL " +
                                "  AND (gp.accuracy IS NULL OR gp.accuracy <= :maxAccuracy)")
                .setParameter("userId", userId)
                .setParameter("since", since)
                .setParameter("maxAccuracy", maxAccuracyMeters)
                .getSingleResult();

        return TimestampUtils.getInstantSafe(result);
    }

    public int upsertCoverageCells(UUID userId,
                                   Instant since,
                                   int gridMeters,
                                   int radiusMeters,
                                   int segmentizeMeters,
                                   int maxGapSeconds,
                                   double maxSpeedMps,
                                   double maxAccuracyMeters) {
        String sql = """
                WITH anchor AS (
                    SELECT gp.timestamp, gp.coordinates
                    FROM gps_points gp
                    WHERE gp.user_id = :userId
                      AND CAST(:since AS timestamp) IS NOT NULL
                      AND gp.timestamp <= CAST(:since AS timestamp)
                    ORDER BY gp.timestamp DESC
                    LIMIT 1
                ),
                new_points AS (
                    SELECT gp.timestamp,
                           ST_Transform(gp.coordinates, 3857) AS geom
                    FROM gps_points gp
                    WHERE gp.user_id = :userId
                      AND gp.timestamp > COALESCE(CAST(:since AS timestamp), to_timestamp(0))
                      AND gp.coordinates IS NOT NULL
                      AND (gp.accuracy IS NULL OR gp.accuracy <= :maxAccuracy)
                ),
                points_for_segments AS (
                    SELECT timestamp, geom FROM new_points
                    UNION ALL
                    SELECT a.timestamp, ST_Transform(a.coordinates, 3857) AS geom FROM anchor a
                ),
                ordered AS (
                    SELECT
                        timestamp,
                        geom,
                        LEAD(timestamp) OVER (ORDER BY timestamp) AS next_ts,
                        LEAD(geom) OVER (ORDER BY timestamp) AS next_geom
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
                .setParameter("since", since)
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
                                                int gridMeters) {
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
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager.createNativeQuery(sql)
                .setParameter("userId", userId)
                .setParameter("gridMeters", gridMeters)
                .setParameter("minLon", minLon)
                .setParameter("minLat", minLat)
                .setParameter("maxLon", maxLon)
                .setParameter("maxLat", maxLat)
                .getResultList();

        return results.stream()
                .map(row -> CoverageCell.builder()
                        .seenCount(((Number) row[0]).longValue())
                        .latitude(((Number) row[1]).doubleValue())
                        .longitude(((Number) row[2]).doubleValue())
                        .gridMeters(gridMeters)
                        .build())
                .toList();
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

package org.github.tess1o.geopulse.coverage.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.coverage.CoverageDefaults;
import org.github.tess1o.geopulse.coverage.model.CoverageCells;
import org.github.tess1o.geopulse.coverage.model.CoverageProcessingCursor;
import org.github.tess1o.geopulse.coverage.model.CoverageStatusSnapshot;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    @Transactional
    public void deleteCoverageCells(UUID userId) {
        entityManager.createNativeQuery(
                        "DELETE FROM coverage_cells WHERE user_id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
    }

    @Transactional
    public void resetProcessingCursor(UUID userId) {
        entityManager.createNativeQuery(
                        "INSERT INTO coverage_state (user_id, last_processed, last_processed_point_id, updated_at) " +
                                "VALUES (:userId, NULL, NULL, NOW()) " +
                                "ON CONFLICT (user_id) DO UPDATE SET " +
                                "last_processed = NULL, " +
                                "last_processed_point_id = NULL, " +
                                "updated_at = NOW()")
                .setParameter("userId", userId)
                .executeUpdate();
    }

    public List<UUID> findUsersWithNewCoverage(double maxAccuracyMeters, int staleTimeoutSeconds) {
        @SuppressWarnings("unchecked")
        List<Object> results = entityManager.createNativeQuery(USERS_WITH_NEW_POINTS_SQL)
                .setParameter("maxAccuracy", maxAccuracyMeters)
                .setParameter("staleTimeoutSeconds", staleTimeoutSeconds)
                .getResultList();

        return results.stream()
                .map(row -> UUID.fromString(row.toString()))
                .toList();
    }

    /**
     * Enabled, idle users with a usable point after their cursor. Each user is one probe of the
     * {@code (user_id, timestamp, id)} index from the cursor; the lateral {@code LIMIT 1} keeps PostgreSQL from turning
     * it into a semi join over every user's points, which an {@code EXISTS} allows.
     */
    static final String USERS_WITH_NEW_POINTS_SQL = """
            SELECT u.id
            FROM users u
            LEFT JOIN coverage_state cs ON cs.user_id = u.id
            CROSS JOIN LATERAL (
                SELECT 1
                FROM gps_points gp
                WHERE gp.user_id = u.id
                  AND (gp.timestamp, gp.id) > (COALESCE(cs.last_processed, CAST('-infinity' AS timestamptz)),
                                               COALESCE(cs.last_processed_point_id, -1))
                  AND gp.coordinates IS NOT NULL
                  AND (gp.accuracy IS NULL OR gp.accuracy <= :maxAccuracy)
                ORDER BY gp.timestamp, gp.id
                LIMIT 1
            ) new_point
            WHERE u.is_active = true
              AND u.coverage_enabled = true
              AND (cs.processing IS NULL OR cs.processing = false
                   OR cs.processing_started_at IS NULL
                   OR cs.processing_started_at < NOW() - (:staleTimeoutSeconds * INTERVAL '1 second'))
            """;

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
                                "  AND (gp.timestamp, gp.id) > (COALESCE(CAST(:lowerTs AS timestamptz), CAST('-infinity' AS timestamptz)), " +
                                "                               COALESCE(CAST(:lowerPointId AS bigint), -1)) " +
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

    public CoverageProcessingCursor findBatchUpperBound(UUID userId,
                                                        CoverageProcessingCursor lowerBound,
                                                        double maxAccuracyMeters,
                                                        int batchSize) {
        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager.createNativeQuery(
                        "SELECT sub.timestamp, sub.id FROM (" +
                                "SELECT gp.timestamp, gp.id " +
                                "FROM gps_points gp " +
                                "WHERE gp.user_id = :userId " +
                                "  AND gp.coordinates IS NOT NULL " +
                                "  AND gp.timestamp IS NOT NULL " +
                                "  AND (gp.accuracy IS NULL OR gp.accuracy <= :maxAccuracy) " +
                                "  AND (gp.timestamp, gp.id) > (COALESCE(CAST(:lowerTs AS timestamptz), CAST('-infinity' AS timestamptz)), " +
                                "                               COALESCE(CAST(:lowerPointId AS bigint), -1)) " +
                                "ORDER BY gp.timestamp ASC, gp.id ASC " +
                                "LIMIT :batchSize" +
                                ") sub ORDER BY sub.timestamp DESC, sub.id DESC LIMIT 1")
                .setParameter("userId", userId)
                .setParameter("maxAccuracy", maxAccuracyMeters)
                .setParameter("lowerTs", lowerBound == null ? null : lowerBound.timestamp())
                .setParameter("lowerPointId", lowerBound == null ? null : lowerBound.pointId())
                .setParameter("batchSize", batchSize)
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

    /**
     * Session settings for the coverage batch transaction, set with {@code is_local} so they end with it. JIT
     * compilation costs more than these statements gain from it.
     */
    public void configureBatchSession() {
        entityManager.createNativeQuery("SELECT set_config('jit', 'off', true)").getSingleResult();
    }

    /**
     * Upserts the cells of {@code gridSizes}, one of {@link CoverageDefaults#GRID_SIZE_PASSES}, for the points after
     * {@code lowerBound} up to {@code upperBound}.
     *
     * @return the number of cells inserted or updated
     */
    public int upsertCoverageCells(UUID userId,
                                   CoverageProcessingCursor lowerBound,
                                   CoverageProcessingCursor upperBound,
                                   List<Integer> gridSizes) {
        Query query = entityManager.createNativeQuery(upsertCellsSql(gridSizes));
        bindUpsertCells(query, userId, lowerBound, upperBound);
        return query.executeUpdate();
    }

    static void bindUpsertCells(Query query,
                                UUID userId,
                                CoverageProcessingCursor lowerBound,
                                CoverageProcessingCursor upperBound) {
        query.setParameter("userId", userId)
                .setParameter("lowerTs", lowerBound == null ? null : lowerBound.timestamp())
                .setParameter("lowerPointId", lowerBound == null ? null : lowerBound.pointId())
                .setParameter("upperTs", upperBound.timestamp())
                .setParameter("upperPointId", upperBound.pointId())
                .setParameter("radiusMeters", CoverageDefaults.RADIUS_METERS)
                .setParameter("segmentizeMeters", CoverageDefaults.SEGMENTIZE_METERS)
                .setParameter("maxGapSeconds", CoverageDefaults.MAX_GAP_SECONDS)
                .setParameter("maxSpeedMps", CoverageDefaults.MAX_SPEED_MPS)
                .setParameter("maxAccuracy", CoverageDefaults.MAX_ACCURACY_METERS);
    }

    /**
     * The upsert for {@code gridSizes}. A single grid is a literal, so the rows the aggregate holds and spills are as
     * narrow as they can be; several grids come from a {@code VALUES} list joined to each sample, and the grid becomes
     * part of the group key.
     */
    static String upsertCellsSql(List<Integer> gridSizes) {
        String sampleBounds;
        String gridKey;
        String gridValue;
        if (gridSizes.size() == 1) {
            String grid = String.valueOf(gridSizes.getFirst());
            sampleBounds = "SELECT " + SAMPLE_BOUNDS.replace("{grid}", grid) + ", s.ts, s.next_ts\n"
                    + "                FROM samples s";
            gridKey = "";
            gridValue = grid;
        } else {
            String gridRows = gridSizes.stream()
                    .map(grid -> "(" + grid + ")")
                    .collect(Collectors.joining(", "));
            sampleBounds = "SELECT b.grid_m, b.min_x, b.max_x, b.min_y, b.max_y, s.ts, s.next_ts\n"
                    + "                FROM samples s\n"
                    + "                CROSS JOIN LATERAL (\n"
                    + "                    SELECT g.grid_m, " + SAMPLE_BOUNDS.replace("{grid}", "g.grid_m") + "\n"
                    + "                    FROM (VALUES " + gridRows + ") AS g(grid_m)\n"
                    + "                    OFFSET 0\n"
                    + "                ) AS b";
            gridKey = "grid_m, ";
            gridValue = "grid_m";
        }
        return UPSERT_CELLS_SQL
                .replace("{sampleBounds}", sampleBounds)
                .replace("{gridKey}", gridKey)
                .replace("{gridValue}", gridValue)
                .replace("{cellOffsets}", CELL_OFFSET_ROWS);
    }

    /** First and last cell per axis within the radius of sample {@code s}, in the grid {@code {grid}}. */
    private static final String SAMPLE_BOUNDS =
            "floor((s.x - :radiusMeters) / {grid})::bigint AS min_x, "
                    + "floor((s.x + :radiusMeters) / {grid})::bigint AS max_x, "
                    + "floor((s.y - :radiusMeters) / {grid})::bigint AS min_y, "
                    + "floor((s.y + :radiusMeters) / {grid})::bigint AS max_y";

    /**
     * {@code (VALUES (0), (1), ...)} rows of cell offsets. A sample marks the cells within the radius, which span at
     * most {@code ceil(2 * radius / grid)} cells past the first one per axis.
     */
    private static final String CELL_OFFSET_ROWS = IntStream.rangeClosed(0, (int) Math.ceil(
                    2.0 * CoverageDefaults.RADIUS_METERS / Collections.min(CoverageDefaults.GRID_SIZES_METERS_ORDERED)))
            .mapToObj(offset -> "(" + offset + ")")
            .collect(Collectors.joining(", "));

    /**
     * Segments between consecutive points (anchored to the last processed point) are sampled every
     * {@code segmentizeMeters}; each sample and each point marks the cells within {@code radiusMeters} in every grid
     * of the statement (see {@link #upsertCellsSql}). The samples stream through the grid expansion and are never
     * stored: the {@code OFFSET 0} keeps the lateral grid list from being flattened into a join that could rescan the
     * samples per grid. Cursor bounds are row comparisons, which stay index ranges in generic plans. Cell offsets come
     * from a small {@code VALUES} list instead of {@code generate_series}, whose 1000-row estimate made the planner
     * cost the statement in the billions.
     */
    private static final String UPSERT_CELLS_SQL = """
            WITH anchor AS (
                SELECT gp.timestamp, gp.id, gp.coordinates
                FROM gps_points gp
                WHERE gp.user_id = :userId
                  AND gp.coordinates IS NOT NULL
                  AND gp.timestamp IS NOT NULL
                  AND (gp.accuracy IS NULL OR gp.accuracy <= :maxAccuracy)
                  AND CAST(:lowerTs AS timestamptz) IS NOT NULL
                  AND (gp.timestamp, gp.id) <= (CAST(:lowerTs AS timestamptz),
                                                COALESCE(CAST(:lowerPointId AS bigint), 9223372036854775807))
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
                  AND (gp.timestamp, gp.id) > (COALESCE(CAST(:lowerTs AS timestamptz), CAST('-infinity' AS timestamptz)),
                                               COALESCE(CAST(:lowerPointId AS bigint), -1))
                  AND (gp.timestamp, gp.id) <= (CAST(:upperTs AS timestamptz), CAST(:upperPointId AS bigint))
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
            samples AS (
                SELECT ST_X(d.geom) AS x, ST_Y(d.geom) AS y, f.ts, f.next_ts
                FROM filtered f
                CROSS JOIN LATERAL ST_DumpPoints(ST_Segmentize(f.seg, :segmentizeMeters)) AS d
                UNION ALL
                SELECT ST_X(geom), ST_Y(geom), timestamp, timestamp
                FROM new_points
            ),
            sample_bounds AS (
                {sampleBounds}
            ),
            expanded AS (
                SELECT {gridKey}b.min_x + ox.o AS cell_x, b.min_y + oy.o AS cell_y, b.ts, b.next_ts
                FROM sample_bounds b
                JOIN (VALUES {cellOffsets}) AS ox(o) ON b.min_x + ox.o <= b.max_x
                JOIN (VALUES {cellOffsets}) AS oy(o) ON b.min_y + oy.o <= b.max_y
            ),
            aggregated AS (
                SELECT
                    {gridKey}cell_x,
                    cell_y,
                    MIN(ts) AS first_seen,
                    MAX(next_ts) AS last_seen,
                    COUNT(*) AS seen_count
                FROM expanded
                GROUP BY {gridKey}cell_x, cell_y
            )
            INSERT INTO coverage_cells (user_id, grid_m, cell_x, cell_y, first_seen, last_seen, seen_count)
            SELECT :userId, {gridValue}, cell_x, cell_y, first_seen, last_seen, seen_count
            FROM aggregated
            ON CONFLICT (user_id, grid_m, cell_x, cell_y)
            DO UPDATE SET
              first_seen = LEAST(coverage_cells.first_seen, EXCLUDED.first_seen),
              last_seen = GREATEST(coverage_cells.last_seen, EXCLUDED.last_seen),
              seen_count = coverage_cells.seen_count + EXCLUDED.seen_count
            """;

    /**
     * Cells inside the bounding box, in index order. At most {@code limit} cells are returned; {@code truncated} says
     * whether the box holds more.
     */
    public CoverageCells findCoverageCells(UUID userId,
                                           double minLon,
                                           double minLat,
                                           double maxLon,
                                           double maxLat,
                                           int gridMeters,
                                           int limit) {
        Query query = entityManager.createNativeQuery(CELLS_SQL);
        bindCellsQuery(query, userId, minLon, minLat, maxLon, maxLat, gridMeters, limit + 1);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        List<long[]> cells = results.stream()
                .limit(limit)
                .map(row -> new long[]{
                        ((Number) row[0]).longValue(),
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue()
                })
                .toList();
        return new CoverageCells(gridMeters, results.size() > limit, cells);
    }

    static void bindCellsQuery(Query query,
                               UUID userId,
                               double minLon,
                               double minLat,
                               double maxLon,
                               double maxLat,
                               int gridMeters,
                               int limit) {
        query.setParameter("userId", userId)
                .setParameter("gridMeters", gridMeters)
                .setParameter("minLon", minLon)
                .setParameter("minLat", minLat)
                .setParameter("maxLon", maxLon)
                .setParameter("maxLat", maxLat)
                .setParameter("limit", limit);
    }

    /** The bounds are cast to bigint, the column type, so the primary key can range-scan cell_x. */
    static final String CELLS_SQL = """
            WITH bounds AS (
                SELECT
                    ST_Transform(ST_SetSRID(ST_MakePoint(:minLon, :minLat), 4326), 3857) AS min_pt,
                    ST_Transform(ST_SetSRID(ST_MakePoint(:maxLon, :maxLat), 4326), 3857) AS max_pt
            )
            SELECT c.cell_x, c.cell_y, c.seen_count
            FROM coverage_cells c, bounds b
            WHERE c.user_id = :userId
              AND c.grid_m = :gridMeters
              AND c.cell_x BETWEEN CAST(floor(ST_X(b.min_pt) / :gridMeters) AS bigint)
                               AND CAST(floor(ST_X(b.max_pt) / :gridMeters) AS bigint)
              AND c.cell_y BETWEEN CAST(floor(ST_Y(b.min_pt) / :gridMeters) AS bigint)
                               AND CAST(floor(ST_Y(b.max_pt) / :gridMeters) AS bigint)
            ORDER BY c.cell_x, c.cell_y
            LIMIT :limit
            """;

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

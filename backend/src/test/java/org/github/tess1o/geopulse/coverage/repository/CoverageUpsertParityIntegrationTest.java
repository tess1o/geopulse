package org.github.tess1o.geopulse.coverage.repository;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.coverage.CoverageDefaults;
import org.github.tess1o.geopulse.coverage.model.CoverageProcessingCursor;
import org.github.tess1o.geopulse.coverage.service.CoverageBatchProcessor;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.TimelineSeeder;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The coverage upsert computes every grid in one statement. This checks it against the earlier statement, one per
 * grid, kept below as {@link #LEGACY_UPSERT_SQL}: two users get the same GPS history, one is processed the old way and
 * one the current way, and every cell (grid, x, y, first/last seen, count) must match.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class CoverageUpsertParityIntegrationTest {

    private static final double START_LAT = 50.4501;
    private static final double START_LON = 30.5234;
    private static final double METERS_PER_DEG_LAT = 111_320.0;
    private static final double METERS_PER_DEG_LON = METERS_PER_DEG_LAT * Math.cos(Math.toRadians(START_LAT));

    @Inject
    CoverageRepository coverageRepository;

    @Inject
    CoverageBatchProcessor coverageBatchProcessor;

    @Inject
    TimelineSeeder seed;

    @Inject
    EntityManager entityManager;

    @Test
    void fullBuildMatchesThePerGridStatement() {
        List<SeedPoint> history = history(Instant.parse("2026-03-01T08:00:00Z"), 1);
        UUID legacy = userWith(history);
        UUID current = userWith(history);

        processLegacy(legacy, CoverageDefaults.DEFAULT_BATCH_SIZE);
        processCurrent(current, CoverageDefaults.DEFAULT_BATCH_SIZE);

        assertSameCells(legacy, current);
    }

    @Test
    void multiBatchBuildMatchesThePerGridStatement() {
        List<SeedPoint> history = history(Instant.parse("2026-03-02T08:00:00Z"), 2);
        UUID legacy = userWith(history);
        UUID current = userWith(history);

        processLegacy(legacy, 37);
        processCurrent(current, 37);

        assertSameCells(legacy, current);
    }

    @Test
    void incrementalUpdateContinuesTheSegmentAcrossTheCursor() {
        Instant start = Instant.parse("2026-03-03T08:00:00Z");
        List<SeedPoint> history = history(start, 3);
        List<SeedPoint> firstPart = history.subList(0, history.size() / 2);
        List<SeedPoint> secondPart = history.subList(history.size() / 2, history.size());
        UUID legacy = userWith(firstPart);
        UUID current = userWith(firstPart);

        processLegacy(legacy, CoverageDefaults.DEFAULT_BATCH_SIZE);
        processCurrent(current, CoverageDefaults.DEFAULT_BATCH_SIZE);
        addPoints(legacy, secondPart);
        addPoints(current, secondPart);
        processLegacy(legacy, CoverageDefaults.DEFAULT_BATCH_SIZE);
        processCurrent(current, CoverageDefaults.DEFAULT_BATCH_SIZE);

        assertThat(countCells(current, 20)).isGreaterThan(100);
        assertSameCells(legacy, current);
    }

    private void processCurrent(UUID userId, int batchSize) {
        CoverageProcessingCursor cursor = coverageBatchProcessor.findProcessingCursor(userId);
        while (true) {
            CoverageProcessingCursor next = coverageBatchProcessor.processNextBatch(userId, cursor, batchSize);
            if (next == null) {
                return;
            }
            cursor = next;
        }
    }

    private void processLegacy(UUID userId, int batchSize) {
        CoverageProcessingCursor cursor = QuarkusTransaction.requiringNew()
                .call(() -> coverageRepository.findProcessingCursor(userId));
        while (true) {
            CoverageProcessingCursor lower = cursor;
            CoverageProcessingCursor next = QuarkusTransaction.requiringNew().call(() -> {
                CoverageProcessingCursor upper = coverageRepository.findBatchUpperBound(
                        userId, lower, CoverageDefaults.MAX_ACCURACY_METERS, batchSize);
                if (upper == null) {
                    return null;
                }
                // Only to keep the test fast; JIT does not change results.
                entityManager.createNativeQuery("SELECT set_config('jit', 'off', true)").getSingleResult();
                for (int grid : CoverageDefaults.GRID_SIZES_METERS_ORDERED) {
                    legacyUpsert(userId, lower, upper, grid);
                }
                coverageRepository.upsertLastProcessed(userId, upper);
                return upper;
            });
            if (next == null) {
                return;
            }
            cursor = next;
        }
    }

    private void legacyUpsert(UUID userId, CoverageProcessingCursor lower, CoverageProcessingCursor upper, int grid) {
        entityManager.createNativeQuery(LEGACY_UPSERT_SQL)
                .setParameter("userId", userId)
                .setParameter("lowerTs", lower == null ? null : lower.timestamp())
                .setParameter("lowerPointId", lower == null ? null : lower.pointId())
                .setParameter("upperTs", upper.timestamp())
                .setParameter("upperPointId", upper.pointId())
                .setParameter("gridMeters", grid)
                .setParameter("radiusMeters", CoverageDefaults.RADIUS_METERS)
                .setParameter("segmentizeMeters", CoverageDefaults.SEGMENTIZE_METERS)
                .setParameter("maxGapSeconds", CoverageDefaults.MAX_GAP_SECONDS)
                .setParameter("maxSpeedMps", CoverageDefaults.MAX_SPEED_MPS)
                .setParameter("maxAccuracy", CoverageDefaults.MAX_ACCURACY_METERS)
                .executeUpdate();
    }

    private void assertSameCells(UUID legacy, UUID current) {
        List<String> expected = cells(legacy);
        assertThat(expected).isNotEmpty();
        for (int grid : CoverageDefaults.GRID_SIZES_METERS_ORDERED) {
            assertThat(countCells(legacy, grid)).as("grid %d has cells", grid).isPositive();
        }
        assertThat(cells(current)).containsExactlyElementsOf(expected);
    }

    @SuppressWarnings("unchecked")
    private List<String> cells(UUID userId) {
        return QuarkusTransaction.requiringNew().call(() -> ((List<Object[]>) entityManager.createNativeQuery(
                        "SELECT grid_m, cell_x, cell_y, first_seen, last_seen, seen_count FROM coverage_cells " +
                                "WHERE user_id = :userId ORDER BY grid_m, cell_x, cell_y")
                .setParameter("userId", userId)
                .getResultList())
                .stream()
                .map(row -> row[0] + "," + row[1] + "," + row[2] + "," + row[3] + "," + row[4] + "," + row[5])
                .toList());
    }

    private long countCells(UUID userId, int grid) {
        return QuarkusTransaction.requiringNew().call(() -> coverageRepository.countCoverageCells(userId, grid));
    }

    private UUID userWith(List<SeedPoint> points) {
        UserEntity user = seed.user("coverage-parity", "UTC");
        addPoints(user.getId(), points);
        return user.getId();
    }

    private void addPoints(UUID userId, List<SeedPoint> points) {
        QuarkusTransaction.requiringNew().run(() -> {
            UserEntity user = entityManager.find(UserEntity.class, userId);
            for (SeedPoint p : points) {
                seed.gpsPoint(user, p.timestamp(), p.lat(), p.lon(), p.accuracy());
            }
        });
    }

    /**
     * A tracker-like day: a walk, a stay with jitter, a gap longer than {@code MAX_GAP_SECONDS}, a car trip whose
     * segments are longer than the sampling step, a speed spike, two points with the same timestamp, and points
     * without or above the accuracy limit.
     */
    private static List<SeedPoint> history(Instant start, long randomSeed) {
        Random random = new Random(randomSeed);
        List<SeedPoint> points = new ArrayList<>();
        Instant t = start;
        double east = 0;
        double north = 0;

        for (int i = 0; i < 120; i++) {
            t = t.plusSeconds(10);
            east += 14 + random.nextGaussian() * 3;
            north += random.nextGaussian() * 4;
            points.add(point(t, east, north, i % 17 == 0 ? null : 5.0 + random.nextDouble() * 20));
        }
        points.add(point(t, east + 3, north + 3, 8.0));

        for (int i = 0; i < 40; i++) {
            t = t.plusSeconds(30);
            points.add(point(t, east + random.nextGaussian() * 8, north + random.nextGaussian() * 8,
                    i % 9 == 0 ? CoverageDefaults.MAX_ACCURACY_METERS + 30 : 12.0));
        }

        t = t.plusSeconds(CoverageDefaults.MAX_GAP_SECONDS + 300);
        east += 400;
        for (int i = 0; i < 80; i++) {
            t = t.plusSeconds(15);
            east += 250 + random.nextGaussian() * 30;
            north += 120 + random.nextGaussian() * 30;
            points.add(point(t, east, north, 10.0));
            if (i == 40) {
                t = t.plusSeconds(10);
                points.add(point(t, east + 5_000, north, 10.0));
            }
        }

        for (int i = 0; i < 60; i++) {
            t = t.plusSeconds(8);
            east -= 10 + random.nextGaussian() * 3;
            north -= 6 + random.nextGaussian() * 3;
            points.add(point(t, east, north, 6.0));
        }
        return points;
    }

    private static SeedPoint point(Instant t, double eastMeters, double northMeters, Double accuracy) {
        return new SeedPoint(t, START_LAT + northMeters / METERS_PER_DEG_LAT,
                START_LON + eastMeters / METERS_PER_DEG_LON, accuracy);
    }

    private record SeedPoint(Instant timestamp, double lat, double lon, Double accuracy) {
    }

    /** The per-grid upsert as it was before the rewrite; also the temp-space baseline in the query plan test. */
    static final String LEGACY_UPSERT_SQL = """
            WITH anchor AS (
                SELECT gp.timestamp, gp.id, gp.coordinates
                FROM gps_points gp
                WHERE gp.user_id = :userId
                  AND gp.coordinates IS NOT NULL
                  AND gp.timestamp IS NOT NULL
                  AND (gp.accuracy IS NULL OR gp.accuracy <= :maxAccuracy)
                  AND CAST(:lowerTs AS timestamptz) IS NOT NULL
                  AND (gp.timestamp < CAST(:lowerTs AS timestamptz)
                       OR (gp.timestamp = CAST(:lowerTs AS timestamptz)
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
                  AND (CAST(:lowerTs AS timestamptz) IS NULL
                       OR gp.timestamp > CAST(:lowerTs AS timestamptz)
                       OR (gp.timestamp = CAST(:lowerTs AS timestamptz)
                           AND gp.id > COALESCE(CAST(:lowerPointId AS bigint), -1)))
                  AND (gp.timestamp < CAST(:upperTs AS timestamptz)
                       OR (gp.timestamp = CAST(:upperTs AS timestamptz)
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
}

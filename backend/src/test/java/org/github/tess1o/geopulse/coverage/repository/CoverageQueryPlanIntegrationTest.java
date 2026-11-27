package org.github.tess1o.geopulse.coverage.repository;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.coverage.CoverageDefaults;
import org.github.tess1o.geopulse.coverage.model.CoverageProcessingCursor;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.TimelineSeeder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Query plans of the coverage statements: what they must and must not do regardless of data size. */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class CoverageQueryPlanIntegrationTest {

    @Inject
    CoverageRepository coverageRepository;

    @Inject
    TimelineSeeder seed;

    @Inject
    EntityManager entityManager;

    @Test
    void cellsQueryRangeScansCellXInThePrimaryKey() {
        UUID userId = seed.user("coverage-plan-cells", "UTC").getId();

        List<String> plan = inRolledBackTransaction(() -> {
            entityManager.createNativeQuery(
                            "INSERT INTO coverage_cells (user_id, grid_m, cell_x, cell_y, first_seen, last_seen, seen_count) " +
                                    "SELECT :userId, 20, x, y, NOW(), NOW(), 1 " +
                                    "FROM generate_series(169900, 170100) x, generate_series(65000, 65040) y")
                    .setParameter("userId", userId)
                    .executeUpdate();
            // A small table would be scanned sequentially; the question is whether the index can bound cell_x.
            entityManager.createNativeQuery("SELECT set_config('enable_seqscan', 'off', true)").getSingleResult();
            Query explain = entityManager.createNativeQuery("EXPLAIN " + CoverageRepository.CELLS_SQL);
            CoverageRepository.bindCellsQuery(explain, userId, 30.52, 50.44, 30.55, 50.46, 20, 100);
            return planLines(explain);
        });

        assertThat(plan).as(String.join("\n", plan))
                .anySatisfy(line -> assertThat(line).contains("Index Cond").contains("cell_x"));
    }

    @Test
    void usersWithNewPointsProbeTheKeysetIndexFromTheCursor() {
        List<String> plan = inRolledBackTransaction(() -> {
            entityManager.createNativeQuery("SELECT set_config('enable_seqscan', 'off', true)").getSingleResult();
            return planLines(entityManager.createNativeQuery("EXPLAIN " + CoverageRepository.USERS_WITH_NEW_POINTS_SQL)
                    .setParameter("maxAccuracy", CoverageDefaults.MAX_ACCURACY_METERS)
                    .setParameter("staleTimeoutSeconds", CoverageDefaults.PROCESSING_STALE_TIMEOUT_SECONDS));
        });

        assertThat(plan).as(String.join("\n", plan)).anySatisfy(line -> assertThat(line)
                .contains("Index Cond").contains("user_id = u.id").contains("ROW(\"timestamp\", id) >"));
    }

    @Test
    void coverageCellsHasOnlyThePrimaryKeyIndex() {
        @SuppressWarnings("unchecked")
        List<String> indexes = QuarkusTransaction.requiringNew().call(() -> entityManager.createNativeQuery(
                        "SELECT indexname FROM pg_indexes WHERE tablename = 'coverage_cells' ORDER BY indexname")
                .getResultList());

        assertThat(indexes).containsExactly("pk_coverage_cells");
    }

    /**
     * A 30-point incremental update on a 20,000-point history, as in the benchmark where each such statement spent
     * ~380 ms in JIT compilation for ~1 ms of work. The history is analyzed so the planner sees realistic row counts.
     * JIT stays on here: the statement's own estimates must keep it under {@code jit_above_cost}.
     */
    @Test
    void incrementalUpsertIsCheapEnoughNotToBeJitCompiled() {
        UUID userId = userWith20000AnalyzedPoints("coverage-plan-jit");
        CoverageProcessingCursor processed = QuarkusTransaction.requiringNew().call(() ->
                coverageRepository.findBatchUpperBound(userId, null, CoverageDefaults.MAX_ACCURACY_METERS, 19_970));

        List<String> plan = inRolledBackTransaction(() -> {
            entityManager.createNativeQuery("SELECT set_config('jit', 'on', true)").getSingleResult();
            CoverageProcessingCursor upper = coverageRepository.findBatchUpperBound(
                    userId, processed, CoverageDefaults.MAX_ACCURACY_METERS, 1_000);
            List<String> lines = new ArrayList<>();
            for (List<Integer> gridSizes : CoverageDefaults.GRID_SIZE_PASSES) {
                Query explain = entityManager.createNativeQuery(
                        "EXPLAIN (ANALYZE) " + CoverageRepository.upsertCellsSql(gridSizes));
                CoverageRepository.bindUpsertCells(explain, userId, processed, upper);
                lines.addAll(planLines(explain));
            }
            return lines;
        });

        assertThat(plan).as(String.join("\n", plan)).isNotEmpty().noneMatch(line -> line.startsWith("JIT:"));
    }

    /**
     * pgJDBC switches to a server-side prepared statement after five executions, and PostgreSQL may then use a generic
     * plan. The new points must still be an index range from the lower cursor to the upper one there.
     */
    @Test
    void upsertGenericPlanRangeScansFromTheCursor() {
        UUID userId = userWith20000AnalyzedPoints("coverage-plan-generic");
        Map<String, String> arguments = Map.of(
                "userId", "'" + userId + "'",
                "lowerTs", "'2026-03-03 07:00:00+00'", "upperTs", "'2026-03-03 08:00:00+00'",
                "lowerPointId", "1", "upperPointId", "2",
                "radiusMeters", String.valueOf(CoverageDefaults.RADIUS_METERS),
                "segmentizeMeters", String.valueOf(CoverageDefaults.SEGMENTIZE_METERS),
                "maxGapSeconds", String.valueOf(CoverageDefaults.MAX_GAP_SECONDS),
                "maxSpeedMps", String.valueOf(CoverageDefaults.MAX_SPEED_MPS),
                "maxAccuracy", String.valueOf(CoverageDefaults.MAX_ACCURACY_METERS));

        List<String> plan = inRolledBackTransaction(() -> {
            entityManager.createNativeQuery("SELECT set_config('plan_cache_mode', 'force_generic_plan', true)")
                    .getSingleResult();
            List<String> lines = new ArrayList<>();
            for (List<Integer> gridSizes : CoverageDefaults.GRID_SIZE_PASSES) {
                lines.addAll(explainPrepared(CoverageRepository.upsertCellsSql(gridSizes), arguments));
            }
            return lines;
        });

        assertThat(plan).as(String.join("\n", plan)).anySatisfy(line -> assertThat(line)
                .contains("Index Cond").contains("COALESCE($").contains("'-infinity'").contains("<= $"));
    }

    /**
     * Coverage was batched because a single expansion filled pgsql_tmp (#553). Splitting the grids into passes must not
     * need more temp space than the per-grid statement it replaced did for its largest grid, the 20 m one, and nothing
     * may store the samples. Measured where both spill: small work_mem, a tracker-like history.
     */
    @Test
    void noPassWritesMoreTempThanTheFinestGridStatementDid() {
        UUID userId = userWithTrackerHistory("coverage-plan-temp", 20_000);
        CoverageProcessingCursor upper = QuarkusTransaction.requiringNew().call(() ->
                coverageRepository.findBatchUpperBound(userId, null, CoverageDefaults.MAX_ACCURACY_METERS, 20_000));

        for (String workMem : List.of("1MB", "8MB")) {
            List<String> legacy = inRolledBackTransaction(() -> {
                entityManager.createNativeQuery("SELECT set_config('work_mem', '" + workMem + "', true)").getSingleResult();
                coverageRepository.configureBatchSession();
                Query explain = entityManager.createNativeQuery(
                        "EXPLAIN (ANALYZE, BUFFERS) " + CoverageUpsertParityIntegrationTest.LEGACY_UPSERT_SQL);
                CoverageRepository.bindUpsertCells(explain, userId, null, upper);
                explain.setParameter("gridMeters", CoverageDefaults.GRID_SIZES_METERS_ORDERED.getFirst());
                return planLines(explain);
            });
            long legacyTemp = tempBlocksWritten(legacy);
            assertThat(legacyTemp).as("the 20 m statement spills at work_mem %s", workMem).isPositive();

            for (List<Integer> gridSizes : CoverageDefaults.GRID_SIZE_PASSES) {
                List<String> plan = inRolledBackTransaction(() -> {
                    entityManager.createNativeQuery("SELECT set_config('work_mem', '" + workMem + "', true)")
                            .getSingleResult();
                    coverageRepository.configureBatchSession();
                    Query explain = entityManager.createNativeQuery(
                            "EXPLAIN (ANALYZE, BUFFERS) " + CoverageRepository.upsertCellsSql(gridSizes));
                    CoverageRepository.bindUpsertCells(explain, userId, null, upper);
                    return planLines(explain);
                });
                assertThat(tempBlocksWritten(plan)).as("pass %s at work_mem %s\n%s", gridSizes, workMem,
                        String.join("\n", plan)).isLessThanOrEqualTo(legacyTemp);
                assertThat(plan).noneMatch(line -> line.contains("CTE samples"));
            }
        }
    }

    /** Temp blocks written by the whole statement: the first {@code Buffers} line belongs to the top plan node. */
    private static long tempBlocksWritten(List<String> plan) {
        String buffers = plan.stream().filter(line -> line.trim().startsWith("Buffers:")).findFirst().orElseThrow();
        Matcher matcher = Pattern.compile("temp (?:read=\\d+ )?written=(\\d+)").matcher(buffers);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : 0;
    }

    @Test
    void batchSessionTurnsJitOffAndLeavesWorkMemAlone() {
        String settings = inRolledBackTransaction(() -> {
            entityManager.createNativeQuery("SELECT set_config('work_mem', '4MB', true)").getSingleResult();
            coverageRepository.configureBatchSession();
            return (String) entityManager.createNativeQuery(
                            "SELECT current_setting('jit') || ' ' || current_setting('work_mem')")
                    .getSingleResult();
        });

        assertThat(settings).isEqualTo("off 4MB");
    }

    private UUID userWith20000AnalyzedPoints(String prefix) {
        UUID userId = seed.user(prefix, "UTC").getId();
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(
                        "INSERT INTO gps_points (user_id, device_id, coordinates, timestamp, accuracy, source_type, created_at) " +
                                "SELECT :userId, 'seeded-device', " +
                                "ST_SetSRID(ST_MakePoint(30.52 + (i % 400) * 0.0002, 50.45 + (i / 400) * 0.0002), 4326), " +
                                "TIMESTAMPTZ '2026-03-01 00:00:00+00' + i * INTERVAL '10 seconds', 5.0, 'OWNTRACKS', NOW() " +
                                "FROM generate_series(1, 20000) i")
                .setParameter("userId", userId)
                .executeUpdate());
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("ANALYZE gps_points").executeUpdate());
        return userId;
    }

    /**
     * A tracker-like history: walks, drives whose segments are sampled many times, and stays, in blocks of 1,000 points
     * every 10 s, turning every 200 points so most of the track crosses new cells.
     */
    private UUID userWithTrackerHistory(String prefix, int points) {
        UUID userId = seed.user(prefix, "UTC").getId();
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
                        WITH steps AS (
                            SELECT i,
                                   CASE (i / 1000) % 3 WHEN 0 THEN 14.0 WHEN 1 THEN 150.0 ELSE 2.0 END AS step,
                                   (i / 200) * 1.3 + (i % 7) * 0.05 AS heading
                            FROM generate_series(1, :points) i
                        ),
                        positions AS (
                            SELECT i,
                                   SUM(step * cos(heading)) OVER w AS dx,
                                   SUM(step * sin(heading)) OVER w AS dy
                            FROM steps
                            WINDOW w AS (ORDER BY i)
                        )
                        INSERT INTO gps_points (user_id, device_id, coordinates, timestamp, accuracy, source_type, created_at)
                        SELECT :userId, 'seeded-device',
                               ST_SetSRID(ST_MakePoint(30.52 + dx / 71000.0, 50.45 + dy / 111320.0), 4326),
                               TIMESTAMPTZ '2026-01-01 00:00:00+00' + i * INTERVAL '10 seconds', 5.0, 'OWNTRACKS', NOW()
                        FROM positions
                        """)
                .setParameter("points", points)
                .setParameter("userId", userId)
                .executeUpdate());
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("ANALYZE gps_points").executeUpdate());
        return userId;
    }

    /** {@code EXPLAIN EXECUTE} of {@code sql} prepared with its named parameters as {@code $n} placeholders. */
    @SuppressWarnings("unchecked")
    private List<String> explainPrepared(String sql, Map<String, String> arguments) {
        List<String> names = new ArrayList<>();
        String prepared = Pattern.compile("(?<![:\\w]):(\\w+)").matcher(sql).replaceAll(match -> {
            if (!names.contains(match.group(1))) {
                names.add(match.group(1));
            }
            return "\\$" + (names.indexOf(match.group(1)) + 1);
        });
        String types = names.stream().map(PARAMETER_TYPES::get).collect(Collectors.joining(", "));
        String values = names.stream().map(arguments::get).collect(Collectors.joining(", "));

        entityManager.createNativeQuery("PREPARE coverage_plan_test (" + types + ") AS " + prepared).executeUpdate();
        try {
            return ((List<Object>) entityManager.createNativeQuery("EXPLAIN EXECUTE coverage_plan_test (" + values + ")")
                    .getResultList()).stream().map(Object::toString).toList();
        } finally {
            entityManager.createNativeQuery("DEALLOCATE coverage_plan_test").executeUpdate();
        }
    }

    private static final Map<String, String> PARAMETER_TYPES = Map.of(
            "userId", "uuid",
            "lowerTs", "timestamptz", "upperTs", "timestamptz",
            "lowerPointId", "bigint", "upperPointId", "bigint",
            "radiusMeters", "integer", "segmentizeMeters", "integer", "maxGapSeconds", "integer",
            "maxSpeedMps", "double precision", "maxAccuracy", "double precision");

    private static <T> T inRolledBackTransaction(Supplier<T> work) {
        QuarkusTransaction.begin();
        try {
            return work.get();
        } finally {
            QuarkusTransaction.rollback();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> planLines(Query explain) {
        return ((List<Object>) explain.getResultList()).stream().map(Object::toString).toList();
    }
}

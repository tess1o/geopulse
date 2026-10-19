package org.github.tess1o.geopulse.coverage.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.coverage.CoverageDefaults;
import org.github.tess1o.geopulse.coverage.model.CoverageSummary;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.TimelineSeeder;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Coverage summaries after processing a seeded 400 m walk. Cells come from buffered track segments, so instead of
 * hand-computed cell counts the test checks what must hold: the summary matches the cells endpoint, area follows
 * from the cell count, coarser grids never have more cells, and points too inaccurate to use add nothing.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class CoverageSummaryIntegrationTest {

    private static final double START_LAT = 50.4501;
    private static final double START_LON = 30.5234;
    /** About 20 m of longitude at this latitude. */
    private static final double LON_STEP = 0.000282;

    @Inject
    CoverageService coverageService;

    @Inject
    TimelineSeeder seed;

    private UUID walkerId;
    private UUID inaccurateId;

    @BeforeEach
    @Transactional
    void setUp() {
        UserEntity walker = seed.user("coverage-walker", "UTC");
        walkerId = walker.getId();
        Instant start = Instant.parse("2026-03-01T10:00:00Z");
        for (int i = 0; i <= 20; i++) {
            seed.gpsPoint(walker, start.plusSeconds(i * 20L), START_LAT, START_LON + i * LON_STEP, 5.0);
        }

        UserEntity inaccurate = seed.user("coverage-inaccurate", "UTC");
        inaccurateId = inaccurate.getId();
        for (int i = 0; i <= 20; i++) {
            seed.gpsPoint(inaccurate, start.plusSeconds(i * 20L), START_LAT, START_LON + i * LON_STEP,
                    CoverageDefaults.MAX_ACCURACY_METERS + 50);
        }
    }

    @Test
    void summaryBeforeProcessingIsEmpty() {
        CoverageSummary summary = coverageService.getCoverageSummary(walkerId, 50);

        assertThat(summary.totalCells()).isZero();
        assertThat(summary.areaSquareKm()).isZero();
    }

    @Test
    void summaryMatchesTheProcessedCells() {
        coverageService.processUserCoverage(walkerId);

        long previous = Long.MAX_VALUE;
        for (int grid : CoverageDefaults.GRID_SIZES_METERS_ORDERED) {
            CoverageSummary summary = coverageService.getCoverageSummary(walkerId, grid);
            int listed = coverageService.getCoverageCells(walkerId, -180, -85, 180, 85, grid,
                    CoverageDefaults.MAX_CELLS_PER_VIEW).size();

            assertThat(summary.gridMeters()).isEqualTo(grid);
            assertThat(summary.totalCells()).as("grid %d summary vs cells", grid).isEqualTo(listed);
            assertThat(summary.totalCells()).as("grid %d has cells", grid).isPositive();
            assertThat(summary.totalCells()).as("grid %d not more than finer grid", grid).isLessThanOrEqualTo(previous);
            assertThat(summary.areaSquareKm())
                    .isCloseTo(summary.totalCells() * (double) grid * grid / 1_000_000.0, offset(1e-9));
            previous = summary.totalCells();
        }
        assertThat(coverageService.getCoverageSummary(walkerId, 40000).totalCells())
                .as("a 400 m walk fits in one 40 km cell").isEqualTo(1);
        assertThat(coverageService.getCoverageSummary(walkerId, 20).totalCells())
                .as("a 400 m walk crosses many 20 m cells").isGreaterThan(10);
    }

    @Test
    void processingAgainAddsNothingNew() {
        coverageService.processUserCoverage(walkerId);
        long first = coverageService.getCoverageSummary(walkerId, 50).totalCells();

        coverageService.processUserCoverage(walkerId);

        assertThat(coverageService.getCoverageSummary(walkerId, 50).totalCells()).isEqualTo(first);
    }

    @Test
    void pointsLessAccurateThanTheLimitAddNoCoverage() {
        coverageService.processUserCoverage(inaccurateId);

        assertThat(coverageService.getCoverageSummary(inaccurateId, 50).totalCells()).isZero();
    }
}

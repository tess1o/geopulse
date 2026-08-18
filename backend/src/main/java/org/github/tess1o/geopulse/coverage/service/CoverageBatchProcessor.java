package org.github.tess1o.geopulse.coverage.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.coverage.CoverageDefaults;
import org.github.tess1o.geopulse.coverage.model.CoverageProcessingCursor;
import org.github.tess1o.geopulse.coverage.repository.CoverageRepository;
import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;

import java.util.UUID;

@ApplicationScoped
public class CoverageBatchProcessor {

    private final CoverageRepository coverageRepository;

    @Inject
    GeoPulseWorkloadMetrics workloadMetrics;

    @Inject
    public CoverageBatchProcessor(CoverageRepository coverageRepository) {
        this.coverageRepository = coverageRepository;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public CoverageProcessingCursor findProcessingCursor(UUID userId) {
        long startedAtNanos = metricsStart();
        try {
            CoverageProcessingCursor cursor = coverageRepository.findProcessingCursor(userId);
            recordStage(startedAtNanos, "find_cursor", "none", "success");
            return cursor;
        } catch (Exception e) {
            recordStage(startedAtNanos, "find_cursor", "none", "error");
            throw e;
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public CoverageProcessingCursor processNextBatch(UUID userId,
                                                     CoverageProcessingCursor lowerBound,
                                                     int batchSize) {
        long startedAtNanos = metricsStart();
        CoverageProcessingCursor batchUpperBound;
        try {
            batchUpperBound = coverageRepository.findBatchUpperBound(
                    userId,
                    lowerBound,
                    CoverageDefaults.MAX_ACCURACY_METERS,
                    batchSize
            );
            recordStage(startedAtNanos, "find_upper_bound", "none", batchUpperBound == null ? "empty" : "success");
        } catch (Exception e) {
            recordStage(startedAtNanos, "find_upper_bound", "none", "error");
            throw e;
        }

        if (batchUpperBound == null) {
            return null;
        }

        for (int gridMeters : CoverageDefaults.GRID_SIZES_METERS_ORDERED) {
            startedAtNanos = metricsStart();
            try {
                int upserted = coverageRepository.upsertCoverageCells(
                        userId,
                        lowerBound,
                        batchUpperBound,
                        gridMeters,
                        CoverageDefaults.RADIUS_METERS,
                        CoverageDefaults.SEGMENTIZE_METERS,
                        CoverageDefaults.MAX_GAP_SECONDS,
                        CoverageDefaults.MAX_SPEED_MPS,
                        CoverageDefaults.MAX_ACCURACY_METERS
                );
                recordStage(startedAtNanos, "upsert_grid", String.valueOf(gridMeters), "success");
                countCells(upserted, gridMeters);
            } catch (Exception e) {
                recordStage(startedAtNanos, "upsert_grid", String.valueOf(gridMeters), "error");
                throw e;
            }
        }

        startedAtNanos = metricsStart();
        coverageRepository.upsertLastProcessed(userId, batchUpperBound);
        recordStage(startedAtNanos, "update_cursor", "none", "success");
        return batchUpperBound;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void resetForRebuild(UUID userId) {
        long startedAtNanos = metricsStart();
        coverageRepository.deleteCoverageCells(userId);
        recordStage(startedAtNanos, "delete_cells", "none", "success");
        startedAtNanos = metricsStart();
        coverageRepository.resetProcessingCursor(userId);
        recordStage(startedAtNanos, "reset_cursor", "none", "success");
    }

    private long metricsStart() {
        return workloadMetrics == null ? System.nanoTime() : workloadMetrics.start();
    }

    private void recordStage(long startedAtNanos, String stage, String gridMeters, String result) {
        if (workloadMetrics == null) {
            return;
        }
        workloadMetrics.recordTimer("geopulse.coverage.batch.stage.duration", startedAtNanos,
                "component", "coverage",
                "stage", stage,
                "mode", "batch",
                "grid_m", gridMeters,
                "result", result);
    }

    private void countCells(int count, int gridMeters) {
        if (workloadMetrics == null || count <= 0) {
            return;
        }
        workloadMetrics.increment("geopulse.coverage.cells_upserted", count,
                "component", "coverage",
                "grid_m", String.valueOf(gridMeters),
                "result", "success");
    }
}

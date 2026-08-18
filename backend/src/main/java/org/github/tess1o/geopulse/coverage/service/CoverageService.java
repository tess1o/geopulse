package org.github.tess1o.geopulse.coverage.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.coverage.CoverageDefaults;
import org.github.tess1o.geopulse.coverage.model.CoverageCell;
import org.github.tess1o.geopulse.coverage.model.CoverageProcessingCursor;
import org.github.tess1o.geopulse.coverage.model.CoverageSummary;
import org.github.tess1o.geopulse.coverage.model.CoverageStatus;
import org.github.tess1o.geopulse.coverage.model.CoverageStatusSnapshot;
import org.github.tess1o.geopulse.coverage.repository.CoverageRepository;
import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class CoverageService {

    private final CoverageRepository coverageRepository;
    private final UserRepository userRepository;
    private final CoverageBatchProcessor coverageBatchProcessor;

    @Inject
    GeoPulseWorkloadMetrics workloadMetrics;

    @ConfigProperty(name = "geopulse.coverage.processing.batch-size",
            defaultValue = "" + CoverageDefaults.DEFAULT_BATCH_SIZE)
    int batchSize;

    @Inject
    public CoverageService(CoverageRepository coverageRepository,
                           UserRepository userRepository,
                           CoverageBatchProcessor coverageBatchProcessor) {
        this.coverageRepository = coverageRepository;
        this.userRepository = userRepository;
        this.coverageBatchProcessor = coverageBatchProcessor;
    }

    public void processUserCoverage(UUID userId) {
        processUserCoverage(userId, "incremental");
    }

    private void processUserCoverage(UUID userId, String mode) {
        long jobStart = metricsStart();
        String result = "success";
        int validatedBatchSize = validateBatchSize();
        CoverageProcessingCursor lowerBound = coverageBatchProcessor.findProcessingCursor(userId);
        int batchNum = 0;

        try {
            while (true) {
                long batchStart = metricsStart();
                CoverageProcessingCursor batchUpperBound = coverageBatchProcessor.processNextBatch(
                        userId,
                        lowerBound,
                        validatedBatchSize
                );

                if (batchUpperBound == null) {
                    recordCoverageBatch(batchStart, mode, "empty");
                    log.debug("Coverage processing complete for user {} after {} batches.", userId, batchNum);
                    break;
                }

                batchNum++;
                recordCoverageBatch(batchStart, mode, "success");
                countCoverage("geopulse.coverage.batches", 1, mode, "success");
                log.debug("Processing coverage batch {} for user {} (cursor: {})", batchNum, userId, batchUpperBound);
                lowerBound = batchUpperBound;
            }
        } catch (Exception e) {
            result = "error";
            throw e;
        } finally {
            recordCoverageJob(jobStart, "service", mode, result);
        }
    }

    public void rebuildUserCoverage(UUID userId) {
        long stageStart = metricsStart();
        coverageBatchProcessor.resetForRebuild(userId);
        recordCoverageStage(stageStart, "reset_rebuild", "full", "success");
        processUserCoverage(userId, "full");
    }

    public List<CoverageCell> getCoverageCells(UUID userId,
                                               double minLon,
                                               double minLat,
                                               double maxLon,
                                               double maxLat,
                                               int gridMeters,
                                               int limit) {
        ensureGridSupported(gridMeters);
        int boundedLimit = Math.max(1, Math.min(limit, CoverageDefaults.MAX_CELLS_PER_VIEW));
        return coverageRepository.findCoverageCells(userId, minLon, minLat, maxLon, maxLat, gridMeters, boundedLimit);
    }

    public CoverageSummary getCoverageSummary(UUID userId, int gridMeters) {
        ensureGridSupported(gridMeters);
        long totalCells = coverageRepository.countCoverageCells(userId, gridMeters);
        double areaSquareKm = (totalCells * (double) gridMeters * (double) gridMeters) / 1_000_000.0;

        return new CoverageSummary(gridMeters, totalCells, areaSquareKm);
    }

    public boolean isGridSupported(int gridMeters) {
        return CoverageDefaults.GRID_SIZES_METERS.contains(gridMeters);
    }

    @Transactional
    public void setUserCoverageEnabled(UUID userId, boolean enabled) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        user.setCoverageEnabled(enabled);
    }

    public CoverageStatus getCoverageStatus(UUID userId) {
        CoverageStatusSnapshot snapshot = coverageRepository.findCoverageStatusSnapshot(userId);
        return new CoverageStatus(
                snapshot.userEnabled(),
                snapshot.processing(),
                snapshot.hasCells(),
                snapshot.lastProcessed(),
                snapshot.processingStartedAt()
        );
    }

    private void ensureGridSupported(int gridMeters) {
        if (!isGridSupported(gridMeters)) {
            throw new IllegalArgumentException("Unsupported grid size: " + gridMeters);
        }
    }

    private int validateBatchSize() {
        if (batchSize < 1) {
            throw new IllegalArgumentException("geopulse.coverage.processing.batch-size must be at least 1");
        }
        return batchSize;
    }

    private long metricsStart() {
        return workloadMetrics == null ? System.nanoTime() : workloadMetrics.start();
    }

    private void recordCoverageJob(long startedAtNanos, String trigger, String mode, String result) {
        if (workloadMetrics == null) {
            return;
        }
        workloadMetrics.recordTimer("geopulse.coverage.job.duration", startedAtNanos,
                "component", "coverage",
                "trigger", trigger,
                "mode", mode,
                "result", result);
    }

    private void recordCoverageBatch(long startedAtNanos, String mode, String result) {
        if (workloadMetrics == null) {
            return;
        }
        workloadMetrics.recordTimer("geopulse.coverage.batch.duration", startedAtNanos,
                "component", "coverage",
                "mode", mode,
                "result", result);
    }

    private void recordCoverageStage(long startedAtNanos, String stage, String mode, String result) {
        if (workloadMetrics == null) {
            return;
        }
        workloadMetrics.recordTimer("geopulse.coverage.batch.stage.duration", startedAtNanos,
                "component", "coverage",
                "stage", stage,
                "mode", mode,
                "grid_m", "none",
                "result", result);
    }

    private void countCoverage(String name, long count, String mode, String result) {
        if (workloadMetrics == null || count <= 0) {
            return;
        }
        workloadMetrics.increment(name, count,
                "component", "coverage",
                "mode", mode,
                "result", result);
    }
}

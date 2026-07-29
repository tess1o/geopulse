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
        int validatedBatchSize = validateBatchSize();
        CoverageProcessingCursor lowerBound = coverageBatchProcessor.findProcessingCursor(userId);
        int batchNum = 0;

        while (true) {
            CoverageProcessingCursor batchUpperBound = coverageBatchProcessor.processNextBatch(
                    userId,
                    lowerBound,
                    validatedBatchSize
            );

            if (batchUpperBound == null) {
                log.debug("Coverage processing complete for user {} after {} batches.", userId, batchNum);
                break;
            }

            batchNum++;
            log.debug("Processing coverage batch {} for user {} (cursor: {})", batchNum, userId, batchUpperBound);
            lowerBound = batchUpperBound;
        }
    }

    public void rebuildUserCoverage(UUID userId) {
        coverageBatchProcessor.resetForRebuild(userId);
        processUserCoverage(userId);
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
}

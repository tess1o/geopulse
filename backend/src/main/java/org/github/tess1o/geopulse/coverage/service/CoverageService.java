package org.github.tess1o.geopulse.coverage.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.coverage.CoverageDefaults;
import org.github.tess1o.geopulse.coverage.model.CoverageCell;
import org.github.tess1o.geopulse.coverage.model.CoverageProcessingState;
import org.github.tess1o.geopulse.coverage.model.CoverageSummary;
import org.github.tess1o.geopulse.coverage.model.CoverageStatus;
import org.github.tess1o.geopulse.coverage.repository.CoverageRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CoverageService {

    private final CoverageRepository coverageRepository;
    private final UserRepository userRepository;

    @Inject
    public CoverageService(CoverageRepository coverageRepository,
                           UserRepository userRepository) {
        this.coverageRepository = coverageRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void processUserCoverage(UUID userId) {
        Instant lastProcessed = coverageRepository.findLastProcessed(userId);
        Instant newLastProcessed = coverageRepository.findMaxGpsTimestamp(
                userId,
                lastProcessed,
                CoverageDefaults.MAX_ACCURACY_METERS
        );

        if (newLastProcessed == null) {
            return;
        }

        for (int gridMeters : CoverageDefaults.GRID_SIZES_METERS) {
            coverageRepository.upsertCoverageCells(
                    userId,
                    lastProcessed,
                    gridMeters,
                    CoverageDefaults.RADIUS_METERS,
                    CoverageDefaults.SEGMENTIZE_METERS,
                    CoverageDefaults.MAX_GAP_SECONDS,
                    CoverageDefaults.MAX_SPEED_MPS,
                    CoverageDefaults.MAX_ACCURACY_METERS
            );
        }

        coverageRepository.upsertLastProcessed(userId, newLastProcessed);
    }

    public List<CoverageCell> getCoverageCells(UUID userId,
                                               double minLon,
                                               double minLat,
                                               double maxLon,
                                               double maxLat,
                                               int gridMeters) {
        ensureGridSupported(gridMeters);
        return coverageRepository.findCoverageCells(userId, minLon, minLat, maxLon, maxLat, gridMeters);
    }

    public CoverageSummary getCoverageSummary(UUID userId, int gridMeters) {
        ensureGridSupported(gridMeters);
        long totalCells = coverageRepository.countCoverageCells(userId, gridMeters);
        double areaSquareKm = (totalCells * (double) gridMeters * (double) gridMeters) / 1_000_000.0;

        return CoverageSummary.builder()
                .gridMeters(gridMeters)
                .totalCells(totalCells)
                .areaSquareKm(areaSquareKm)
                .build();
    }

    public boolean isGridSupported(int gridMeters) {
        return Arrays.stream(CoverageDefaults.GRID_SIZES_METERS).anyMatch(value -> value == gridMeters);
    }

    public boolean isUserCoverageEnabled(UUID userId) {
        UserEntity user = userRepository.findById(userId);
        return user != null && user.isCoverageEnabled();
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
        boolean userEnabled = isUserCoverageEnabled(userId);
        CoverageProcessingState processingState = coverageRepository.findProcessingState(userId);
        boolean hasCells = coverageRepository.hasCoverageCells(userId);

        return CoverageStatus.builder()
                .userEnabled(userEnabled)
                .processing(processingState.isProcessing())
                .hasCells(hasCells)
                .lastProcessed(processingState.getLastProcessed())
                .processingStartedAt(processingState.getProcessingStartedAt())
                .build();
    }

    private void ensureGridSupported(int gridMeters) {
        if (!isGridSupported(gridMeters)) {
            throw new IllegalArgumentException("Unsupported grid size: " + gridMeters);
        }
    }
}

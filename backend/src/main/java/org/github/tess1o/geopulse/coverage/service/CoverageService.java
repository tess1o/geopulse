package org.github.tess1o.geopulse.coverage.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
        CoverageProcessingCursor lowerBound = coverageRepository.findProcessingCursor(userId);
        CoverageProcessingCursor upperBound = coverageRepository.findProcessingUpperBound(
                userId,
                lowerBound,
                CoverageDefaults.MAX_ACCURACY_METERS
        );

        if (upperBound == null) {
            return;
        }

        for (int gridMeters : CoverageDefaults.GRID_SIZES_METERS_ORDERED) {
            coverageRepository.upsertCoverageCells(
                    userId,
                    lowerBound,
                    upperBound,
                    gridMeters,
                    CoverageDefaults.RADIUS_METERS,
                    CoverageDefaults.SEGMENTIZE_METERS,
                    CoverageDefaults.MAX_GAP_SECONDS,
                    CoverageDefaults.MAX_SPEED_MPS,
                    CoverageDefaults.MAX_ACCURACY_METERS
            );
        }

        coverageRepository.upsertLastProcessed(userId, upperBound);
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
}

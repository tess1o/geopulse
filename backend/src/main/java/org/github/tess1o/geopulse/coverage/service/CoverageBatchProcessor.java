package org.github.tess1o.geopulse.coverage.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.coverage.CoverageDefaults;
import org.github.tess1o.geopulse.coverage.model.CoverageProcessingCursor;
import org.github.tess1o.geopulse.coverage.repository.CoverageRepository;

import java.util.UUID;

@ApplicationScoped
public class CoverageBatchProcessor {

    private final CoverageRepository coverageRepository;

    @Inject
    public CoverageBatchProcessor(CoverageRepository coverageRepository) {
        this.coverageRepository = coverageRepository;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public CoverageProcessingCursor findProcessingCursor(UUID userId) {
        return coverageRepository.findProcessingCursor(userId);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public CoverageProcessingCursor processNextBatch(UUID userId,
                                                     CoverageProcessingCursor lowerBound,
                                                     int batchSize) {
        CoverageProcessingCursor batchUpperBound = coverageRepository.findBatchUpperBound(
                userId,
                lowerBound,
                CoverageDefaults.MAX_ACCURACY_METERS,
                batchSize
        );

        if (batchUpperBound == null) {
            return null;
        }

        for (int gridMeters : CoverageDefaults.GRID_SIZES_METERS_ORDERED) {
            coverageRepository.upsertCoverageCells(
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
        }

        coverageRepository.upsertLastProcessed(userId, batchUpperBound);
        return batchUpperBound;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void resetForRebuild(UUID userId) {
        coverageRepository.deleteCoverageCells(userId);
        coverageRepository.resetProcessingCursor(userId);
    }
}

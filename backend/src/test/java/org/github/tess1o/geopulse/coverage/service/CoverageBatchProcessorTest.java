package org.github.tess1o.geopulse.coverage.service;

import org.github.tess1o.geopulse.coverage.CoverageDefaults;
import org.github.tess1o.geopulse.coverage.model.CoverageProcessingCursor;
import org.github.tess1o.geopulse.coverage.repository.CoverageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class CoverageBatchProcessorTest {

    @Mock
    CoverageRepository coverageRepository;

    @InjectMocks
    CoverageBatchProcessor coverageBatchProcessor;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void processNextBatch_noPendingPoints_returnsNullWithoutWritingCells() {
        when(coverageRepository.findBatchUpperBound(userId, null, CoverageDefaults.MAX_ACCURACY_METERS, 5_000))
                .thenReturn(null);

        CoverageProcessingCursor result = coverageBatchProcessor.processNextBatch(userId, null, 5_000);

        assertThat(result).isNull();
        verify(coverageRepository).findBatchUpperBound(userId, null, CoverageDefaults.MAX_ACCURACY_METERS, 5_000);
        verifyNoMoreInteractions(coverageRepository);
    }

    @Test
    void processNextBatch_processesAllGridsAndCommitsCursor() {
        CoverageProcessingCursor lowerBound = new CoverageProcessingCursor(
                Instant.parse("2026-01-01T00:00:00Z"),
                100L
        );
        CoverageProcessingCursor upperBound = new CoverageProcessingCursor(
                Instant.parse("2026-01-01T01:00:00Z"),
                5_100L
        );

        when(coverageRepository.findBatchUpperBound(userId, lowerBound, CoverageDefaults.MAX_ACCURACY_METERS, 5_000))
                .thenReturn(upperBound);

        CoverageProcessingCursor result = coverageBatchProcessor.processNextBatch(userId, lowerBound, 5_000);

        assertThat(result).isEqualTo(upperBound);
        verify(coverageRepository).findBatchUpperBound(userId, lowerBound, CoverageDefaults.MAX_ACCURACY_METERS, 5_000);
        for (int gridMeters : CoverageDefaults.GRID_SIZES_METERS_ORDERED) {
            verify(coverageRepository).upsertCoverageCells(
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
        verify(coverageRepository).upsertLastProcessed(userId, upperBound);
        verifyNoMoreInteractions(coverageRepository);
    }

    @Test
    void resetForRebuild_deletesCoverageAndResetsCursor() {
        coverageBatchProcessor.resetForRebuild(userId);

        verify(coverageRepository).deleteCoverageCells(userId);
        verify(coverageRepository).resetProcessingCursor(userId);
        verifyNoMoreInteractions(coverageRepository);
    }
}

package org.github.tess1o.geopulse.coverage.service;

import org.github.tess1o.geopulse.coverage.CoverageDefaults;
import org.github.tess1o.geopulse.coverage.model.CoverageProcessingCursor;
import org.github.tess1o.geopulse.coverage.repository.CoverageRepository;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CoverageService batch processing refactor.
 * Verifies that processUserCoverage runs in chunks to avoid
 * Postgres pgsql_tmp disk exhaustion (issue #553).
 */
@ExtendWith(MockitoExtension.class)
class CoverageServiceTest {

    @Mock
    CoverageRepository coverageRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    CoverageService coverageService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        coverageService.batchSize = 5_000;
    }

    @Test
    void processUserCoverage_noPendingPoints_doesNothing() {
        when(coverageRepository.findProcessingCursor(userId)).thenReturn(null);
        when(coverageRepository.findBatchUpperBound(eq(userId), isNull(), anyDouble(), anyInt()))
                .thenReturn(null);

        coverageService.processUserCoverage(userId);

        verify(coverageRepository, never()).upsertCoverageCells(any(), any(), any(), anyInt(), anyInt(), anyInt(), anyInt(), anyDouble(), anyDouble());
        verify(coverageRepository, never()).upsertLastProcessed(any(), any());
    }

    @Test
    void processUserCoverage_singleBatch_processesAndSavesCursor() {
        CoverageProcessingCursor batchCursor = new CoverageProcessingCursor(Instant.parse("2026-01-01T00:00:00Z"), 100L);

        when(coverageRepository.findProcessingCursor(userId)).thenReturn(null);
        when(coverageRepository.findBatchUpperBound(eq(userId), isNull(), anyDouble(), anyInt()))
                .thenReturn(batchCursor)
                .thenReturn(null);

        coverageService.processUserCoverage(userId);

        // Should upsert cells for all 7 grid sizes exactly once
        verify(coverageRepository, times(CoverageDefaults.GRID_SIZES_METERS_ORDERED.size()))
                .upsertCoverageCells(eq(userId), isNull(), eq(batchCursor), anyInt(), anyInt(), anyInt(), anyInt(), anyDouble(), anyDouble());
        verify(coverageRepository, times(1)).upsertLastProcessed(userId, batchCursor);
    }

    @Test
    void processUserCoverage_multipleBatches_processesAllChunks() {
        CoverageProcessingCursor batch1 = new CoverageProcessingCursor(Instant.parse("2026-01-01T00:00:00Z"), 5000L);
        CoverageProcessingCursor batch2 = new CoverageProcessingCursor(Instant.parse("2026-01-02T00:00:00Z"), 10000L);

        when(coverageRepository.findProcessingCursor(userId)).thenReturn(null);
        when(coverageRepository.findBatchUpperBound(eq(userId), isNull(), anyDouble(), anyInt()))
                .thenReturn(batch1);
        when(coverageRepository.findBatchUpperBound(eq(userId), eq(batch1), anyDouble(), anyInt()))
                .thenReturn(batch2);
        when(coverageRepository.findBatchUpperBound(eq(userId), eq(batch2), anyDouble(), anyInt()))
                .thenReturn(null);

        coverageService.processUserCoverage(userId);

        int gridCount = CoverageDefaults.GRID_SIZES_METERS_ORDERED.size();
        // Each batch processes all grid sizes once = 2 batches * 7 grids = 14 calls
        verify(coverageRepository, times(gridCount * 2))
                .upsertCoverageCells(eq(userId), any(), any(), anyInt(), anyInt(), anyInt(), anyInt(), anyDouble(), anyDouble());
        verify(coverageRepository, times(1)).upsertLastProcessed(userId, batch1);
        verify(coverageRepository, times(1)).upsertLastProcessed(userId, batch2);
    }

    @Test
    void processUserCoverage_cursorAdvancesCorrectly_betweenBatches() {
        CoverageProcessingCursor existing = new CoverageProcessingCursor(Instant.parse("2025-12-01T00:00:00Z"), 1L);
        CoverageProcessingCursor batch1 = new CoverageProcessingCursor(Instant.parse("2026-01-01T00:00:00Z"), 5000L);

        when(coverageRepository.findProcessingCursor(userId)).thenReturn(existing);
        when(coverageRepository.findBatchUpperBound(eq(userId), eq(existing), anyDouble(), anyInt()))
                .thenReturn(batch1);
        when(coverageRepository.findBatchUpperBound(eq(userId), eq(batch1), anyDouble(), anyInt()))
                .thenReturn(null);

        coverageService.processUserCoverage(userId);

        // First batch: lowerBound = existing
        verify(coverageRepository, atLeastOnce())
                .upsertCoverageCells(eq(userId), eq(existing), eq(batch1), anyInt(), anyInt(), anyInt(), anyInt(), anyDouble(), anyDouble());
        verify(coverageRepository).upsertLastProcessed(userId, batch1);
    }
}

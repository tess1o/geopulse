package org.github.tess1o.geopulse.coverage.service;

import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.coverage.model.CoverageProcessingCursor;
import org.github.tess1o.geopulse.coverage.repository.CoverageRepository;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class CoverageServiceTest {

    @Mock
    CoverageRepository coverageRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    CoverageBatchProcessor coverageBatchProcessor;

    @InjectMocks
    CoverageService coverageService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        coverageService.batchSize = 5_000;
    }

    @Test
    void processUserCoverage_noPendingPoints_delegatesOnceAndStops() {
        when(coverageBatchProcessor.findProcessingCursor(userId)).thenReturn(null);
        when(coverageBatchProcessor.processNextBatch(userId, null, 5_000)).thenReturn(null);

        coverageService.processUserCoverage(userId);

        InOrder inOrder = inOrder(coverageBatchProcessor);
        inOrder.verify(coverageBatchProcessor).findProcessingCursor(userId);
        inOrder.verify(coverageBatchProcessor).processNextBatch(userId, null, 5_000);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void processUserCoverage_singleBatch_advancesUntilNoMoreBatches() {
        CoverageProcessingCursor batchCursor = new CoverageProcessingCursor(
                Instant.parse("2026-01-01T00:00:00Z"),
                100L
        );

        when(coverageBatchProcessor.findProcessingCursor(userId)).thenReturn(null);
        when(coverageBatchProcessor.processNextBatch(userId, null, 5_000)).thenReturn(batchCursor);
        when(coverageBatchProcessor.processNextBatch(userId, batchCursor, 5_000)).thenReturn(null);

        coverageService.processUserCoverage(userId);

        InOrder inOrder = inOrder(coverageBatchProcessor);
        inOrder.verify(coverageBatchProcessor).findProcessingCursor(userId);
        inOrder.verify(coverageBatchProcessor).processNextBatch(userId, null, 5_000);
        inOrder.verify(coverageBatchProcessor).processNextBatch(userId, batchCursor, 5_000);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void processUserCoverage_multipleBatches_passesCommittedCursorToNextBatch() {
        CoverageProcessingCursor batch1 = new CoverageProcessingCursor(
                Instant.parse("2026-01-01T00:00:00Z"),
                5_000L
        );
        CoverageProcessingCursor batch2 = new CoverageProcessingCursor(
                Instant.parse("2026-01-02T00:00:00Z"),
                10_000L
        );

        when(coverageBatchProcessor.findProcessingCursor(userId)).thenReturn(null);
        when(coverageBatchProcessor.processNextBatch(userId, null, 5_000)).thenReturn(batch1);
        when(coverageBatchProcessor.processNextBatch(userId, batch1, 5_000)).thenReturn(batch2);
        when(coverageBatchProcessor.processNextBatch(userId, batch2, 5_000)).thenReturn(null);

        coverageService.processUserCoverage(userId);

        InOrder inOrder = inOrder(coverageBatchProcessor);
        inOrder.verify(coverageBatchProcessor).findProcessingCursor(userId);
        inOrder.verify(coverageBatchProcessor).processNextBatch(userId, null, 5_000);
        inOrder.verify(coverageBatchProcessor).processNextBatch(userId, batch1, 5_000);
        inOrder.verify(coverageBatchProcessor).processNextBatch(userId, batch2, 5_000);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void processUserCoverage_existingCursor_startsFromPersistedCursor() {
        CoverageProcessingCursor existing = new CoverageProcessingCursor(
                Instant.parse("2025-12-01T00:00:00Z"),
                1L
        );
        CoverageProcessingCursor batch1 = new CoverageProcessingCursor(
                Instant.parse("2026-01-01T00:00:00Z"),
                5_000L
        );

        when(coverageBatchProcessor.findProcessingCursor(userId)).thenReturn(existing);
        when(coverageBatchProcessor.processNextBatch(userId, existing, 5_000)).thenReturn(batch1);
        when(coverageBatchProcessor.processNextBatch(userId, batch1, 5_000)).thenReturn(null);

        coverageService.processUserCoverage(userId);

        InOrder inOrder = inOrder(coverageBatchProcessor);
        inOrder.verify(coverageBatchProcessor).findProcessingCursor(userId);
        inOrder.verify(coverageBatchProcessor).processNextBatch(userId, existing, 5_000);
        inOrder.verify(coverageBatchProcessor).processNextBatch(userId, batch1, 5_000);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void rebuildUserCoverage_resetsInDedicatedTransactionThenProcessesBatches() {
        when(coverageBatchProcessor.findProcessingCursor(userId)).thenReturn(null);
        when(coverageBatchProcessor.processNextBatch(userId, null, 5_000)).thenReturn(null);

        coverageService.rebuildUserCoverage(userId);

        InOrder inOrder = inOrder(coverageBatchProcessor);
        inOrder.verify(coverageBatchProcessor).resetForRebuild(userId);
        inOrder.verify(coverageBatchProcessor).findProcessingCursor(userId);
        inOrder.verify(coverageBatchProcessor).processNextBatch(userId, null, 5_000);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void processUserCoverage_invalidBatchSize_failsBeforeTouchingDatabase() {
        coverageService.batchSize = 0;

        assertThatThrownBy(() -> coverageService.processUserCoverage(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("geopulse.coverage.processing.batch-size must be at least 1");

        verifyNoInteractions(coverageBatchProcessor);
    }

    @Test
    void transactionBoundaries_areOnBatchProcessorNotCoverageLoop() throws NoSuchMethodException {
        Method processUserCoverage = CoverageService.class.getMethod("processUserCoverage", UUID.class);
        Method rebuildUserCoverage = CoverageService.class.getMethod("rebuildUserCoverage", UUID.class);
        Method findProcessingCursor = CoverageBatchProcessor.class.getMethod("findProcessingCursor", UUID.class);
        Method processNextBatch = CoverageBatchProcessor.class.getMethod(
                "processNextBatch",
                UUID.class,
                CoverageProcessingCursor.class,
                int.class
        );
        Method resetForRebuild = CoverageBatchProcessor.class.getMethod("resetForRebuild", UUID.class);

        assertThat(processUserCoverage.getAnnotation(Transactional.class)).isNull();
        assertThat(rebuildUserCoverage.getAnnotation(Transactional.class)).isNull();
        assertRequiresNew(findProcessingCursor);
        assertRequiresNew(processNextBatch);
        assertRequiresNew(resetForRebuild);
    }

    private static void assertRequiresNew(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.value()).isEqualTo(Transactional.TxType.REQUIRES_NEW);
    }
}

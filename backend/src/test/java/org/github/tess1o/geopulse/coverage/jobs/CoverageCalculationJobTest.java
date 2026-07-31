package org.github.tess1o.geopulse.coverage.jobs;

import org.github.tess1o.geopulse.coverage.CoverageDefaults;
import org.github.tess1o.geopulse.coverage.repository.CoverageRepository;
import org.github.tess1o.geopulse.coverage.service.CoverageProcessingService;
import org.github.tess1o.geopulse.importdata.service.ImportJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class CoverageCalculationJobTest {

    @Mock
    CoverageRepository coverageRepository;

    @Mock
    CoverageProcessingService processingService;

    @Mock
    ImportJobService importJobService;

    CoverageCalculationJob coverageCalculationJob;

    @BeforeEach
    void setUp() {
        coverageCalculationJob = new CoverageCalculationJob(
                coverageRepository,
                processingService,
                importJobService
        );
        coverageCalculationJob.maxConcurrentTasks = 2;
        coverageCalculationJob.processingStaleTimeoutSeconds = CoverageDefaults.PROCESSING_STALE_TIMEOUT_SECONDS;
        coverageCalculationJob.executorService = new DirectExecutorService();
        coverageCalculationJob.init();
    }

    @Test
    void processCoverage_skipsUsersWithActiveImportJobs() {
        UUID activeImportUserId = UUID.randomUUID();
        UUID idleUserId = UUID.randomUUID();
        when(coverageRepository.findUsersWithNewCoverage(
                CoverageDefaults.MAX_ACCURACY_METERS,
                CoverageDefaults.PROCESSING_STALE_TIMEOUT_SECONDS
        )).thenReturn(List.of(activeImportUserId, idleUserId));
        when(importJobService.hasActiveImportJob(activeImportUserId)).thenReturn(true);
        when(importJobService.hasActiveImportJob(idleUserId)).thenReturn(false);

        coverageCalculationJob.processCoverage();

        verify(processingService, never()).processUserCoverage(activeImportUserId);
        verify(processingService).processUserCoverage(idleUserId);
    }

    @Test
    void processCoverage_doesNothingWhenAllUsersHaveActiveImports() {
        UUID activeImportUserId = UUID.randomUUID();
        when(coverageRepository.findUsersWithNewCoverage(
                CoverageDefaults.MAX_ACCURACY_METERS,
                CoverageDefaults.PROCESSING_STALE_TIMEOUT_SECONDS
        )).thenReturn(List.of(activeImportUserId));
        when(importJobService.hasActiveImportJob(activeImportUserId)).thenReturn(true);

        coverageCalculationJob.processCoverage();

        verifyNoInteractions(processingService);
    }

    private static class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}

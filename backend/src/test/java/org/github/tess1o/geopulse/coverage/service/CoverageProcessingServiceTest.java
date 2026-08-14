package org.github.tess1o.geopulse.coverage.service;

import org.github.tess1o.geopulse.coverage.repository.CoverageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class CoverageProcessingServiceTest {

    @Mock
    CoverageRepository coverageRepository;

    @Mock
    CoverageService coverageService;

    private ExecutorService executorService;

    @AfterEach
    void tearDown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test
    void requestFullRecalculationAsync_queuesRebuildWhenCoverageIsAlreadyProcessing() {
        UUID userId = UUID.randomUUID();
        executorService = Executors.newSingleThreadExecutor();
        CoverageProcessingService service = new CoverageProcessingService(
                coverageRepository,
                coverageService,
                executorService
        );

        when(coverageRepository.tryStartProcessing(userId, 0)).thenReturn(false, true);

        CoverageProcessingService.CoverageSchedulingResult result =
                service.requestFullRecalculationAsync(userId);

        assertTrue(result.scheduled());
        assertTrue(result.queued());

        service.drainPendingFullRecalculation(userId);

        verify(coverageService, timeout(1000)).rebuildUserCoverage(userId);
    }
}

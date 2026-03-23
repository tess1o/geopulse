package org.github.tess1o.geopulse.geocoding.service;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfigurationService;
import org.github.tess1o.geopulse.geocoding.dto.ReverseGeocodingDTO;
import org.github.tess1o.geopulse.geocoding.mapper.ReverseGeocodingDTOMapper;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class ReverseGeocodingManagementServiceRetryTest {

    @Mock
    ReverseGeocodingLocationRepository geocodingRepository;
    @Mock
    GeocodingProviderFactory providerFactory;
    @Mock
    GeocodingConfigurationService configService;
    @Mock
    ReverseGeocodingDTOMapper dtoMapper;
    @Mock
    GeocodingCopyOnWriteHandler copyOnWriteHandler;
    @Mock
    ReconciliationJobProgressService reconciliationProgressService;
    @Mock
    ManagedExecutor managedExecutor;

    ReverseGeocodingManagementService service;

    @BeforeEach
    void setUp() {
        service = spy(new ReverseGeocodingManagementService(
                geocodingRepository,
                providerFactory,
                configService,
                dtoMapper,
                copyOnWriteHandler,
                reconciliationProgressService,
                managedExecutor
        ));
        service.reconcileItemMaxAttempts = 3;
        service.reconcileCircuitOpenWaitMs = 0;
        service.reconcileInterItemDelayMs = 0;
    }

    @Test
    void processReconciliationJob_retriesCircuitBreakerOpenAndSucceeds() {
        UUID jobId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ReverseGeocodingDTO successResponse = ReverseGeocodingDTO.builder().id(1L).build();

        doThrow(new RuntimeException("circuit open", new CircuitBreakerOpenException("open")))
                .doReturn(successResponse)
                .when(service).reconcileWithProvider(userId, 1L, "Photon");

        service.processReconciliationJob(jobId, userId, List.of(1L), "Photon");

        verify(service, times(2)).reconcileWithProvider(userId, 1L, "Photon");
        verify(reconciliationProgressService).updateProgress(jobId, 1, 1, 0);
        verify(reconciliationProgressService).completeJob(jobId);
    }

    @Test
    void processReconciliationJob_marksFailureAfterMaxAttemptsAndContinues() {
        UUID jobId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ReverseGeocodingDTO successResponse = ReverseGeocodingDTO.builder().id(1L).build();

        doReturn(successResponse).when(service).reconcileWithProvider(userId, 1L, "Photon");
        doThrow(new RuntimeException("provider unavailable"))
                .when(service).reconcileWithProvider(userId, 2L, "Photon");

        service.processReconciliationJob(jobId, userId, List.of(1L, 2L), "Photon");

        verify(service, times(1)).reconcileWithProvider(userId, 1L, "Photon");
        verify(service, times(3)).reconcileWithProvider(userId, 2L, "Photon");

        InOrder inOrder = inOrder(reconciliationProgressService);
        inOrder.verify(reconciliationProgressService).updateProgress(jobId, 1, 1, 0);
        inOrder.verify(reconciliationProgressService).updateProgress(jobId, 2, 1, 1);
        inOrder.verify(reconciliationProgressService).completeJob(jobId);
    }

    @Test
    void processReconciliationJob_waitsForCircuitOpenDelayBeforeRetry() {
        UUID jobId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ReverseGeocodingDTO successResponse = ReverseGeocodingDTO.builder().id(1L).build();
        service.reconcileItemMaxAttempts = 2;
        service.reconcileCircuitOpenWaitMs = 120;
        service.reconcileInterItemDelayMs = 0;

        doThrow(new RuntimeException("circuit open", new CircuitBreakerOpenException("open")))
                .doReturn(successResponse)
                .when(service).reconcileWithProvider(userId, 1L, "Photon");

        long startedAt = System.nanoTime();
        service.processReconciliationJob(jobId, userId, List.of(1L), "Photon");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertThat(elapsedMs).isGreaterThanOrEqualTo(90);
        verify(service, times(2)).reconcileWithProvider(userId, 1L, "Photon");
        verify(reconciliationProgressService).completeJob(jobId);
    }

    @Test
    void processReconciliationJob_waitsBetweenItemsUsingInterItemDelay() {
        UUID jobId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ReverseGeocodingDTO firstResponse = ReverseGeocodingDTO.builder().id(1L).build();
        ReverseGeocodingDTO secondResponse = ReverseGeocodingDTO.builder().id(2L).build();
        service.reconcileItemMaxAttempts = 1;
        service.reconcileCircuitOpenWaitMs = 0;
        service.reconcileInterItemDelayMs = 120;

        doReturn(firstResponse).when(service).reconcileWithProvider(userId, 1L, "Photon");
        doReturn(secondResponse).when(service).reconcileWithProvider(userId, 2L, "Photon");

        long startedAt = System.nanoTime();
        service.processReconciliationJob(jobId, userId, List.of(1L, 2L), "Photon");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertThat(elapsedMs).isGreaterThanOrEqualTo(90);
        verify(service, times(1)).reconcileWithProvider(userId, 1L, "Photon");
        verify(service, times(1)).reconcileWithProvider(userId, 2L, "Photon");
        verify(reconciliationProgressService).completeJob(jobId);
    }
}

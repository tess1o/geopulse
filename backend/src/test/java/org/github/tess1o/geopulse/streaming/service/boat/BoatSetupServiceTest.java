package org.github.tess1o.geopulse.streaming.service.boat;

import org.github.tess1o.geopulse.streaming.service.trips.TripReclassificationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class BoatSetupServiceTest {

    @Mock
    TripReclassificationService tripReclassificationService;

    @Test
    void reclassifyExistingTripsUpdatesPhaseAndReturnsSuccess() {
        TestableBoatSetupService service = new TestableBoatSetupService();
        service.tripReclassificationService = tripReclassificationService;
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        boolean result = service.reclassifyExistingTrips(userId, jobId);

        assertTrue(result);
        assertEquals("RUNNING", service.updatedStatus);
        assertEquals("READY", service.updatedEnvironmentStatus);
        assertEquals("Reclassifying existing trips", service.updatedPhase);
        assertEquals(99, service.updatedProgress);
        assertNull(service.failedErrorCode);
        verify(tripReclassificationService).reclassifyUserTrips(userId);
    }

    @Test
    void reclassifyExistingTripsMarksJobFailedWhenReclassificationFails() {
        TestableBoatSetupService service = new TestableBoatSetupService();
        service.tripReclassificationService = tripReclassificationService;
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        doThrow(new IllegalStateException("boom"))
                .when(tripReclassificationService)
                .reclassifyUserTrips(userId);

        boolean result = service.reclassifyExistingTrips(userId, jobId);

        assertFalse(result);
        assertEquals("TRIP_RECLASSIFICATION_FAILED", service.failedErrorCode);
        assertTrue(service.failedErrorMessage.contains("failed to reclassify existing trips"));
        assertTrue(service.failedErrorMessage.contains("boom"));
        verify(tripReclassificationService).reclassifyUserTrips(userId);
    }

    private static class TestableBoatSetupService extends BoatSetupService {
        String updatedStatus;
        String updatedEnvironmentStatus;
        String updatedPhase;
        int updatedProgress;
        String failedErrorCode;
        String failedErrorMessage;

        @Override
        void updateJob(UUID jobId,
                       String status,
                       String datasetStatus,
                       String environmentStatus,
                       String phase,
                       int progress,
                       Map<String, Object> details) {
            this.updatedStatus = status;
            this.updatedEnvironmentStatus = environmentStatus;
            this.updatedPhase = phase;
            this.updatedProgress = progress;
        }

        @Override
        void failJob(UUID jobId, String errorCode, String errorMessage) {
            this.failedErrorCode = errorCode;
            this.failedErrorMessage = errorMessage;
        }
    }
}

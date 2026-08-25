package org.github.tess1o.geopulse.mapmatching.service;

import org.github.tess1o.geopulse.mapmatching.dto.MapMatchingAdminStatusDTO;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingBackfillProgress;
import org.github.tess1o.geopulse.mapmatching.repository.MapMatchingReconciliationRepository;
import org.github.tess1o.geopulse.mapmatching.repository.TimelineTripPathMatchRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class MapMatchingWorkerStatusTest {

    @Mock
    MapMatchingConfiguration configuration;
    @Mock
    TimelineTripPathMatchRepository targetRepository;
    @Mock
    MapMatchingReconciliationRepository reconciliationRepository;
    @Mock
    TimelineTripRepository tripRepository;
    @Mock
    MapMatchingService mapMatchingService;

    MapMatchingWorker worker;

    @BeforeEach
    void setUp() {
        worker = new MapMatchingWorker(
                configuration, targetRepository, reconciliationRepository, tripRepository, mapMatchingService);
    }

    @Test
    void separatesWorkerBackfillQueueAndDiagnosticStatus() {
        Instant targetActivity = Instant.parse("2026-08-22T21:10:00Z");
        Instant reconciliationActivity = Instant.parse("2026-08-22T21:11:00Z");
        Instant oldestQueued = Instant.parse("2026-08-22T20:00:00Z");

        when(configuration.isEnabled()).thenReturn(true);
        when(configuration.valhallaConfigured()).thenReturn(true);
        when(configuration.backfillEnabled()).thenReturn(true);
        when(targetRepository.countByStatus()).thenReturn(Map.of(
                "PENDING", 265L,
                "PROCESSING", 9L,
                "MATCHED", 12_709L,
                "FAILED", 348L));
        when(targetRepository.countBySource()).thenReturn(Map.of("HISTORICAL", 13_331L));
        when(targetRepository.oldestQueuedAt()).thenReturn(oldestQueued);
        when(targetRepository.lastUpdatedAt()).thenReturn(targetActivity);
        when(reconciliationRepository.lastUpdatedAt()).thenReturn(reconciliationActivity);
        when(reconciliationRepository.historicalProgress()).thenReturn(
                new MapMatchingBackfillProgress(18_341, 12_850, 49, 13, reconciliationActivity));
        when(reconciliationRepository.countPending()).thenReturn(36L);

        MapMatchingAdminStatusDTO status = worker.status();

        assertThat(status.getBackfill().getPercent()).isEqualTo(70.06161059920397);
        assertThat(status.getBackfill().getRemainingTrips()).isEqualTo(5_491);
        assertThat(status.getBackfill().getRemainingUsers()).isEqualTo(36);
        assertThat(status.getQueue().getQueued()).isEqualTo(265);
        assertThat(status.getQueue().getProcessing()).isEqualTo(9);
        assertThat(status.getQueue().getOldestQueuedAt()).isEqualTo(oldestQueued);
        assertThat(status.getWorker().getLastActivityAt()).isEqualTo(reconciliationActivity);
        assertThat(status.getDiagnostics().getTargetsByStatus().get("MATCHED")).isEqualTo(12_709);
    }
}

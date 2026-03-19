package org.github.tess1o.geopulse.geofencing.service;

import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class GeofenceEventCleanupServiceTest {

    @Mock
    GeofenceEventRepository eventRepository;

    @Mock
    SystemSettingsService settingsService;

    private GeofenceEventCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        cleanupService = new GeofenceEventCleanupService(eventRepository, settingsService);
    }

    @Test
    void shouldSkipCleanupWhenDisabled() {
        when(settingsService.getBoolean(GeofenceEventCleanupService.KEY_ENABLED)).thenReturn(false);

        cleanupService.cleanupOldEvents();

        verifyNoInteractions(eventRepository);
        verify(settingsService, never()).getInteger(GeofenceEventCleanupService.KEY_INTERVAL_DAYS);
        verify(settingsService, never()).getInteger(GeofenceEventCleanupService.KEY_RETENTION_DAYS);
    }

    @Test
    void shouldDeleteEventsOlderThanRetentionWindow() {
        when(settingsService.getBoolean(GeofenceEventCleanupService.KEY_ENABLED)).thenReturn(true);
        when(settingsService.getInteger(GeofenceEventCleanupService.KEY_INTERVAL_DAYS)).thenReturn(1);
        when(settingsService.getInteger(GeofenceEventCleanupService.KEY_RETENTION_DAYS)).thenReturn(90);
        when(eventRepository.deleteOlderThan(any())).thenReturn(5L);

        Instant before = Instant.now();
        cleanupService.cleanupOldEvents();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(eventRepository).deleteOlderThan(cutoffCaptor.capture());

        Instant cutoff = cutoffCaptor.getValue();
        assertThat(cutoff).isBetween(
                before.minus(90, ChronoUnit.DAYS).minusSeconds(1),
                after.minus(90, ChronoUnit.DAYS).plusSeconds(1)
        );
    }

    @Test
    void shouldRespectCleanupIntervalDays() {
        when(settingsService.getBoolean(GeofenceEventCleanupService.KEY_ENABLED)).thenReturn(true);
        when(settingsService.getInteger(GeofenceEventCleanupService.KEY_INTERVAL_DAYS)).thenReturn(2);
        when(settingsService.getInteger(GeofenceEventCleanupService.KEY_RETENTION_DAYS)).thenReturn(90);
        when(eventRepository.deleteOlderThan(any())).thenReturn(0L);

        cleanupService.cleanupOldEvents();
        cleanupService.cleanupOldEvents();

        verify(eventRepository, times(1)).deleteOlderThan(any());
    }
}

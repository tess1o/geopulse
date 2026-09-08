package org.github.tess1o.geopulse.notifications.service;

import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.notifications.model.NotificationPreferences;
import org.github.tess1o.geopulse.notifications.model.entity.GpsHealthIncidentEntity;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;
import org.github.tess1o.geopulse.notifications.repository.GpsHealthIncidentRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class GpsHealthMonitoringServiceTest {
    @Mock UserRepository users;
    @Mock GpsPointRepository points;
    @Mock GpsHealthIncidentRepository incidents;
    @Mock NotificationPreferencesService preferences;
    @Mock NotificationPublisherService publisher;

    @Test
    void opensOneIncidentWhenNoGpsPointWasReceivedPastThreshold() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-08T10:00:00Z");
        UserEntity user = new UserEntity();
        user.setId(userId);
        NotificationPreferences prefs = NotificationPreferences.builder().gpsHealthEnabled(true).gpsSilenceMinutes(30)
                .gpsMonitoringStartedAt(now.minusSeconds(3600)).build();
        when(users.findActiveUsers()).thenReturn(List.of(user));
        when(preferences.getEntityPreferences(user)).thenReturn(prefs);
        when(points.findLatestReceivedByUserId(userId)).thenReturn(null);
        when(incidents.findById(userId)).thenReturn(null);

        new GpsHealthMonitoringService(users, points, incidents, preferences, publisher).checkHealthAt(now);

        verify(incidents).persist(any(GpsHealthIncidentEntity.class));
        ArgumentCaptor<NotificationType> type = ArgumentCaptor.forClass(NotificationType.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(publisher).publish(eq(user), any(), type.capture(), any(), body.capture(), any(), any(), any());
        assertThat(type.getValue()).isEqualTo(NotificationType.GPS_HEALTH_INCIDENT_OPENED);
        assertThat(body.getValue()).isEqualTo("GeoPulse has not received a GPS point for 30 minutes.");
    }

    @Test
    void doesNotOpenAnIncidentDuringTheInitialGracePeriod() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-08T10:00:00Z");
        UserEntity user = new UserEntity(); user.setId(userId);
        NotificationPreferences prefs = NotificationPreferences.builder().gpsHealthEnabled(true).gpsSilenceMinutes(30)
                .gpsMonitoringStartedAt(now.minusSeconds(60)).build();
        when(users.findActiveUsers()).thenReturn(List.of(user));
        when(preferences.getEntityPreferences(user)).thenReturn(prefs);
        when(incidents.findById(userId)).thenReturn(null);

        new GpsHealthMonitoringService(users, points, incidents, preferences, publisher).checkHealthAt(now);

        verify(incidents, never()).persist(any(GpsHealthIncidentEntity.class));
        verifyNoInteractions(publisher);
    }

    @Test
    void resolvesAnOpenIncidentWhenAnyGpsPointArrives() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-08T10:00:00Z");
        UserEntity user = new UserEntity(); user.setId(userId);
        NotificationPreferences prefs = NotificationPreferences.builder().gpsHealthEnabled(true).gpsSilenceMinutes(30)
                .gpsMonitoringStartedAt(now.minusSeconds(3600)).build();
        GpsHealthIncidentEntity incident = new GpsHealthIncidentEntity(); incident.setOpenedAt(now.minusSeconds(1800));
        when(users.findActiveUsers()).thenReturn(List.of(user));
        when(preferences.getEntityPreferences(user)).thenReturn(prefs);
        when(points.findLatestReceivedByUserId(userId)).thenReturn(now.minusSeconds(10));
        when(incidents.findById(userId)).thenReturn(incident);

        new GpsHealthMonitoringService(users, points, incidents, preferences, publisher).checkHealthAt(now);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(publisher).publish(eq(user), any(), eq(NotificationType.GPS_HEALTH_INCIDENT_RESOLVED), any(), body.capture(), any(), any(), any());
        assertThat(incident.getOpenedAt()).isNull();
        assertThat(body.getValue()).isEqualTo("GeoPulse is receiving GPS points again.");
    }

    @Test
    void resolvesAnOpenIncidentDuringTheNewMonitorGracePeriod() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-08T10:00:00Z");
        UserEntity user = new UserEntity(); user.setId(userId);
        NotificationPreferences prefs = NotificationPreferences.builder().gpsHealthEnabled(true).gpsSilenceMinutes(60)
                .gpsMonitoringStartedAt(now.minusSeconds(60)).build();
        GpsHealthIncidentEntity incident = new GpsHealthIncidentEntity(); incident.setOpenedAt(now.minusSeconds(1800));
        when(users.findActiveUsers()).thenReturn(List.of(user));
        when(preferences.getEntityPreferences(user)).thenReturn(prefs);
        when(points.findLatestReceivedByUserId(userId)).thenReturn(now.minusSeconds(10));
        when(incidents.findById(userId)).thenReturn(incident);

        new GpsHealthMonitoringService(users, points, incidents, preferences, publisher).checkHealthAt(now);

        verify(publisher).publish(eq(user), any(), eq(NotificationType.GPS_HEALTH_INCIDENT_RESOLVED), any(), any(), any(), any(), any());
        assertThat(incident.getOpenedAt()).isNull();
    }

    @Test
    void doesNotSendAgainWhileAnIncidentIsAlreadyOpen() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-08T10:00:00Z");
        UserEntity user = new UserEntity(); user.setId(userId);
        NotificationPreferences prefs = NotificationPreferences.builder().gpsHealthEnabled(true).gpsSilenceMinutes(30)
                .gpsMonitoringStartedAt(now.minusSeconds(3600)).build();
        GpsHealthIncidentEntity incident = new GpsHealthIncidentEntity(); incident.setOpenedAt(now.minusSeconds(1800));
        when(users.findActiveUsers()).thenReturn(List.of(user));
        when(preferences.getEntityPreferences(user)).thenReturn(prefs);
        when(points.findLatestReceivedByUserId(userId)).thenReturn(now.minusSeconds(1801));
        when(incidents.findById(userId)).thenReturn(incident);

        new GpsHealthMonitoringService(users, points, incidents, preferences, publisher).checkHealthAt(now);

        verify(incidents, never()).persist(any(GpsHealthIncidentEntity.class));
        verifyNoInteractions(publisher);
    }
}

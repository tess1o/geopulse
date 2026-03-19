package org.github.tess1o.geopulse.notifications.service;

import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceDeliveryStatus;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventType;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;
import org.github.tess1o.geopulse.notifications.model.entity.UserNotificationEntity;
import org.github.tess1o.geopulse.notifications.repository.UserNotificationRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class GeofenceNotificationProjectionServiceTest {

    @Mock
    UserNotificationRepository notificationRepository;

    private GeofenceNotificationProjectionService service;

    @BeforeEach
    void setUp() {
        service = new GeofenceNotificationProjectionService(notificationRepository);
    }

    @Test
    void shouldPersistSnapshotForNewGeofenceEvent() {
        UUID ownerId = UUID.randomUUID();
        GeofenceEventEntity event = geofenceEvent(10L, ownerId, GeofenceEventType.ENTER, GeofenceDeliveryStatus.PENDING);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("ruleId", 5L);
        metadata.put("eventCode", "ENTER");

        when(notificationRepository.findByDedupeKey("geofence-event:10")).thenReturn(Optional.empty());

        service.publishSnapshot(event, metadata);

        ArgumentCaptor<UserNotificationEntity> captor = ArgumentCaptor.forClass(UserNotificationEntity.class);
        verify(notificationRepository).persist(captor.capture());

        UserNotificationEntity persisted = captor.getValue();
        assertThat(persisted.getOwnerUser().getId()).isEqualTo(ownerId);
        assertThat(persisted.getSource()).isEqualTo(NotificationSource.GEOFENCE);
        assertThat(persisted.getType()).isEqualTo(NotificationType.GEOFENCE_ENTER);
        assertThat(persisted.getTitle()).isEqualTo("Title 10");
        assertThat(persisted.getMessage()).isEqualTo("Message 10");
        assertThat(persisted.getObjectRef()).isEqualTo("10");
        assertThat(persisted.getDedupeKey()).isEqualTo("geofence-event:10");
        assertThat(persisted.getMetadata()).containsEntry("eventCode", "ENTER");
        assertThat(persisted.getDeliveryStatus()).isEqualTo(GeofenceDeliveryStatus.PENDING);
    }

    @Test
    void shouldUpdateExistingSnapshotWithoutPersist() {
        UUID ownerId = UUID.randomUUID();
        GeofenceEventEntity event = geofenceEvent(22L, ownerId, GeofenceEventType.LEAVE, GeofenceDeliveryStatus.SENT);
        Map<String, Object> metadata = Map.of("eventCode", "LEAVE");

        UserNotificationEntity existing = UserNotificationEntity.builder()
                .id(500L)
                .dedupeKey("geofence-event:22")
                .build();
        when(notificationRepository.findByDedupeKey("geofence-event:22")).thenReturn(Optional.of(existing));

        service.publishSnapshot(event, metadata);

        verify(notificationRepository, never()).persist(any(UserNotificationEntity.class));
        assertThat(existing.getOwnerUser().getId()).isEqualTo(ownerId);
        assertThat(existing.getSource()).isEqualTo(NotificationSource.GEOFENCE);
        assertThat(existing.getType()).isEqualTo(NotificationType.GEOFENCE_LEAVE);
        assertThat(existing.getDeliveryStatus()).isEqualTo(GeofenceDeliveryStatus.SENT);
        assertThat(existing.getMetadata()).containsEntry("eventCode", "LEAVE");
    }

    @Test
    void shouldIgnorePublishSnapshotWhenRequiredFieldsMissing() {
        service.publishSnapshot(null, Map.of());
        verifyNoInteractions(notificationRepository);

        GeofenceEventEntity missingId = geofenceEvent(null, UUID.randomUUID(), GeofenceEventType.ENTER, GeofenceDeliveryStatus.PENDING);
        service.publishSnapshot(missingId, Map.of());
        verifyNoMoreInteractions(notificationRepository);

        GeofenceEventEntity missingOwner = geofenceEvent(33L, null, GeofenceEventType.ENTER, GeofenceDeliveryStatus.PENDING);
        service.publishSnapshot(missingOwner, Map.of());
        verifyNoMoreInteractions(notificationRepository);
    }

    @Test
    void shouldSyncDeliveryStatusBySourceAndObjectRef() {
        UUID ownerId = UUID.randomUUID();
        UserNotificationEntity existing = UserNotificationEntity.builder()
                .id(501L)
                .deliveryStatus(GeofenceDeliveryStatus.PENDING)
                .build();
        when(notificationRepository.findBySourceAndObjectRefAndOwner(NotificationSource.GEOFENCE, "44", ownerId))
                .thenReturn(Optional.of(existing));

        service.syncDeliveryStatus(ownerId, 44L, GeofenceDeliveryStatus.FAILED);

        verify(notificationRepository).findBySourceAndObjectRefAndOwner(NotificationSource.GEOFENCE, "44", ownerId);
        assertThat(existing.getDeliveryStatus()).isEqualTo(GeofenceDeliveryStatus.FAILED);
        verify(notificationRepository, never()).findByDedupeKey(any());
    }

    @Test
    void shouldFallbackToDedupeLookupWhenDirectSyncMisses() {
        UUID ownerId = UUID.randomUUID();
        UserNotificationEntity existing = UserNotificationEntity.builder()
                .id(900L)
                .deliveryStatus(GeofenceDeliveryStatus.PENDING)
                .build();
        when(notificationRepository.findBySourceAndObjectRefAndOwner(NotificationSource.GEOFENCE, "55", ownerId))
                .thenReturn(Optional.empty());
        when(notificationRepository.findByDedupeKey("geofence-event:55")).thenReturn(Optional.of(existing));

        service.syncDeliveryStatus(ownerId, 55L, GeofenceDeliveryStatus.SENT);

        assertThat(existing.getDeliveryStatus()).isEqualTo(GeofenceDeliveryStatus.SENT);
    }

    @Test
    void shouldIgnoreSyncWhenOwnerOrEventIdMissing() {
        service.syncDeliveryStatus(null, 1L, GeofenceDeliveryStatus.SENT);
        service.syncDeliveryStatus(UUID.randomUUID(), null, GeofenceDeliveryStatus.SENT);

        verifyNoInteractions(notificationRepository);
    }

    private GeofenceEventEntity geofenceEvent(Long eventId,
                                              UUID ownerId,
                                              GeofenceEventType eventType,
                                              GeofenceDeliveryStatus deliveryStatus) {
        UserEntity owner = null;
        if (ownerId != null) {
            owner = new UserEntity();
            owner.setId(ownerId);
        }

        return GeofenceEventEntity.builder()
                .id(eventId)
                .ownerUser(owner)
                .eventType(eventType)
                .title("Title " + eventId)
                .message("Message " + eventId)
                .occurredAt(Instant.parse("2026-03-19T12:00:00Z"))
                .deliveryStatus(deliveryStatus)
                .build();
    }
}

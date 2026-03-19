package org.github.tess1o.geopulse.notifications.service;

import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceDeliveryStatus;
import org.github.tess1o.geopulse.notifications.model.dto.UnreadCountDto;
import org.github.tess1o.geopulse.notifications.model.dto.UserNotificationDto;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class UserNotificationServiceTest {

    @Mock
    UserNotificationRepository notificationRepository;

    private UserNotificationService service;

    @BeforeEach
    void setUp() {
        service = new UserNotificationService(notificationRepository);
    }

    @Test
    void shouldClampListLimitToMinimumOne() {
        UUID ownerId = UUID.randomUUID();
        when(notificationRepository.findByOwner(ownerId, 1, false, null)).thenReturn(List.of());

        service.listNotifications(ownerId, 0, false, null);

        verify(notificationRepository).findByOwner(ownerId, 1, false, null);
    }

    @Test
    void shouldClampListLimitToMaximum() {
        UUID ownerId = UUID.randomUUID();
        when(notificationRepository.findByOwner(ownerId, 200, true, NotificationSource.GEOFENCE)).thenReturn(List.of());

        service.listNotifications(ownerId, 999, true, NotificationSource.GEOFENCE);

        verify(notificationRepository).findByOwner(ownerId, 200, true, NotificationSource.GEOFENCE);
    }

    @Test
    void shouldResolveUnreadCountWithoutLatestIdWhenNoUnread() {
        UUID ownerId = UUID.randomUUID();
        when(notificationRepository.countUnreadByOwner(ownerId, null)).thenReturn(0L);

        UnreadCountDto result = service.getUnreadCount(ownerId, null);

        assertThat(result.getCount()).isZero();
        assertThat(result.getLatestUnreadId()).isNull();
        verify(notificationRepository, never()).findLatestUnreadIdByOwner(any(), any());
    }

    @Test
    void shouldResolveUnreadCountWithLatestIdWhenUnreadExists() {
        UUID ownerId = UUID.randomUUID();
        when(notificationRepository.countUnreadByOwner(ownerId, NotificationSource.GEOFENCE)).thenReturn(3L);
        when(notificationRepository.findLatestUnreadIdByOwner(ownerId, NotificationSource.GEOFENCE)).thenReturn(42L);

        UnreadCountDto result = service.getUnreadCount(ownerId, NotificationSource.GEOFENCE);

        assertThat(result.getCount()).isEqualTo(3L);
        assertThat(result.getLatestUnreadId()).isEqualTo(42L);
        verify(notificationRepository).findLatestUnreadIdByOwner(ownerId, NotificationSource.GEOFENCE);
    }

    @Test
    void shouldMarkNotificationSeenWhenUnseen() {
        UUID ownerId = UUID.randomUUID();
        UserNotificationEntity entity = baseEntity(ownerId, null);
        when(notificationRepository.findByIdAndOwner(10L, ownerId)).thenReturn(Optional.of(entity));

        UserNotificationDto result = service.markSeen(ownerId, 10L);

        assertThat(entity.getSeenAt()).isNotNull();
        assertThat(result.getSeen()).isTrue();
        assertThat(result.getSeenAt()).isNotNull();
    }

    @Test
    void shouldKeepExistingSeenTimestampWhenAlreadySeen() {
        UUID ownerId = UUID.randomUUID();
        Instant existingSeenAt = Instant.parse("2026-03-19T10:00:00Z");
        UserNotificationEntity entity = baseEntity(ownerId, existingSeenAt);
        when(notificationRepository.findByIdAndOwner(10L, ownerId)).thenReturn(Optional.of(entity));

        UserNotificationDto result = service.markSeen(ownerId, 10L);

        assertThat(entity.getSeenAt()).isEqualTo(existingSeenAt);
        assertThat(result.getSeenAt()).isEqualTo(existingSeenAt);
    }

    @Test
    void shouldThrowWhenMarkSeenNotificationNotFound() {
        UUID ownerId = UUID.randomUUID();
        when(notificationRepository.findByIdAndOwner(99L, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markSeen(ownerId, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Notification not found");
    }

    @Test
    void shouldMarkAllSeenForOwnerAndSource() {
        UUID ownerId = UUID.randomUUID();
        when(notificationRepository.markAllSeenByOwner(eq(ownerId), any(Instant.class), eq(NotificationSource.GEOFENCE)))
                .thenReturn(5L);

        long updated = service.markAllSeen(ownerId, NotificationSource.GEOFENCE);

        assertThat(updated).isEqualTo(5L);
        verify(notificationRepository).markAllSeenByOwner(eq(ownerId), any(Instant.class), eq(NotificationSource.GEOFENCE));
    }

    @Test
    void shouldMapEntityToDtoWithSeenFlag() {
        UUID ownerId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-03-19T11:00:00Z");
        Instant seenAt = Instant.parse("2026-03-19T11:05:00Z");

        UserNotificationEntity entity = baseEntity(ownerId, seenAt);
        entity.setId(77L);
        entity.setOccurredAt(occurredAt);
        entity.setTitle("Title");
        entity.setMessage("Message");
        entity.setObjectRef("123");
        entity.setMetadata(Map.of("ruleName", "Home"));

        UserNotificationDto dto = service.toDto(entity);

        assertThat(dto.getId()).isEqualTo(77L);
        assertThat(dto.getSource()).isEqualTo(NotificationSource.GEOFENCE);
        assertThat(dto.getType()).isEqualTo(NotificationType.GEOFENCE_ENTER);
        assertThat(dto.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(dto.getSeen()).isTrue();
        assertThat(dto.getDeliveryStatus()).isEqualTo(GeofenceDeliveryStatus.PENDING);
        assertThat(dto.getMetadata()).containsEntry("ruleName", "Home");
    }

    @Test
    void shouldMapListNotificationsToDtos() {
        UUID ownerId = UUID.randomUUID();
        UserNotificationEntity unseen = baseEntity(ownerId, null);
        unseen.setId(1L);
        unseen.setTitle("A");

        UserNotificationEntity seen = baseEntity(ownerId, Instant.parse("2026-03-19T12:00:00Z"));
        seen.setId(2L);
        seen.setTitle("B");
        when(notificationRepository.findByOwner(ownerId, 50, false, null)).thenReturn(List.of(unseen, seen));

        List<UserNotificationDto> result = service.listNotifications(ownerId, 50, false, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSeen()).isFalse();
        assertThat(result.get(1).getSeen()).isTrue();
    }

    private UserNotificationEntity baseEntity(UUID ownerId, Instant seenAt) {
        UserEntity owner = new UserEntity();
        owner.setId(ownerId);

        return UserNotificationEntity.builder()
                .ownerUser(owner)
                .source(NotificationSource.GEOFENCE)
                .type(NotificationType.GEOFENCE_ENTER)
                .title("Alert")
                .message("Body")
                .occurredAt(Instant.parse("2026-03-19T09:00:00Z"))
                .seenAt(seenAt)
                .deliveryStatus(GeofenceDeliveryStatus.PENDING)
                .objectRef("geofence-1")
                .metadata(Map.of("eventCode", "ENTER"))
                .dedupeKey("geofence-event:1")
                .build();
    }
}

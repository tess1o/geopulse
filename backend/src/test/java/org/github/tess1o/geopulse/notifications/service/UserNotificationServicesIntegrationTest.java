package org.github.tess1o.geopulse.notifications.service;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceDeliveryStatus;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventType;
import org.github.tess1o.geopulse.notifications.model.dto.UnreadCountDto;
import org.github.tess1o.geopulse.notifications.model.dto.UserNotificationDto;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;
import org.github.tess1o.geopulse.notifications.model.entity.UserNotificationEntity;
import org.github.tess1o.geopulse.notifications.repository.UserNotificationRepository;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class UserNotificationServicesIntegrationTest {
    @Inject
    GeofenceNotificationProjectionService projectionService;
    @Inject
    UserNotificationService userNotificationService;
    @Inject
    UserNotificationRepository userNotificationRepository;
    @Inject
    UserRepository userRepository;
    private UserEntity owner;
    @BeforeEach
    @Transactional
    void setUp() {
        owner = createUser("notifications-owner");
    }
    @AfterEach
    @Transactional
    void tearDown() {
        if (owner != null && owner.getId() != null) {
            userRepository.deleteById(owner.getId());
        }
    }
    @Test
    @Transactional
    void shouldProjectGeofenceEventAndExposeInInbox() {
        GeofenceEventEntity event = geofenceEvent(1001L, GeofenceEventType.ENTER, GeofenceDeliveryStatus.PENDING);
        projectionService.publishSnapshot(event, Map.of(
                "ruleName", "Home",
                "eventCode", "ENTER",
                "eventVerb", "entered"
        ));
        List<UserNotificationDto> list = userNotificationService.listNotifications(owner.getId(), 50);
        assertThat(list).hasSize(1);
        UserNotificationDto dto = list.getFirst();
        assertThat(dto.getSource()).isEqualTo(NotificationSource.GEOFENCE);
        assertThat(dto.getType()).isEqualTo(NotificationType.GEOFENCE_ENTER);
        assertThat(dto.getDeliveryStatus()).isEqualTo(GeofenceDeliveryStatus.PENDING);
        assertThat(dto.getMetadata()).containsEntry("ruleName", "Home");
        UnreadCountDto unreadCount = userNotificationService.getUnreadCount(owner.getId());
        assertThat(unreadCount.getCount()).isEqualTo(1L);
        assertThat(unreadCount.getLatestUnreadId()).isNotNull();
    }
    @Test
    @Transactional
    void shouldBeIdempotentByDedupeAndSyncDeliveryStatus() {
        GeofenceEventEntity event = geofenceEvent(1002L, GeofenceEventType.LEAVE, GeofenceDeliveryStatus.PENDING);
        projectionService.publishSnapshot(event, Map.of("eventCode", "LEAVE"));
        projectionService.publishSnapshot(event, Map.of("eventCode", "LEAVE", "ruleName", "Office"));
        assertThat(userNotificationRepository.count("ownerUser.id = ?1", owner.getId())).isEqualTo(1L);
        UserNotificationEntity persisted = userNotificationRepository
                .findBySourceAndObjectRefAndOwner(NotificationSource.GEOFENCE, "1002", owner.getId())
                .orElseThrow();
        assertThat(persisted.getType()).isEqualTo(NotificationType.GEOFENCE_LEAVE);
        assertThat(persisted.getMetadata()).containsEntry("ruleName", "Office");
        projectionService.syncDeliveryStatus(owner.getId(), 1002L, GeofenceDeliveryStatus.SENT);
        Optional<UserNotificationEntity> updated = userNotificationRepository
                .findBySourceAndObjectRefAndOwner(NotificationSource.GEOFENCE, "1002", owner.getId());
        assertThat(updated).isPresent();
        assertThat(updated.get().getDeliveryStatus()).isEqualTo(GeofenceDeliveryStatus.SENT);
    }
    @Test
    @Transactional
    void shouldMarkSeenAndMarkAllSeenViaService() {
        projectionService.publishSnapshot(geofenceEvent(1003L, GeofenceEventType.ENTER, GeofenceDeliveryStatus.SKIPPED), Map.of());
        projectionService.publishSnapshot(geofenceEvent(1004L, GeofenceEventType.LEAVE, GeofenceDeliveryStatus.FAILED), Map.of());
        List<UserNotificationDto> before = userNotificationService.listNotifications(owner.getId(), 50);
        assertThat(before).hasSize(2);
        UserNotificationDto markedOne = userNotificationService.markSeen(owner.getId(), before.getFirst().getId());
        assertThat(markedOne.getSeen()).isTrue();
        long updated = userNotificationService.markAllSeen(owner.getId());
        assertThat(updated).isEqualTo(1L);
        UnreadCountDto unreadCount = userNotificationService.getUnreadCount(owner.getId());
        assertThat(unreadCount.getCount()).isZero();
        assertThat(unreadCount.getLatestUnreadId()).isNull();
    }
    private GeofenceEventEntity geofenceEvent(Long id, GeofenceEventType type, GeofenceDeliveryStatus deliveryStatus) {
        return GeofenceEventEntity.builder()
                .id(id)
                .ownerUser(owner)
                .eventType(type)
                .occurredAt(Instant.parse("2026-03-19T15:00:00Z"))
                .title("Title " + id)
                .message("Message " + id)
                .deliveryStatus(deliveryStatus)
                .createdAt(Instant.now())
                .build();
    }
    private UserEntity createUser(String prefix) {
        UserEntity user = new UserEntity();
        user.setEmail(TestIds.uniqueEmail(prefix));
        user.setPasswordHash("test-hash");
        user.setFullName(prefix);
        user.setTimezone("UTC");
        userRepository.persist(user);
        return user;
    }
}

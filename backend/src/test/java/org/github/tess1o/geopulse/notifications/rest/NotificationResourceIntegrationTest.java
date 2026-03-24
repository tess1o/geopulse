package org.github.tess1o.geopulse.notifications.rest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Transactional;
import jakarta.transaction.UserTransaction;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceDeliveryStatus;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventType;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleStatus;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceEventRepository;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceRuleRepository;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;
import org.github.tess1o.geopulse.notifications.model.entity.UserNotificationEntity;
import org.github.tess1o.geopulse.notifications.repository.UserNotificationRepository;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class, restrictToAnnotatedClass = true)
@SerializedDatabaseTest
class NotificationResourceIntegrationTest {

    @Inject
    UserService userService;

    @Inject
    AuthenticationService authenticationService;

    @Inject
    UserRepository userRepository;

    @Inject
    UserNotificationRepository notificationRepository;

    @Inject
    GeofenceRuleRepository ruleRepository;

    @Inject
    GeofenceEventRepository eventRepository;

    @Inject
    UserTransaction userTransaction;

    private UserEntity ownerUser;
    private UserEntity otherUser;
    private String ownerToken;

    @BeforeEach
    @Transactional
    void setUp() {
        notificationRepository.deleteAll();
        eventRepository.deleteAll();
        ruleRepository.deleteAll();
        userRepository.deleteAll();

        ownerUser = userService.registerUser(
                "notifications-owner-" + System.nanoTime() + "@example.com",
                "password123",
                "Owner User",
                "UTC"
        );
        otherUser = userService.registerUser(
                "notifications-other-" + System.nanoTime() + "@example.com",
                "password123",
                "Other User",
                "UTC"
        );

        AuthResponse auth = authenticationService.authenticate(ownerUser.getEmail(), "password123");
        ownerToken = auth.getAccessToken();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        notificationRepository.deleteAll();
        eventRepository.deleteAll();
        ruleRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldRequireAuthentication() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/notifications")
                .then()
                .statusCode(401);
    }

    @Test
    void shouldListOnlyOwnerNotifications() {
        createNotification(ownerUser,
                NotificationSource.GEOFENCE,
                NotificationType.GEOFENCE_ENTER,
                "Owner geofence unread",
                null,
                Instant.parse("2026-03-19T10:00:00Z"));
        createNotification(ownerUser,
                NotificationSource.GEOFENCE,
                NotificationType.GEOFENCE_LEAVE,
                "Owner geofence seen",
                Instant.parse("2026-03-19T11:00:00Z"),
                Instant.parse("2026-03-19T09:00:00Z"));
        createNotification(ownerUser,
                NotificationSource.IMPORT,
                NotificationType.IMPORT_COMPLETED,
                "Owner import unread",
                null,
                Instant.parse("2026-03-19T08:00:00Z"));
        createNotification(otherUser,
                NotificationSource.GEOFENCE,
                NotificationType.GEOFENCE_ENTER,
                "Other user geofence",
                null,
                Instant.parse("2026-03-19T12:00:00Z"));

        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/notifications?limit=50")
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("data.size()", equalTo(3))
                .body("data.title", hasItems("Owner geofence unread", "Owner geofence seen", "Owner import unread"))
                .body("data.title", not(hasItem("Other user geofence")));
    }

    @Test
    void shouldReturnUnreadCountAndLatestUnreadId() {
        createNotification(ownerUser,
                NotificationSource.GEOFENCE,
                NotificationType.GEOFENCE_ENTER,
                "Older unread",
                null,
                Instant.parse("2026-03-19T07:00:00Z"));
        createNotification(ownerUser,
                NotificationSource.GEOFENCE,
                NotificationType.GEOFENCE_LEAVE,
                "Seen",
                Instant.parse("2026-03-19T11:00:00Z"),
                Instant.parse("2026-03-19T08:00:00Z"));
        UserNotificationEntity newestUnread = createNotification(ownerUser,
                NotificationSource.IMPORT,
                NotificationType.IMPORT_COMPLETED,
                "Newest unread",
                null,
                Instant.parse("2026-03-19T09:00:00Z"));

        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/notifications/unread-count")
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("data.count", equalTo(2))
                .body("data.latestUnreadId", equalTo(newestUnread.getId().intValue()));
    }

    @Test
    void shouldMarkSeenAndEnforceOwnerAccess() {
        GeofenceRuleEntity ownerRule = createRule(ownerUser, "Owner geofence");
        GeofenceEventEntity ownerEvent = createEvent(
                ownerUser,
                ownerUser,
                ownerRule,
                GeofenceEventType.ENTER,
                Instant.parse("2026-03-19T10:00:00Z"),
                null
        );
        GeofenceRuleEntity otherRule = createRule(otherUser, "Other geofence");
        GeofenceEventEntity otherEvent = createEvent(
                otherUser,
                otherUser,
                otherRule,
                GeofenceEventType.ENTER,
                Instant.parse("2026-03-19T10:00:00Z"),
                null
        );

        UserNotificationEntity ownerNotification = createNotification(ownerUser,
                NotificationSource.GEOFENCE,
                NotificationType.GEOFENCE_ENTER,
                "Owner unread",
                null,
                Instant.parse("2026-03-19T10:00:00Z"),
                String.valueOf(ownerEvent.getId()),
                "geofence-event:" + ownerEvent.getId());
        UserNotificationEntity otherNotification = createNotification(otherUser,
                NotificationSource.GEOFENCE,
                NotificationType.GEOFENCE_ENTER,
                "Other unread",
                null,
                Instant.parse("2026-03-19T10:00:00Z"),
                String.valueOf(otherEvent.getId()),
                "geofence-event:" + otherEvent.getId());

        given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .when()
                .post("/api/notifications/" + ownerNotification.getId() + "/seen")
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("data.id", equalTo(ownerNotification.getId().intValue()))
                .body("data.seen", equalTo(true))
                .body("data.seenAt", notNullValue());
        assertGeofenceEventSeen(ownerEvent.getId(), true);

        given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .when()
                .post("/api/notifications/" + otherNotification.getId() + "/seen")
                .then()
                .statusCode(400)
                .body("status", equalTo("error"))
                .body("message", containsString("Notification not found"));
        assertGeofenceEventSeen(otherEvent.getId(), false);
    }

    @Test
    void shouldMarkAllSeenGlobally() {
        GeofenceRuleEntity ownerRule = createRule(ownerUser, "Owner geofence");
        GeofenceEventEntity ownerEvent = createEvent(
                ownerUser,
                ownerUser,
                ownerRule,
                GeofenceEventType.ENTER,
                Instant.parse("2026-03-19T10:00:00Z"),
                null
        );

        createNotification(ownerUser,
                NotificationSource.GEOFENCE,
                NotificationType.GEOFENCE_ENTER,
                "Geofence unread",
                null,
                Instant.parse("2026-03-19T10:00:00Z"),
                String.valueOf(ownerEvent.getId()),
                "geofence-event:" + ownerEvent.getId());
        createNotification(ownerUser,
                NotificationSource.IMPORT,
                NotificationType.IMPORT_COMPLETED,
                "Import unread",
                null,
                Instant.parse("2026-03-19T09:00:00Z"));

        given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .when()
                .post("/api/notifications/seen-all")
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("data.updatedCount", equalTo(2));

        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/notifications/unread-count")
                .then()
                .statusCode(200)
                .body("data.count", equalTo(0))
                .body("data.latestUnreadId", nullValue());
        assertGeofenceEventSeen(ownerEvent.getId(), true);
    }

    @Test
    void shouldRollbackWhenGeofenceMarkSeenSyncFails() {
        UserNotificationEntity brokenGeofenceNotification = createNotification(
                ownerUser,
                NotificationSource.GEOFENCE,
                NotificationType.GEOFENCE_ENTER,
                "Broken geofence notification",
                null,
                Instant.parse("2026-03-19T10:00:00Z"),
                "not-a-number",
                "broken-geofence-notification"
        );

        given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .when()
                .post("/api/notifications/" + brokenGeofenceNotification.getId() + "/seen")
                .then()
                .statusCode(400)
                .body("status", equalTo("error"))
                .body("message", containsString("Invalid geofence objectRef"));

        assertNotificationSeen(brokenGeofenceNotification.getId(), false);
    }

    UserNotificationEntity createNotification(UserEntity owner,
                                              NotificationSource source,
                                              NotificationType type,
                                              String title,
                                              Instant seenAt,
                                              Instant occurredAt) {
        return createNotification(
                owner,
                source,
                type,
                title,
                seenAt,
                occurredAt,
                "ref-" + System.nanoTime(),
                null
        );
    }

    UserNotificationEntity createNotification(UserEntity owner,
                                              NotificationSource source,
                                              NotificationType type,
                                              String title,
                                              Instant seenAt,
                                              Instant occurredAt,
                                              String objectRef,
                                              String dedupeKey) {
        try {
            if (userTransaction.getStatus() == Status.STATUS_ACTIVE) {
                userTransaction.rollback();
            }
            userTransaction.begin();

            UserEntity managedOwner = userRepository.findById(owner.getId());
            UserNotificationEntity entity = UserNotificationEntity.builder()
                    .ownerUser(managedOwner)
                    .source(source)
                    .type(type)
                    .title(title)
                    .message("Message for " + title)
                    .occurredAt(occurredAt)
                    .seenAt(seenAt)
                    .deliveryStatus(GeofenceDeliveryStatus.PENDING)
                    .objectRef(objectRef)
                    .metadata(Map.of("example", "value"))
                    .dedupeKey(dedupeKey)
                    .build();

            notificationRepository.persist(entity);
            notificationRepository.flush();
            userTransaction.commit();
            return entity;
        } catch (Exception e) {
            try {
                if (userTransaction.getStatus() == Status.STATUS_ACTIVE) {
                    userTransaction.rollback();
                }
            } catch (Exception ignored) {
                // no-op
            }
            throw new RuntimeException(e);
        }
    }

    private GeofenceRuleEntity createRule(UserEntity owner, String name) {
        try {
            if (userTransaction.getStatus() == Status.STATUS_ACTIVE) {
                userTransaction.rollback();
            }
            userTransaction.begin();

            UserEntity managedOwner = userRepository.findById(owner.getId());
            GeofenceRuleEntity rule = GeofenceRuleEntity.builder()
                    .ownerUser(managedOwner)
                    .name(name)
                    .northEastLat(50.0)
                    .northEastLon(30.0)
                    .southWestLat(49.0)
                    .southWestLon(29.0)
                    .monitorEnter(true)
                    .monitorLeave(true)
                    .cooldownSeconds(120)
                    .status(GeofenceRuleStatus.ACTIVE)
                    .build();

            ruleRepository.persist(rule);
            ruleRepository.flush();
            userTransaction.commit();
            return rule;
        } catch (Exception e) {
            rollbackQuietly();
            throw new RuntimeException(e);
        }
    }

    private GeofenceEventEntity createEvent(UserEntity owner,
                                            UserEntity subject,
                                            GeofenceRuleEntity rule,
                                            GeofenceEventType eventType,
                                            Instant occurredAt,
                                            Instant seenAt) {
        try {
            if (userTransaction.getStatus() == Status.STATUS_ACTIVE) {
                userTransaction.rollback();
            }
            userTransaction.begin();

            UserEntity managedOwner = userRepository.findById(owner.getId());
            UserEntity managedSubject = userRepository.findById(subject.getId());
            GeofenceRuleEntity managedRule = ruleRepository.findById(rule.getId());

            GeofenceEventEntity event = GeofenceEventEntity.builder()
                    .ownerUser(managedOwner)
                    .subjectUser(managedSubject)
                    .subjectDisplayName(
                            managedSubject.getFullName() != null && !managedSubject.getFullName().isBlank()
                                    ? managedSubject.getFullName()
                                    : managedSubject.getEmail()
                    )
                    .rule(managedRule)
                    .eventType(eventType)
                    .occurredAt(occurredAt)
                    .title("Event for " + managedRule.getName())
                    .message("Event message")
                    .deliveryStatus(GeofenceDeliveryStatus.PENDING)
                    .deliveryAttempts(0)
                    .seenAt(seenAt)
                    .createdAt(occurredAt)
                    .build();

            eventRepository.persist(event);
            eventRepository.flush();
            userTransaction.commit();
            return event;
        } catch (Exception e) {
            rollbackQuietly();
            throw new RuntimeException(e);
        }
    }

    private void assertGeofenceEventSeen(Long eventId, boolean expectedSeen) {
        try {
            if (userTransaction.getStatus() == Status.STATUS_ACTIVE) {
                userTransaction.rollback();
            }
            userTransaction.begin();
            GeofenceEventEntity event = eventRepository.findById(eventId);

            assertThat(event).isNotNull();
            assertThat(event.getSeenAt() != null).isEqualTo(expectedSeen);
            userTransaction.commit();
        } catch (Exception e) {
            rollbackQuietly();
            throw new RuntimeException(e);
        }
    }

    private void assertNotificationSeen(Long notificationId, boolean expectedSeen) {
        try {
            if (userTransaction.getStatus() == Status.STATUS_ACTIVE) {
                userTransaction.rollback();
            }
            userTransaction.begin();
            UserNotificationEntity notification = notificationRepository.findById(notificationId);

            assertThat(notification).isNotNull();
            assertThat(notification.getSeenAt() != null).isEqualTo(expectedSeen);
            userTransaction.commit();
        } catch (Exception e) {
            rollbackQuietly();
            throw new RuntimeException(e);
        }
    }

    private void rollbackQuietly() {
        try {
            if (userTransaction.getStatus() == Status.STATUS_ACTIVE) {
                userTransaction.rollback();
            }
        } catch (Exception ignored) {
            // no-op
        }
    }
}

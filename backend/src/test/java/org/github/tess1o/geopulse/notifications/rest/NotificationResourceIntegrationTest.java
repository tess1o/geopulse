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
    UserTransaction userTransaction;

    private UserEntity ownerUser;
    private UserEntity otherUser;
    private String ownerToken;

    @BeforeEach
    @Transactional
    void setUp() {
        notificationRepository.deleteAll();
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
        UserNotificationEntity ownerNotification = createNotification(ownerUser,
                NotificationSource.GEOFENCE,
                NotificationType.GEOFENCE_ENTER,
                "Owner unread",
                null,
                Instant.parse("2026-03-19T10:00:00Z"));
        UserNotificationEntity otherNotification = createNotification(otherUser,
                NotificationSource.GEOFENCE,
                NotificationType.GEOFENCE_ENTER,
                "Other unread",
                null,
                Instant.parse("2026-03-19T10:00:00Z"));

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

        given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .when()
                .post("/api/notifications/" + otherNotification.getId() + "/seen")
                .then()
                .statusCode(400)
                .body("status", equalTo("error"))
                .body("message", containsString("Notification not found"));
    }

    @Test
    void shouldMarkAllSeenGlobally() {
        createNotification(ownerUser,
                NotificationSource.GEOFENCE,
                NotificationType.GEOFENCE_ENTER,
                "Geofence unread",
                null,
                Instant.parse("2026-03-19T10:00:00Z"));
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
    }

    UserNotificationEntity createNotification(UserEntity owner,
                                              NotificationSource source,
                                              NotificationType type,
                                              String title,
                                              Instant seenAt,
                                              Instant occurredAt) {
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
                    .objectRef("ref-" + System.nanoTime())
                    .metadata(Map.of("example", "value"))
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
}

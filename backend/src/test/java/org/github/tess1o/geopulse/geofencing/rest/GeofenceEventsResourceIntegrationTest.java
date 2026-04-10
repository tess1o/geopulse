package org.github.tess1o.geopulse.geofencing.rest;
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
import org.github.tess1o.geopulse.geofencing.model.entity.NotificationTemplateEntity;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceEventRepository;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceRuleRepository;
import org.github.tess1o.geopulse.geofencing.repository.NotificationTemplateRepository;
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
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GeofenceEventsResourceIntegrationTest {
    @Inject
    UserService userService;
    @Inject
    AuthenticationService authenticationService;
    @Inject
    UserRepository userRepository;
    @Inject
    GeofenceRuleRepository ruleRepository;
    @Inject
    GeofenceEventRepository eventRepository;
    @Inject
    NotificationTemplateRepository templateRepository;
    @Inject
    UserNotificationRepository notificationRepository;
    @Inject
    UserTransaction userTransaction;
    private UserEntity ownerUser;
    private UserEntity otherUser;
    private UserEntity subjectA;
    private UserEntity subjectB;
    private String ownerToken;
    @BeforeEach
    @Transactional
    void setUp() {
        ownerUser = userService.registerUser(
                "geofence-events-owner-" + System.nanoTime() + "@example.com",
                "password123",
                "Owner User",
                "UTC"
        );
        otherUser = userService.registerUser(
                "geofence-events-other-" + System.nanoTime() + "@example.com",
                "password123",
                "Other User",
                "UTC"
        );
        subjectA = userService.registerUser(
                "geofence-events-subject-a-" + System.nanoTime() + "@example.com",
                "password123",
                "Alice Subject",
                "UTC"
        );
        subjectB = userService.registerUser(
                "geofence-events-subject-b-" + System.nanoTime() + "@example.com",
                "password123",
                "Bob Subject",
                "UTC"
        );
        AuthResponse auth = authenticationService.authenticate(ownerUser.getEmail(), "password123");
        ownerToken = auth.getAccessToken();
    }
    @AfterEach
    @Transactional
    void tearDown() {
    }
    @Test
    void shouldRequireAuthentication() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/geofences/events")
                .then()
                .statusCode(401);
    }
    @Test
    void shouldListEventsWithPaginationSortingAndFilters() {
        GeofenceRuleEntity home = createRule(ownerUser, "Home");
        GeofenceRuleEntity office = createRule(ownerUser, "Office");
        GeofenceRuleEntity otherRule = createRule(otherUser, "Other");
        createEvent(ownerUser, subjectA, home, null, GeofenceEventType.ENTER, "Older enter", Instant.parse("2026-03-20T08:00:00Z"), null);
        createEvent(ownerUser, subjectB, office, null, GeofenceEventType.LEAVE, "Middle leave", Instant.parse("2026-03-20T09:00:00Z"), null);
        createEvent(ownerUser, subjectA, home, null, GeofenceEventType.ENTER, "Newest seen", Instant.parse("2026-03-20T10:00:00Z"), Instant.parse("2026-03-20T10:30:00Z"));
        createEvent(otherUser, subjectA, otherRule, null, GeofenceEventType.ENTER, "Other user event", Instant.parse("2026-03-20T11:00:00Z"), null);
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/geofences/events?page=0&pageSize=2&sortBy=occurredAt&sortDir=desc")
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("data.items.size()", equalTo(2))
                .body("data.totalCount", equalTo(3))
                .body("data.page", equalTo(0))
                .body("data.pageSize", equalTo(2))
                .body("data.items[0].title", equalTo("Newest seen"));
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/geofences/events?sortBy=subjectDisplayName&sortDir=asc")
                .then()
                .statusCode(200)
                .body("data.items.size()", equalTo(3))
                .body("data.items[0].subjectDisplayName", equalTo("Alice Subject"));
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/geofences/events?sortBy=eventType&sortDir=asc")
                .then()
                .statusCode(200)
                .body("data.items.size()", equalTo(3))
                .body("data.items[0].eventType", equalTo("ENTER"));
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/geofences/events?subjectUserIds=" + subjectA.getId())
                .then()
                .statusCode(200)
                .body("data.items.size()", equalTo(2))
                .body("data.items.subjectUserId", everyItem(equalTo(subjectA.getId().toString())));
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/geofences/events?eventTypes=LEAVE")
                .then()
                .statusCode(200)
                .body("data.items.size()", equalTo(1))
                .body("data.items[0].eventType", equalTo("LEAVE"));
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/geofences/events?unreadOnly=true")
                .then()
                .statusCode(200)
                .body("data.items.size()", equalTo(2))
                .body("data.items.seen", everyItem(equalTo(false)));
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/geofences/events?dateFrom=2026-03-20T08:30:00Z&dateTo=2026-03-20T09:30:00Z")
                .then()
                .statusCode(200)
                .body("data.items.size()", equalTo(1))
                .body("data.items[0].title", equalTo("Middle leave"));
    }
    @Test
    void shouldRejectInvalidFilters() {
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/geofences/events?subjectUserIds=not-a-uuid")
                .then()
                .statusCode(400)
                .body("status", equalTo("error"))
                .body("message", containsString("Invalid UUID"));
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/geofences/events?eventTypes=INVALID")
                .then()
                .statusCode(400)
                .body("status", equalTo("error"))
                .body("message", containsString("Invalid eventTypes"));
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/geofences/events?dateFrom=bad-date")
                .then()
                .statusCode(400)
                .body("status", equalTo("error"))
                .body("message", containsString("Invalid dateFrom"));
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/geofences/events?dateFrom=2026-03-20T10:00:00Z&dateTo=2026-03-20T09:00:00Z")
                .then()
                .statusCode(400)
                .body("status", equalTo("error"))
                .body("message", containsString("dateFrom must be before or equal to dateTo"));
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/geofences/events?sortBy=deliveryStatus")
                .then()
                .statusCode(400)
                .body("status", equalTo("error"))
                .body("message", containsString("Unsupported sortBy value"));
    }
    @Test
    void shouldMarkSeenAndMarkAllSeenWithUnreadCount() {
        GeofenceRuleEntity rule = createRule(ownerUser, "Home");
        GeofenceEventEntity first = createEvent(ownerUser, subjectA, rule, null, GeofenceEventType.ENTER, "First", Instant.parse("2026-03-21T08:00:00Z"), null);
        GeofenceEventEntity second = createEvent(ownerUser, subjectB, rule, null, GeofenceEventType.LEAVE, "Second", Instant.parse("2026-03-21T09:00:00Z"), null);
        UserNotificationEntity firstNotification = createGeofenceNotification(ownerUser, first, NotificationType.GEOFENCE_ENTER, null);
        UserNotificationEntity secondNotification = createGeofenceNotification(ownerUser, second, NotificationType.GEOFENCE_LEAVE, null);
        UserNotificationEntity importNotification = createNotification(
                ownerUser,
                NotificationSource.IMPORT,
                NotificationType.IMPORT_COMPLETED,
                "Import unread",
                null,
                Instant.parse("2026-03-21T09:15:00Z"),
                "import-1",
                "import:1"
        );
        GeofenceEventEntity foreign = createEvent(otherUser, subjectA, createRule(otherUser, "Other"), null, GeofenceEventType.ENTER, "Other", Instant.parse("2026-03-21T10:00:00Z"), null);
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/geofences/events/unread-count")
                .then()
                .statusCode(200)
                .body("data.count", equalTo(2));
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .when()
                .post("/api/geofences/events/" + first.getId() + "/seen")
                .then()
                .statusCode(200)
                .body("data.id", equalTo(first.getId().intValue()))
                .body("data.seen", equalTo(true));
        assertNotificationSeen(firstNotification.getId(), true);
        assertNotificationSeen(secondNotification.getId(), false);
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .when()
                .post("/api/geofences/events/" + foreign.getId() + "/seen")
                .then()
                .statusCode(400)
                .body("status", equalTo("error"))
                .body("message", containsString("not found"));
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .when()
                .post("/api/geofences/events/seen-all")
                .then()
                .statusCode(200)
                .body("data.updatedCount", equalTo(1));
        assertNotificationSeen(secondNotification.getId(), true);
        assertNotificationSeen(importNotification.getId(), false);
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/geofences/events/unread-count")
                .then()
                .statusCode(200)
                .body("data.count", equalTo(0));
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/geofences/events?unreadOnly=true")
                .then()
                .statusCode(200)
                .body("data.items.size()", equalTo(0));
    }
    @Test
    void shouldKeepEventsAfterTemplateDeletion() {
        NotificationTemplateEntity template = createTemplate(ownerUser, "Push template");
        GeofenceRuleEntity rule = createRule(ownerUser, "Home", template);
        GeofenceEventEntity event = createEvent(ownerUser, subjectA, rule, template, GeofenceEventType.ENTER, "Template-backed event",
                Instant.parse("2026-03-21T08:00:00Z"), null);
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .when()
                .delete("/api/geofences/templates/" + template.getId())
                .then()
                .statusCode(200)
                .body("status", equalTo("success"));
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/geofences/events")
                .then()
                .statusCode(200)
                .body("data.items.size()", equalTo(1))
                .body("data.items[0].title", equalTo("Template-backed event"));
        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/geofences/events/unread-count")
                .then()
                .statusCode(200)
                .body("data.count", equalTo(1));
        assertTemplateReferencesDetached(event.getId(), rule.getId());
    }
    private GeofenceRuleEntity createRule(UserEntity owner, String name) {
        return createRule(owner, name, null);
    }
    private GeofenceRuleEntity createRule(UserEntity owner, String name, NotificationTemplateEntity template) {
        try {
            if (userTransaction.getStatus() == Status.STATUS_ACTIVE) {
                userTransaction.rollback();
            }
            userTransaction.begin();
            UserEntity managedOwner = userRepository.findById(owner.getId());
            NotificationTemplateEntity managedTemplate = template == null ? null : templateRepository.findById(template.getId());
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
                    .enterTemplate(managedTemplate)
                    .leaveTemplate(managedTemplate)
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
                                            NotificationTemplateEntity template,
                                            GeofenceEventType type,
                                            String title,
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
            NotificationTemplateEntity managedTemplate = template == null ? null : templateRepository.findById(template.getId());
            GeofenceEventEntity event = GeofenceEventEntity.builder()
                    .ownerUser(managedOwner)
                    .subjectUser(managedSubject)
                    .subjectDisplayName(
                            managedSubject.getFullName() != null && !managedSubject.getFullName().isBlank()
                                    ? managedSubject.getFullName()
                                    : managedSubject.getEmail()
                    )
                    .rule(managedRule)
                    .template(managedTemplate)
                    .eventType(type)
                    .occurredAt(occurredAt)
                    .title(title)
                    .message("Message for " + title)
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
    private UserNotificationEntity createGeofenceNotification(UserEntity owner,
                                                              GeofenceEventEntity event,
                                                              NotificationType type,
                                                              Instant seenAt) {
        return createNotification(
                owner,
                NotificationSource.GEOFENCE,
                type,
                "Notification for event " + event.getId(),
                seenAt,
                event.getOccurredAt(),
                String.valueOf(event.getId()),
                "geofence-event:" + event.getId()
        );
    }
    private UserNotificationEntity createNotification(UserEntity owner,
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
                    .dedupeKey(dedupeKey)
                    .build();
            notificationRepository.persist(entity);
            notificationRepository.flush();
            userTransaction.commit();
            return entity;
        } catch (Exception e) {
            rollbackQuietly();
            throw new RuntimeException(e);
        }
    }
    private NotificationTemplateEntity createTemplate(UserEntity owner, String name) {
        try {
            if (userTransaction.getStatus() == Status.STATUS_ACTIVE) {
                userTransaction.rollback();
            }
            userTransaction.begin();
            UserEntity managedOwner = userRepository.findById(owner.getId());
            NotificationTemplateEntity template = NotificationTemplateEntity.builder()
                    .user(managedOwner)
                    .name(name)
                    .destination("")
                    .titleTemplate("{{eventCode}}")
                    .bodyTemplate("{{subjectName}} {{eventVerb}} {{geofenceName}}")
                    .defaultForEnter(false)
                    .defaultForLeave(false)
                    .enabled(true)
                    .sendInApp(true)
                    .build();
            templateRepository.persist(template);
            templateRepository.flush();
            userTransaction.commit();
            return template;
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
    private void assertTemplateReferencesDetached(Long eventId, Long ruleId) {
        try {
            if (userTransaction.getStatus() == Status.STATUS_ACTIVE) {
                userTransaction.rollback();
            }
            userTransaction.begin();
            GeofenceEventEntity managedEvent = eventRepository.findById(eventId);
            GeofenceRuleEntity managedRule = ruleRepository.findById(ruleId);
            assertThat(managedEvent).isNotNull();
            assertThat(managedEvent.getTemplate()).isNull();
            assertThat(managedRule).isNotNull();
            assertThat(managedRule.getEnterTemplate()).isNull();
            assertThat(managedRule.getLeaveTemplate()).isNull();
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
}

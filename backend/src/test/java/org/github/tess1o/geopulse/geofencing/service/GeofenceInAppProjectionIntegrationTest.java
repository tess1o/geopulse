package org.github.tess1o.geopulse.geofencing.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geofencing.model.dto.CreateGeofenceRuleRequest;
import org.github.tess1o.geopulse.geofencing.model.dto.CreateNotificationTemplateRequest;
import org.github.tess1o.geopulse.geofencing.model.dto.GeofenceRuleDto;
import org.github.tess1o.geopulse.geofencing.model.dto.NotificationTemplateDto;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceDeliveryStatus;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventType;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceEventRepository;
import org.github.tess1o.geopulse.geofencing.repository.NotificationTemplateRepository;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.repository.UserNotificationRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GeofenceInAppProjectionIntegrationTest {

    @Inject
    UserService userService;

    @Inject
    NotificationTemplateService notificationTemplateService;

    @Inject
    GeofenceRuleService geofenceRuleService;

    @Inject
    GeofenceEvaluationService geofenceEvaluationService;

    @Inject
    NotificationTemplateRepository templateRepository;

    @Inject
    GeofenceEventRepository eventRepository;

    @Inject
    UserNotificationRepository notificationRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Test
    @Transactional
    void shouldNotCreateInAppNotificationWhenTemplateDisablesInApp() {
        UserEntity owner = createOwnerUser("geo-inapp-off");

        NotificationTemplateDto template = createTemplate(owner.getId(), "External-only", false, "discord://token");
        GeofenceRuleDto rule = createRule(owner.getId(), template.getId());

        evaluatePoint(owner, 48.9000, 29.5000, Instant.parse("2026-04-01T08:00:00Z"));
        evaluatePoint(owner, 49.5000, 29.5000, Instant.parse("2026-04-01T08:00:10Z"));

        GeofenceEventEntity enterEvent = findSingleEnterEvent(owner.getId(), rule.getId());
        assertThat(enterEvent.getDeliveryStatus()).isEqualTo(GeofenceDeliveryStatus.PENDING);
        assertThat(enterEvent.getTemplate()).isNotNull();
        assertThat(enterEvent.getTemplate().getSendInApp()).isFalse();

        long inAppCount = notificationRepository.count(
                "ownerUser.id = ?1 AND source = ?2",
                owner.getId(),
                NotificationSource.GEOFENCE
        );
        assertThat(inAppCount).isZero();
    }

    @Test
    @Transactional
    void shouldCreateInAppNotificationWhenTemplateEnablesInApp() {
        UserEntity owner = createOwnerUser("geo-inapp-on");

        NotificationTemplateDto template = createTemplate(owner.getId(), "In-app+external", true, "discord://token");
        GeofenceRuleDto rule = createRule(owner.getId(), template.getId());

        evaluatePoint(owner, 48.9000, 29.5000, Instant.parse("2026-04-01T09:00:00Z"));
        evaluatePoint(owner, 49.5000, 29.5000, Instant.parse("2026-04-01T09:00:10Z"));

        GeofenceEventEntity enterEvent = findSingleEnterEvent(owner.getId(), rule.getId());
        assertThat(enterEvent.getDeliveryStatus()).isEqualTo(GeofenceDeliveryStatus.PENDING);

        long inAppCount = notificationRepository.count(
                "ownerUser.id = ?1 AND source = ?2",
                owner.getId(),
                NotificationSource.GEOFENCE
        );
        assertThat(inAppCount).isEqualTo(1);
    }

    @Test
    @Transactional
    void shouldNotCreateInAppNotificationWhenNoTemplateResolved() {
        UserEntity owner = createOwnerUser("geo-fallback");

        disableAllTemplates(owner.getId());
        GeofenceRuleDto rule = createRule(owner.getId(), null);

        evaluatePoint(owner, 48.9000, 29.5000, Instant.parse("2026-04-01T10:00:00Z"));
        evaluatePoint(owner, 49.5000, 29.5000, Instant.parse("2026-04-01T10:00:10Z"));

        GeofenceEventEntity enterEvent = findSingleEnterEvent(owner.getId(), rule.getId());
        assertThat(enterEvent.getTemplate()).isNull();
        assertThat(enterEvent.getDeliveryStatus()).isEqualTo(GeofenceDeliveryStatus.SKIPPED);

        long inAppCount = notificationRepository.count(
                "ownerUser.id = ?1 AND source = ?2",
                owner.getId(),
                NotificationSource.GEOFENCE
        );
        assertThat(inAppCount).isZero();
    }

    private UserEntity createOwnerUser(String prefix) {
        return userService.registerUser(
                prefix + "-" + System.nanoTime() + "@example.com",
                "password123",
                "Geofence Owner",
                "UTC"
        );
    }

    private NotificationTemplateDto createTemplate(UUID userId,
                                                   String name,
                                                   boolean sendInApp,
                                                   String destination) {
        CreateNotificationTemplateRequest request = new CreateNotificationTemplateRequest();
        request.setName(name);
        request.setDestination(destination);
        request.setTitleTemplate("Geofence {{eventCode}}: {{geofenceName}}");
        request.setBodyTemplate("{{subjectName}} {{eventVerb}} {{geofenceName}} at {{timestamp}}");
        request.setDefaultForEnter(false);
        request.setDefaultForLeave(false);
        request.setEnabled(true);
        request.setSendInApp(sendInApp);
        return notificationTemplateService.createTemplate(userId, request);
    }

    private GeofenceRuleDto createRule(UUID ownerUserId, Long enterTemplateId) {
        CreateGeofenceRuleRequest request = new CreateGeofenceRuleRequest();
        request.setName("Test Rule " + System.nanoTime());
        request.setSubjectUserIds(List.of(ownerUserId));
        request.setNorthEastLat(50.0000);
        request.setNorthEastLon(30.0000);
        request.setSouthWestLat(49.0000);
        request.setSouthWestLon(29.0000);
        request.setMonitorEnter(true);
        request.setMonitorLeave(false);
        request.setCooldownSeconds(360);
        request.setEnterTemplateId(enterTemplateId);
        request.setLeaveTemplateId(null);
        return geofenceRuleService.createRule(ownerUserId, request);
    }

    private void evaluatePoint(UserEntity subject, double lat, double lon, Instant timestamp) {
        GpsPointEntity point = new GpsPointEntity();
        point.setUser(subject);
        point.setCoordinates(GeoUtils.createPoint(lon, lat));
        point.setTimestamp(timestamp);
        point.setSourceType(GpsSourceType.OWNTRACKS);
        point.setDeviceId("test-device");
        point.setCreatedAt(timestamp);

        gpsPointRepository.persist(point);
        gpsPointRepository.flush();

        geofenceEvaluationService.handlePersistedPoint(point);
    }

    private void disableAllTemplates(UUID userId) {
        templateRepository.findByUser(userId).forEach(template -> template.setEnabled(false));
    }

    private GeofenceEventEntity findSingleEnterEvent(UUID ownerUserId, Long ruleId) {
        long enterCount = eventRepository.count(
                "ownerUser.id = ?1 AND rule.id = ?2 AND eventType = ?3",
                ownerUserId,
                ruleId,
                GeofenceEventType.ENTER
        );
        assertThat(enterCount).isEqualTo(1);

        GeofenceEventEntity event = eventRepository.find(
                "ownerUser.id = ?1 AND rule.id = ?2 AND eventType = ?3",
                ownerUserId,
                ruleId,
                GeofenceEventType.ENTER
        ).firstResult();
        assertThat(event).isNotNull();
        return event;
    }
}

package org.github.tess1o.geopulse.geofencing.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventType;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleStatus;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleSubjectEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleSubjectId;
import org.github.tess1o.geopulse.geofencing.model.entity.NotificationTemplateEntity;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceRuleRepository;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A point that arrives late (a queued upload, a backfill) must not flip a geofence's inside/outside state or emit an
 * enter or leave event for a moment that is already past.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GeofenceLatePointIntegrationTest {

    private static final Instant NOON = Instant.parse("2026-09-11T12:00:00Z");

    @Inject
    GpsPointService gpsPointService;

    @Inject
    GeofenceRuleRepository ruleRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsSourceRepository gpsSourceRepository;

    @Inject
    EntityManager entityManager;

    private UUID userId;
    private Long ruleId;
    private GpsSourceConfigEntity sourceConfig;

    @BeforeEach
    @Transactional
    void setUp() {
        UserEntity user = UserEntity.builder()
                .email(TestIds.uniqueEmail("geofence-late-point"))
                .passwordHash("pass")
                .role(Role.USER)
                .timezone("UTC")
                .isActive(true)
                .build();
        userRepository.persist(user);
        userId = user.getId();

        sourceConfig = GpsSourceConfigEntity.builder()
                .user(user)
                .sourceType(GpsSourceType.OWNTRACKS)
                .username(TestIds.uniqueValue("geofence-late-source"))
                .active(true)
                .filterInaccurateData(false)
                .build();
        gpsSourceRepository.persist(sourceConfig);

        NotificationTemplateEntity template = NotificationTemplateEntity.builder()
                .user(user)
                .name("Late point template")
                .destination("")
                .titleTemplate("Geofence")
                .bodyTemplate("Geofence event")
                .enabled(true)
                .defaultForEnter(false)
                .defaultForLeave(false)
                .build();
        entityManager.persist(template);

        GeofenceRuleEntity rule = GeofenceRuleEntity.builder()
                .ownerUser(user)
                .name("Late point rule")
                .northEastLat(11.0)
                .northEastLon(11.0)
                .southWestLat(10.0)
                .southWestLon(10.0)
                .monitorEnter(true)
                .monitorLeave(true)
                .cooldownSeconds(0)
                .enterTemplate(template)
                .leaveTemplate(template)
                .status(GeofenceRuleStatus.ACTIVE)
                .build();
        ruleRepository.persist(rule);
        ruleRepository.flush();

        entityManager.persist(GeofenceRuleSubjectEntity.builder()
                .id(new GeofenceRuleSubjectId(rule.getId(), userId))
                .rule(rule)
                .subjectUser(user)
                .build());
        entityManager.flush();
        ruleId = rule.getId();
    }

    @Test
    void latePointDoesNotFlipTheStateOrEmitAnEvent() {
        save(NOON, 10.5, 10.5);
        assertThat(events()).containsExactly(GeofenceEventType.ENTER);

        // An hour-old point from outside the area arrives after the user is already inside.
        save(NOON.minusSeconds(3600), 9.5, 9.5);

        assertThat(events()).containsExactly(GeofenceEventType.ENTER);
        assertThat(currentlyInside()).isTrue();

        // A newer point outside is a real departure.
        save(NOON.plusSeconds(600), 9.5, 9.5);
        assertThat(events()).containsExactly(GeofenceEventType.ENTER, GeofenceEventType.LEAVE);
        assertThat(currentlyInside()).isFalse();
    }

    private void save(Instant timestamp, double lat, double lon) {
        OwnTracksLocationMessage message = OwnTracksLocationMessage.builder()
                .type("location")
                .lat(lat)
                .lon(lon)
                .acc(10.0)
                .vel(0.0)
                .tst(timestamp.getEpochSecond())
                .build();
        gpsPointService.saveOwnTracksGpsPoint(message, userId, "phone", GpsSourceType.OWNTRACKS, sourceConfig);
    }

    private List<GeofenceEventType> events() {
        return QuarkusTransaction.requiringNew().call(() -> entityManager.createQuery(
                        "select e.eventType from GeofenceEventEntity e where e.subjectUser.id = :userId "
                                + "order by e.occurredAt", GeofenceEventType.class)
                .setParameter("userId", userId)
                .getResultList());
    }

    private boolean currentlyInside() {
        return QuarkusTransaction.requiringNew().call(() -> entityManager.createQuery(
                        "select s.currentInside from GeofenceRuleStateEntity s "
                                + "where s.rule.id = :ruleId and s.subjectUser.id = :userId", Boolean.class)
                .setParameter("ruleId", ruleId)
                .setParameter("userId", userId)
                .getSingleResult());
    }
}

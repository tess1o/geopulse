package org.github.tess1o.geopulse.geofencing.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventType;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleStatus;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleSubjectEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleSubjectId;
import org.github.tess1o.geopulse.geofencing.model.entity.NotificationTemplateEntity;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceEventRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GeofenceCooldownConcurrencyIntegrationTest {

    private static final int WORKER_COUNT = 5;
    private static final String DEVICE_PREFIX = "race-device-";

    @Inject
    GpsPointService gpsPointService;

    @Inject
    GeofenceRuleRepository ruleRepository;

    @Inject
    GeofenceEventRepository eventRepository;

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
                .email(TestIds.uniqueEmail("geofence-cooldown-race"))
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
                .username(TestIds.uniqueValue("cooldown-race-source"))
                .active(true)
                .filterInaccurateData(false)
                .maxAllowedAccuracy(100)
                .maxAllowedSpeed(300)
                .build();
        gpsSourceRepository.persist(sourceConfig);

        NotificationTemplateEntity template = NotificationTemplateEntity.builder()
                .user(user)
                .name("Race In-App Enter")
                .destination("")
                .titleTemplate("Race enter")
                .bodyTemplate("Race enter body")
                .enabled(true)
                .defaultForEnter(false)
                .defaultForLeave(false)
                .build();
        entityManager.persist(template);

        GeofenceRuleEntity rule = GeofenceRuleEntity.builder()
                .ownerUser(user)
                .name("Race Rule")
                .northEastLat(11.0)
                .northEastLon(11.0)
                .southWestLat(10.0)
                .southWestLon(10.0)
                .monitorEnter(true)
                .monitorLeave(false)
                .cooldownSeconds(360)
                .enterTemplate(template)
                .leaveTemplate(null)
                .status(GeofenceRuleStatus.ACTIVE)
                .build();
        ruleRepository.persist(rule);
        ruleRepository.flush();

        GeofenceRuleSubjectEntity assignment = GeofenceRuleSubjectEntity.builder()
                .id(new GeofenceRuleSubjectId(rule.getId(), userId))
                .rule(rule)
                .subjectUser(user)
                .build();
        entityManager.persist(assignment);
        entityManager.flush();

        ruleId = rule.getId();
    }

    @Test
    void shouldEmitSingleEnterWithinCooldownUnderConcurrentInsidePoints() throws Exception {
        Instant burstBase = Instant.parse("2026-04-09T08:31:20Z");

        // Prime rule state with an outside point.
        saveOwnTracksPoint(burstBase, 9.5, 9.5, DEVICE_PREFIX + "outside");
        assertEquals(0, findEnterEvents().size(), "Outside bootstrap point should not generate ENTER events");

        ExecutorService executor = Executors.newFixedThreadPool(WORKER_COUNT);
        CountDownLatch workersReady = new CountDownLatch(WORKER_COUNT);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < WORKER_COUNT; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    workersReady.countDown();
                    boolean started = startGate.await(5, TimeUnit.SECONDS);
                    if (!started) {
                        throw new IllegalStateException("Workers were not started in time");
                    }
                    saveOwnTracksPoint(
                            burstBase.plusSeconds(idx + 1L),
                            10.5,
                            10.5,
                            DEVICE_PREFIX + idx
                    );
                    return null;
                }));
            }

            assertTrue(workersReady.await(5, TimeUnit.SECONDS), "Workers did not become ready");
            startGate.countDown();

            for (Future<?> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        List<GeofenceEventEntity> enterEvents = findEnterEvents();
        assertTrue(!enterEvents.isEmpty(), "Expected at least one ENTER event");

        Instant minOccurredAt = enterEvents.stream().map(GeofenceEventEntity::getOccurredAt).min(Instant::compareTo).orElseThrow();
        Instant maxOccurredAt = enterEvents.stream().map(GeofenceEventEntity::getOccurredAt).max(Instant::compareTo).orElseThrow();
        assertTrue(Duration.between(minOccurredAt, maxOccurredAt).abs().getSeconds() <= 10,
                "Concurrent burst ENTER events should remain within a 10-second window");

        // Expected behavior: only one ENTER within 360s cooldown window.
        assertEquals(1, enterEvents.size(),
                "Expected exactly one ENTER event within cooldown=360s for a 10-second burst");
    }

    @Test
    void shouldEmitSingleEnterWithinCooldownForSequentialPoints() {
        Instant burstBase = Instant.parse("2026-04-09T08:41:00Z");

        saveOwnTracksPoint(burstBase, 9.5, 9.5, DEVICE_PREFIX + "outside-seq");
        assertEquals(0, findEnterEvents().size(), "Outside bootstrap point should not generate ENTER events");

        for (int i = 0; i < WORKER_COUNT; i++) {
            saveOwnTracksPoint(
                    burstBase.plusSeconds(i + 1L),
                    10.5,
                    10.5,
                    DEVICE_PREFIX + "seq-" + i
            );
        }

        List<GeofenceEventEntity> enterEvents = findEnterEvents();
        assertEquals(1, enterEvents.size(), "Sequential 10-second burst should respect cooldown=360s");
    }

    private List<GeofenceEventEntity> findEnterEvents() {
        return eventRepository.find(
                        "rule.id = ?1 AND subjectUser.id = ?2 AND eventType = ?3 ORDER BY occurredAt ASC",
                        ruleId,
                        userId,
                        GeofenceEventType.ENTER
                )
                .list();
    }

    private void saveOwnTracksPoint(Instant timestamp, double lat, double lon, String deviceId) {
        OwnTracksLocationMessage message = OwnTracksLocationMessage.builder()
                .type("location")
                .lat(lat)
                .lon(lon)
                .acc(10.0)
                .vel(0.0)
                .tst(timestamp.getEpochSecond())
                .build();

        gpsPointService.saveOwnTracksGpsPoint(
                message,
                userId,
                deviceId,
                GpsSourceType.OWNTRACKS,
                sourceConfig
        );
    }
}

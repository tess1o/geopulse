package org.github.tess1o.geopulse.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.model.GpsPointFilterDTO;
import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.gps.model.GpsPointSummaryDTO;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.model.GpsTelemetryMappingEntry;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceTypeTelemetryConfigRepository;
import org.github.tess1o.geopulse.gpssource.service.GpsSourceTypeTelemetryConfigService;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class, restrictToAnnotatedClass = true)
@TestProfile(GpsPointServiceTest.DisableLocationTimeThresholdTestProfile.class)
@SerializedDatabaseTest
public class GpsPointServiceTest {
    private UUID userId;
    private GpsSourceConfigEntity testConfig;
    @Inject
    GpsPointService gpsPointService;
    @Inject
    GpsPointRepository gpsPointRepository;
    @Inject
    UserRepository userRepository;
    @Inject
    GpsSourceRepository gpsSourceRepository;
    @Inject
    EntityManager em;
    @Inject
    GpsSourceTypeTelemetryConfigService telemetryConfigService;
    @Inject
    GpsSourceTypeTelemetryConfigRepository telemetryConfigRepository;
    @BeforeEach
    @Transactional
    public void setup() {
        // Clean up GPS points first (due to foreign key constraints)
        gpsPointRepository.deleteAll();
        // Clean up GPS source configs
        gpsSourceRepository.deleteAll();
        telemetryConfigRepository.deleteAll();
        // Clean up users
        userRepository.deleteAll();
        // Create fresh test user
        UserEntity user = UserEntity.builder()
                .email("test@test.com" + System.nanoTime()) // Make email unique
                .role(Role.USER)
                .passwordHash("pass")
                .build();
        userRepository.persist(user);
        userId = user.getId();
        // Create test GPS source config with filtering disabled for existing tests
        testConfig = GpsSourceConfigEntity.builder()
                .user(user)
                .sourceType(GpsSourceType.OWNTRACKS)
                .username("test-user")
                .active(true)
                .filterInaccurateData(false) // Filtering disabled for backward compatibility
                .maxAllowedAccuracy(100)
                .maxAllowedSpeed(250)
                .build();
        gpsSourceRepository.persist(testConfig);
    }
    @Test
    @Transactional
    public void testSaveOwnTracksGpsPoint() {
        long tst = (int) Instant.now().plusSeconds(20000).toEpochMilli() / 1000;
        OwnTracksLocationMessage message = OwnTracksLocationMessage.builder()
                .type("location")
                .acc(0.2)
                .lat(40.0)
                .lon(-74.0)
                .tst(tst)
                .vel(5.0)
                .build();
        Statistics stats = getStatistics();
        stats.clear();  // Reset stats before operation
        gpsPointService.saveOwnTracksGpsPoint(message, userId, "test-device", GpsSourceType.OWNTRACKS, testConfig);
        var insertCount = stats.getEntityInsertCount();
        assertEquals(1, insertCount); // Expect 1 query
        var selectCount = stats.getQueryExecutionCount();
        assertTrue(selectCount <= 2); //for duplication check and possbile timeline_regeneration_queue
        assertEquals(1, gpsPointRepository.count());
        GpsPointEntity savedGpsPoint = gpsPointRepository.findAll().firstResult();
        assertEquals(userId, savedGpsPoint.getUser().getId());
        assertFalse(Hibernate.isInitialized(savedGpsPoint.getUser()));
        assertNull(savedGpsPoint.getAltitude());
        assertNull(savedGpsPoint.getBattery());
        assertEquals(0.2, savedGpsPoint.getAccuracy(), 0.000001);
        assertEquals(40.0, savedGpsPoint.getCoordinates().getY(), 0.000001);
        assertEquals(-74.0, savedGpsPoint.getCoordinates().getX(), 0.000001);
        assertEquals(5.0, savedGpsPoint.getVelocity(), 0.000001);
        assertEquals("test-device", savedGpsPoint.getDeviceId());
        assertEquals(tst, (int) savedGpsPoint.getTimestamp().getEpochSecond());
    }
    @Test
    @Transactional
    public void testSaveOwnTracksGpsPointDuplicate() {
        long tst = (int) Instant.now().plusSeconds(20000).toEpochMilli() / 1000;
        OwnTracksLocationMessage message = OwnTracksLocationMessage.builder()
                .type("location")
                .acc(0.2)
                .lat(40.0)
                .lon(-74.0)
                .tst(tst)
                .vel(5.0)
                .build();
        Statistics stats = getStatistics();
        stats.clear();  // Reset stats before operation
        gpsPointService.saveOwnTracksGpsPoint(message, userId, "test-device", GpsSourceType.OWNTRACKS, testConfig);
        var insertCount = stats.getEntityInsertCount();
        assertEquals(1, insertCount); // Expect 1 query
        var selectCount = stats.getQueryExecutionCount();
        assertTrue(selectCount <= 2); //for duplication check and possbile
        assertEquals(1, gpsPointRepository.count());
        gpsPointService.saveOwnTracksGpsPoint(message, userId, "test-device", GpsSourceType.OWNTRACKS, testConfig);
        assertEquals(1, gpsPointRepository.count());
    }
    @Test
    @Transactional
    public void testGetGpsPointSummary_BasicFunctionality() {
        Instant utcTodayNoon = Instant.now()
                .atZone(ZoneId.of("UTC"))
                .toLocalDate()
                .atTime(12, 0)
                .toInstant(ZoneOffset.UTC);
        Instant sevenDaysAgo = utcTodayNoon.minus(7, ChronoUnit.DAYS);
        Instant oneDayAgo = utcTodayNoon.minus(1, ChronoUnit.DAYS);

        // Create test GPS points with deterministic timestamps
        createTestGpsPoint(sevenDaysAgo);
        createTestGpsPoint(oneDayAgo);
        createTestGpsPoint(utcTodayNoon); // Should count as "today" in UTC

        GpsPointSummaryDTO summary = gpsPointService.getGpsPointSummary(userId);
        assertEquals(3, summary.getTotalPoints());
        assertEquals(1, summary.getPointsToday());
        assertEquals(sevenDaysAgo, summary.getFirstPointDate());
        assertEquals(utcTodayNoon, summary.getLastPointDate());
    }
    @Test
    @Transactional
    public void testGetGpsPointSummary_TimezoneIssue_GMT_Plus3_Early_Morning() {
        // Test timezone fix: Create points that are clearly from different days in user timezone
        ZoneId gmtPlus3 = ZoneId.of("Europe/Kyiv"); // GMT+3
        // Use "today" in GMT+3 timezone to avoid date skew under timezone matrix runs
        LocalDate today = LocalDate.now(gmtPlus3);
        LocalDate yesterday = today.minusDays(1);
        // Create test points:
        // 1. Point from "yesterday" in GMT+3 (should NOT count as today)
        ZonedDateTime yesterdayPoint = yesterday.atTime(20, 0).atZone(gmtPlus3);
        createTestGpsPoint(yesterdayPoint.toInstant());
        // 2. Point from "today" in GMT+3 (should count as today)
        ZonedDateTime todayPoint = today.atTime(1, 0).atZone(gmtPlus3);
        createTestGpsPoint(todayPoint.toInstant());
        // Test the FIXED implementation with correct timezone
        GpsPointSummaryDTO summaryFixed = gpsPointService.getGpsPointSummary(userId, gmtPlus3);
        assertEquals(2, summaryFixed.getTotalPoints());
        assertEquals(1, summaryFixed.getPointsToday(),
                "Expected 1 point for 'today' from user's GMT+3 perspective with the timezone fix. " +
                        "Got " + summaryFixed.getPointsToday() + " points.");
        // Also test that UTC-based calculation might give different result
        GpsPointSummaryDTO summaryUtc = gpsPointService.getGpsPointSummary(userId);
        // UTC might count differently due to timezone offset
        assertTrue(summaryUtc.getPointsToday() >= 0 && summaryUtc.getPointsToday() <= 2,
                "UTC-based count should be between 0-2, got: " + summaryUtc.getPointsToday());
    }
    @Test
    @Transactional
    public void testGetGpsPointSummary_TimezoneIssue_GMT_Minus8_Late_Evening() {
        // Test timezone fix: Create points that are clearly from different days in user timezone
        ZoneId gmtMinus8 = ZoneId.of("America/Los_Angeles"); // GMT-8 (Pacific Time)
        // Use "today" in Pacific Time zone (not server timezone) to ensure test works regardless of server location
        LocalDate todayPacific = LocalDate.now(gmtMinus8);
        LocalDate tomorrowPacific = todayPacific.plusDays(1);
        // Create test points:
        // 1. Point from "today" in GMT-8 (should count as today from Pacific perspective)
        ZonedDateTime todayPoint = todayPacific.atTime(10, 0).atZone(gmtMinus8);
        createTestGpsPoint(todayPoint.toInstant());
        // 2. Point from "tomorrow" in GMT-8 (should NOT count as today from Pacific perspective)
        ZonedDateTime tomorrowPoint = tomorrowPacific.atTime(1, 0).atZone(gmtMinus8);
        createTestGpsPoint(tomorrowPoint.toInstant());
        // Test the FIXED implementation with correct timezone
        GpsPointSummaryDTO summaryFixed = gpsPointService.getGpsPointSummary(userId, gmtMinus8);
        assertEquals(2, summaryFixed.getTotalPoints());
        assertEquals(1, summaryFixed.getPointsToday(),
                "Expected 1 point for 'today' from user's GMT-8 perspective with the timezone fix. " +
                        "Got " + summaryFixed.getPointsToday() + " points.");
        // Also test that UTC-based calculation might give different result
        GpsPointSummaryDTO summaryUtc = gpsPointService.getGpsPointSummary(userId);
        // UTC might count differently due to timezone offset
        assertTrue(summaryUtc.getPointsToday() >= 0 && summaryUtc.getPointsToday() <= 2,
                "UTC-based count should be between 0-2, got: " + summaryUtc.getPointsToday());
    }
    @Test
    @Transactional
    public void testGetGpsPointSummary_EmptyResult() {
        // Test with no GPS points
        GpsPointSummaryDTO summary = gpsPointService.getGpsPointSummary(userId);
        assertEquals(0, summary.getTotalPoints());
        assertEquals(0, summary.getPointsToday());
        assertNull(summary.getFirstPointDate());
        assertNull(summary.getLastPointDate());
    }

    @Test
    @Transactional
    public void testSaveOwnTracksGpsPoint_TelemetryPersistedWhenPresent() {
        long tst = Instant.now().plusSeconds(20000).getEpochSecond();

        OwnTracksLocationMessage message = OwnTracksLocationMessage.builder()
                .type("location")
                .lat(40.0)
                .lon(-74.0)
                .tst(tst)
                .ext(Map.of(
                        "ignition", 1,
                        "batt_v", 12.6
                ))
                .build();

        gpsPointService.saveOwnTracksGpsPoint(message, userId, "test-device", GpsSourceType.OWNTRACKS, testConfig);
        GpsPointEntity savedPoint = gpsPointRepository.findAll().firstResult();
        assertNotNull(savedPoint.getTelemetry());
        assertEquals(1, savedPoint.getTelemetry().get("ignition"));
        assertEquals(12.6, ((Number) savedPoint.getTelemetry().get("batt_v")).doubleValue(), 0.000001);
    }

    @Test
    @Transactional
    public void testTelemetryVisibilityRulesAppliedToGpsDataAndCurrentPopup() {
        long tst = Instant.now().plusSeconds(20000).getEpochSecond();

        List<GpsTelemetryMappingEntry> mapping = List.of(
                GpsTelemetryMappingEntry.builder()
                        .key("ignition")
                        .label("Ignition")
                        .type("boolean")
                        .enabled(true)
                        .order(10)
                        .showInGpsData(true)
                        .showInCurrentPopup(false)
                        .build(),
                GpsTelemetryMappingEntry.builder()
                        .key("batt_v")
                        .label("Battery")
                        .type("number")
                        .unit("V")
                        .enabled(true)
                        .order(20)
                        .showInGpsData(false)
                        .showInCurrentPopup(true)
                        .build(),
                GpsTelemetryMappingEntry.builder()
                        .key("geofence_lat")
                        .label("Geofence Lat")
                        .type("number")
                        .enabled(false)
                        .order(30)
                        .showInGpsData(true)
                        .showInCurrentPopup(true)
                        .build()
        );

        telemetryConfigService.upsertConfig(userId, GpsSourceType.OWNTRACKS, mapping);

        OwnTracksLocationMessage message = OwnTracksLocationMessage.builder()
                .type("location")
                .lat(40.0)
                .lon(-74.0)
                .tst(tst)
                .ext(Map.of(
                        "ignition", 1,
                        "batt_v", 12.55,
                        "geofence_lat", 49.1
                ))
                .build();

        gpsPointService.saveOwnTracksGpsPoint(message, userId, "test-device", GpsSourceType.OWNTRACKS, testConfig);

        var page = gpsPointService.getGpsPointsPageWithFilters(
                userId,
                GpsPointFilterDTO.builder().build(),
                1,
                10,
                "timestamp",
                "desc"
        );
        assertEquals(1, page.getData().size());
        assertNotNull(page.getData().get(0).getTelemetryGpsData());
        assertEquals(1, page.getData().get(0).getTelemetryGpsData().size());
        assertEquals("ignition", page.getData().get(0).getTelemetryGpsData().get(0).getKey());

        GpsPointPathDTO path = gpsPointService.getGpsPointPath(userId, Instant.EPOCH, Instant.now().plusSeconds(30000));
        assertEquals(1, path.getPoints().size());
        GpsPointPathPointDTO pathPoint = (GpsPointPathPointDTO) path.getPoints().get(0);
        assertNotNull(pathPoint.getTelemetryCurrentPopup());
        assertEquals(1, pathPoint.getTelemetryCurrentPopup().size());
        assertEquals("batt_v", pathPoint.getTelemetryCurrentPopup().get(0).getKey());
        assertTrue(pathPoint.getTelemetryCurrentPopup().stream().noneMatch(item -> "geofence_lat".equals(item.getKey())));
    }
    private void createTestGpsPoint(Instant timestamp) {
        OwnTracksLocationMessage message = OwnTracksLocationMessage.builder()
                .type("location")
                .acc(5.0)
                .lat(40.0)
                .lon(-74.0)
                .tst(timestamp.getEpochSecond())
                .vel(2.0)
                .build();
        gpsPointService.saveOwnTracksGpsPoint(message, userId, "test-device", GpsSourceType.OWNTRACKS, testConfig);
    }
    private Statistics getStatistics() {
        Session session = em.unwrap(Session.class);
        SessionFactory sessionFactory = session.getSessionFactory();
        return sessionFactory.getStatistics();
    }
    public static class DisableLocationTimeThresholdTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("geopulse.gps.duplicate-detection.location-time-threshold-minutes", "-1");
        }
    }
}

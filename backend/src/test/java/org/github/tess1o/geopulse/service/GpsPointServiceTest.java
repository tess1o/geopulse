package org.github.tess1o.geopulse.service;

import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
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
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.github.tess1o.geopulse.gps.model.GpsPointSummaryDTO;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@TestProfile(GpsPointServiceTest.DisableLocationTimeThresholdTestProfile.class)
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

    @BeforeEach
    @Transactional
    public void setup() {
        // Clean up GPS points first (due to foreign key constraints)
        gpsPointRepository.deleteAll();

        // Clean up GPS source configs
        gpsSourceRepository.deleteAll();

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
        // Create test GPS points with known timestamps
        createTestGpsPoint(Instant.now().minus(7, ChronoUnit.DAYS)); // 7 days ago
        createTestGpsPoint(Instant.now().minus(1, ChronoUnit.DAYS)); // 1 day ago
        createTestGpsPoint(Instant.now().minus(1, ChronoUnit.HOURS)); // 1 hour ago (should count as today in UTC)

        GpsPointSummaryDTO summary = gpsPointService.getGpsPointSummary(userId);

        assertEquals(3, summary.getTotalPoints());
        assertEquals(1, summary.getPointsToday()); // Only the 1-hour-ago point should count as today
        assertNotNull(summary.getFirstPointDate());
        assertNotNull(summary.getLastPointDate());
    }

    @Test
    @Transactional
    public void testGetGpsPointSummary_TimezoneIssue_GMT_Plus3_Early_Morning() {
        // Test timezone fix: Create points that are clearly from different days in user timezone
        ZoneId gmtPlus3 = ZoneId.of("Europe/Kyiv"); // GMT+3

        // Use today's date but convert to specific times
        LocalDate today = LocalDate.now();
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
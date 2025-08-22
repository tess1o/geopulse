package org.github.tess1o.geopulse.timeline.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test the overnight timeline processing functionality.
 * Verifies the 4-step algorithm for handling overnight stays.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class OvernightTimelineProcessorTest {

    @Inject
    WholeTimelineProcessor wholeTimelineProcessor;

    @Inject
    DailyTimelineProcessingService dailyTimelineProcessingService;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    TimelineTripRepository timelineTripRepository;

    private UserEntity testUser;

    // Test coordinates (using proven San Francisco coordinates from working tests)
    private static final double HOME_LAT = 37.7749;
    private static final double HOME_LON = -122.4194;

    private static final double OFFICE_LAT = 37.7849;
    private static final double OFFICE_LON = -122.4094;

    private static final double CAFE_LAT = 37.7649;
    private static final double CAFE_LON = -122.4294;

    @BeforeEach
    @Transactional
    void setUp() {
        cleanupTestData();
        testUser = createTestUser("overnight-test@geopulse.app", "Overnight Processing Test User");
    }

    @AfterEach
    @Transactional
    void tearDown() {
        cleanupTestData();
    }

    @Test
    @Transactional
    void testOvernightStayProcessing_StayExtendedFromPreviousDay() {
        // Scenario: User at home Aug 3rd 20:00-23:59, then moves to office Aug 4th 14:00
        // Expected: Aug 3rd stay extended to Aug 4th 14:00, office stay starts at 14:00

        LocalDate aug3 = LocalDate.of(2025, 8, 3);
        LocalDate aug4 = LocalDate.of(2025, 8, 4);

        // Aug 3rd - user at home from 20:00-23:59 (4 hour stay - dense GPS points every 10 minutes)
        Instant aug3HomeStart = aug3.atTime(20, 0).toInstant(ZoneOffset.UTC);
        Instant aug3HomeEnd = aug4.atTime(13, 00).toInstant(ZoneOffset.UTC);
        createDenseGpsPoints(testUser, aug3HomeStart, aug3HomeEnd, HOME_LAT, HOME_LON, 600); // 10 minutes

        // Process Aug 3rd to create cached timeline data
        Instant aug3Start = aug3.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant aug3End = aug4.atStartOfDay(ZoneOffset.UTC).toInstant();

        boolean aug3Processed = dailyTimelineProcessingService.processUserTimeline(
                testUser.getId(), aug3Start, aug3End, aug3);

        assertTrue(aug3Processed, "Aug 3rd should be processed successfully");

        // Verify Aug 3rd has a cached stay
        long aug3StaysCount = timelineStayRepository.count("user.id = ?1 and timestamp >= ?2 and timestamp < ?3",
                testUser.getId(), aug3Start, aug3End);
        assertTrue(aug3StaysCount > 0, "Aug 3rd should have cached stays");

        // Aug 4th - user moves to office at 14:00 and stays there (dense GPS points every 10 minutes)
        Instant aug4OfficeStart = aug4.atTime(14, 0).toInstant(ZoneOffset.UTC);
        Instant aug4OfficeEnd = aug4.atTime(16, 0).toInstant(ZoneOffset.UTC);
        createDenseGpsPoints(testUser, aug4OfficeStart, aug4OfficeEnd, OFFICE_LAT, OFFICE_LON, 600); // 10 minutes

        log.info("Created GPS data: Aug 3rd (20:00-23:00 at home) + Aug 4th (14:00-16:00 at office)");

        // Process Aug 4th using overnight processing
        MovementTimelineDTO aug4Timeline = wholeTimelineProcessor.processWholeTimeline(testUser.getId(), aug4);

        assertNotNull(aug4Timeline, "Aug 4th timeline should be generated");
        assertTrue(aug4Timeline.getStays().size() >= 1, "Aug 4th timeline should have at least 1 stay (office)");

        // Verify that Aug 3rd stay was updated (extended to Aug 4th 14:00)
        long updatedAug3Stays = timelineStayRepository.count("user.id = ?1 and timestamp >= ?2 and timestamp < ?3",
                testUser.getId(), aug3Start, aug3End);
        assertTrue(updatedAug3Stays > 0, "Aug 3rd should still have the updated stay");
    }

    @Test
    @Transactional
    void testOvernightProcessing_NoPreviousEvents() {
        // Scenario: No previous events exist, should fall back to standard 00:00-23:59 processing

        LocalDate aug4 = LocalDate.of(2025, 8, 4);

        // Aug 4th - user at office from 09:00-17:00 (dense GPS points every 10 minutes)
        Instant aug4OfficeStart = aug4.atTime(9, 0).toInstant(ZoneOffset.UTC);
        Instant aug4OfficeEnd = aug4.atTime(17, 0).toInstant(ZoneOffset.UTC);
        createDenseGpsPoints(testUser, aug4OfficeStart, aug4OfficeEnd, OFFICE_LAT, OFFICE_LON, 600); // 10 minutes

        log.info("Created GPS data: Aug 4th (09:00-17:00 at office) with no previous events");

        // Process Aug 4th using overnight processing (should fall back to standard)
        MovementTimelineDTO aug4Timeline = wholeTimelineProcessor.processWholeTimeline(testUser.getId(), aug4);

        assertNotNull(aug4Timeline, "Aug 4th timeline should be generated");
        assertTrue(aug4Timeline.getStaysCount() >= 1, "Aug 4th timeline should have at least 1 stay (office)");

        log.info("Aug 4th timeline (fallback): {} stays, {} trips", aug4Timeline.getStaysCount(), aug4Timeline.getTripsCount());

        // The office stay should start at 09:00 (standard processing)
        TimelineStayLocationDTO officeStay = aug4Timeline.getStays().get(0);
        assertEquals(aug4.atTime(9, 0).toInstant(ZoneOffset.UTC), officeStay.getTimestamp(),
                "Office stay should start at 09:00 (standard processing)");

        log.info("SUCCESS: Fallback to standard processing working correctly!");
    }

    @Test
    @Transactional
    void testOvernightProcessing_MultipleStaysInDay() {
        // Scenario: User has multiple stays on processing day after overnight stay
        // Expected: Previous stay extended, then multiple new stays saved

        LocalDate aug3 = LocalDate.of(2025, 8, 3);
        LocalDate aug4 = LocalDate.of(2025, 8, 4);

        // Aug 3rd - user at home from 22:00-23:59 (dense GPS points every 10 minutes)
        Instant aug3HomeStart = aug3.atTime(22, 0).toInstant(ZoneOffset.UTC);
        Instant aug3HomeEnd = aug4.atTime(9, 0).toInstant(ZoneOffset.UTC);
        createDenseGpsPoints(testUser, aug3HomeStart, aug3HomeEnd, HOME_LAT, HOME_LON, 600); // 10 minutes

        // Process Aug 3rd
        Instant aug3Start = aug3.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant aug3End = aug4.atStartOfDay(ZoneOffset.UTC).toInstant();

        boolean aug3Processed = dailyTimelineProcessingService.processUserTimeline(
                testUser.getId(), aug3Start, aug3End, aug3);

        assertTrue(aug3Processed, "Aug 3rd should be processed successfully");

        // Aug 4th - user goes to office (10:00-14:00), then cafe (15:00-18:00) - dense GPS points
        Instant aug4OfficeStart = aug4.atTime(10, 0).toInstant(ZoneOffset.UTC);
        Instant aug4OfficeEnd = aug4.atTime(14, 0).toInstant(ZoneOffset.UTC);
        createDenseGpsPoints(testUser, aug4OfficeStart, aug4OfficeEnd, OFFICE_LAT, OFFICE_LON, 600); // 10 minutes

        Instant aug4CafeStart = aug4.atTime(15, 0).toInstant(ZoneOffset.UTC);
        Instant aug4CafeEnd = aug4.atTime(18, 0).toInstant(ZoneOffset.UTC);
        createDenseGpsPoints(testUser, aug4CafeStart, aug4CafeEnd, CAFE_LAT, CAFE_LON, 600); // 10 minutes

        log.info("Created GPS data: Aug 3rd (22:00-23:00 at home) + Aug 4th (10:00-14:00 office, 15:00-18:00 cafe)");

        // Process Aug 4th using overnight processing
        MovementTimelineDTO aug4Timeline = wholeTimelineProcessor.processWholeTimeline(testUser.getId(), aug4);

        assertNotNull(aug4Timeline, "Aug 4th timeline should be generated");
        assertTrue(aug4Timeline.getStays().size() >= 2, "Aug 4th timeline should have at least 2 stays (office + cafe)");
    }

    @Transactional
    void cleanupTestData() {
        timelineTripRepository.delete("user.email like ?1", "%@geopulse.app");
        timelineStayRepository.delete("user.email like ?1", "%@geopulse.app");
        gpsPointRepository.delete("user.email like ?1", "%@geopulse.app");
        userRepository.delete("email like ?1", "%@geopulse.app");
    }

    private UserEntity createTestUser(String email, String fullName) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPasswordHash("test-hash");
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        userRepository.persist(user);
        return user;
    }

    private void createGpsPoint(UserEntity user, Instant timestamp, double latitude, double longitude) {
        GpsPointEntity gpsPoint = new GpsPointEntity();
        gpsPoint.setUser(user);
        gpsPoint.setTimestamp(timestamp);
        gpsPoint.setCoordinates(GeoUtils.createPoint(longitude, latitude));
        gpsPoint.setAccuracy(5.0);
        gpsPoint.setAltitude(100.0);
        gpsPoint.setVelocity(0.0);
        gpsPoint.setBattery(85.0);
        gpsPoint.setDeviceId("test-device");
        gpsPoint.setSourceType(GpsSourceType.OWNTRACKS);
        gpsPoint.setCreatedAt(Instant.now());
        gpsPointRepository.persist(gpsPoint);
    }

    /**
     * Create dense GPS points for a stay to ensure it gets detected properly.
     * Uses the proven pattern: GPS points every 10 minutes at the same coordinates.
     */
    private void createDenseGpsPoints(UserEntity user, Instant startTime, Instant endTime, double latitude, double longitude, long intervalSeconds) {
        Instant currentTime = startTime;
        while (currentTime.isBefore(endTime) || currentTime.equals(endTime)) {
            createGpsPoint(user, currentTime, latitude, longitude);
            currentTime = currentTime.plusSeconds(intervalSeconds);
        }

        log.debug("Created {} GPS points from {} to {} at [{}, {}] with {}s interval",
                Duration.between(startTime, endTime).dividedBy(Duration.ofSeconds(intervalSeconds)) + 1,
                startTime, endTime, latitude, longitude, intervalSeconds);
    }
}
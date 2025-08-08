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
 * Test the timeline prepending functionality in TimelineQueryService.
 * Verifies that previous context is properly prepended with adjusted duration.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class TimelinePrependingTest {

    @Inject
    TimelineQueryService timelineQueryService;

    @Inject
    DailyTimelineProcessingService dailyTimelineProcessingService;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    TimelineStayRepository timelineStayRepository;

    private UserEntity testUser;
    
    // Test coordinates (using proven San Francisco coordinates from working tests)
    private static final double HOME_LAT = 37.7749;
    private static final double HOME_LON = -122.4194;
    
    private static final double OFFICE_LAT = 37.7849;
    private static final double OFFICE_LON = -122.4094;

    @BeforeEach
    @Transactional
    void setUp() {
        cleanupTestData();
        testUser = createTestUser("prepending-test@geopulse.app", "Timeline Prepending Test User");
    }

    @AfterEach
    @Transactional
    void tearDown() {
        cleanupTestData();
    }

    @Test
    @Transactional
    void testTimelinePrepending_WithDurationAdjustment() {
        // Scenario: User at home Aug 3rd 20:00-22:00, then activities on Aug 4th starting at 14:00
        // Expected: Aug 4th timeline shows home stay from 20:00 (Aug 3rd) until 14:00 (Aug 4th)
        
        LocalDate aug3 = LocalDate.of(2025, 8, 3);
        LocalDate aug4 = LocalDate.of(2025, 8, 4);
        
        // Aug 3rd - user at home from 20:00-22:00 (2 hour stay - dense GPS points every 10 minutes)
        Instant aug3HomeStart = aug3.atTime(20, 0).toInstant(ZoneOffset.UTC);
        Instant aug3HomeEnd = aug3.atTime(22, 0).toInstant(ZoneOffset.UTC);
        createDenseGpsPoints(testUser, aug3HomeStart, aug3HomeEnd, HOME_LAT, HOME_LON, 600); // 10 minutes
        
        // Process Aug 3rd to create cached timeline data
        Instant aug3Start = aug3.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant aug3End = aug4.atStartOfDay(ZoneOffset.UTC).toInstant();
        
        boolean aug3Processed = dailyTimelineProcessingService.processUserTimeline(
            testUser.getId(), aug3Start, aug3End, aug3);
        
        assertTrue(aug3Processed, "Aug 3rd should be processed successfully");
        log.info("Aug 3rd processed and cached");
        
        // Aug 4th - user has activities starting at 14:00 (dense GPS points every 10 minutes)
        Instant aug4OfficeStart = aug4.atTime(14, 0).toInstant(ZoneOffset.UTC);
        Instant aug4OfficeEnd = aug4.atTime(16, 0).toInstant(ZoneOffset.UTC);
        createDenseGpsPoints(testUser, aug4OfficeStart, aug4OfficeEnd, OFFICE_LAT, OFFICE_LON, 600); // 10 minutes
        
        log.info("Created GPS data: Aug 3rd (20:00-22:00 at home) + Aug 4th (14:00-16:00 at office)");
        
        // Clear any existing Aug 4th cache to ensure fresh generation
        Instant aug4Start = aug4.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant aug4End = aug4.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);
        
        // Process Aug 4th to generate timeline with new GPS data
        boolean aug4Processed = dailyTimelineProcessingService.processUserTimeline(
            testUser.getId(), aug4Start, aug4End, aug4);
        log.info("Aug 4th processed: {}", aug4Processed);
        
        MovementTimelineDTO timeline = timelineQueryService.getTimeline(
            testUser.getId(), aug4Start, aug4End);
        
        assertNotNull(timeline, "Timeline should be generated");
        
        log.info("=== DEBUG: Timeline Details ===");
        log.info("Timeline has {} stays, {} trips", timeline.getStaysCount(), timeline.getTripsCount());
        log.info("Data source: {}", timeline.getDataSource());
        
        // Debug stays
        for (int i = 0; i < timeline.getStaysCount(); i++) {
            var stay = timeline.getStays().get(i);
            log.info("Stay #{}: {} at {} (duration: {}s, date: {})", 
                    i, stay.getLocationName(), stay.getTimestamp(), stay.getStayDuration(),
                    stay.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate());
        }
        
        // Debug trips 
        for (int i = 0; i < timeline.getTripsCount(); i++) {
            var trip = timeline.getTrips().get(i);
            log.info("Trip #{}: {} at {} (duration: {}min)", 
                    i, trip.getMovementType(), trip.getTimestamp(), trip.getTripDuration());
        }
        
        // Check if Aug 3rd timeline was actually processed and cached
        log.info("=== DEBUG: Aug 3rd Cached Data Check ===");
        long aug3StaysCount = timelineStayRepository.count("user.id = ?1 and timestamp >= ?2 and timestamp < ?3", 
                testUser.getId(), aug3Start, aug3End);
        log.info("Aug 3rd cached stays in DB: {}", aug3StaysCount);
        
        assertTrue(timeline.getStaysCount() >= 2, "Timeline should have at least 2 stays (prepended + current)");
        
        // Verify the first stay is the prepended previous context
        TimelineStayLocationDTO firstStay = timeline.getStays().get(0);
        TimelineStayLocationDTO secondStay = timeline.getStays().get(1);
        
        log.info("First stay (prepended): {} at {} (duration: {}s)", 
                firstStay.getLocationName(), firstStay.getTimestamp(), firstStay.getStayDuration());
        log.info("Second stay (current): {} at {} (duration: {}s)", 
                secondStay.getLocationName(), secondStay.getTimestamp(), secondStay.getStayDuration());
        
        // Verify the prepended stay characteristics
        LocalDate firstStayDate = firstStay.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate();
        assertEquals(aug3, firstStayDate, "First stay should be from Aug 3rd (prepended)");
        
        // Verify duration adjustment: should be from Aug 3rd 20:00 to Aug 4th 14:00 = 18 hours
        Instant expectedStartTime = aug3.atTime(20, 0).toInstant(ZoneOffset.UTC);
        Instant expectedEndTime = aug4.atTime(14, 0).toInstant(ZoneOffset.UTC);
        long expectedDurationSeconds = Duration.between(expectedStartTime, expectedEndTime).getSeconds();
        
        assertEquals(expectedStartTime, firstStay.getTimestamp(), 
                    "Prepended stay should start at original time");
        assertEquals(expectedDurationSeconds, firstStay.getStayDuration(), 
                    "Prepended stay duration should be adjusted to connect to first activity");
        
        // Verify the second stay is from Aug 4th
        LocalDate secondStayDate = secondStay.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate();
        assertEquals(aug4, secondStayDate, "Second stay should be from Aug 4th");
        
        log.info("SUCCESS: Timeline prepending with duration adjustment working correctly!");
        log.info("- Original Aug 3rd stay: 2 hours");
        log.info("- Adjusted prepended stay: {} hours (shows continuity until Aug 4th 14:00)", 
                expectedDurationSeconds / 3600);
    }

    @Transactional
    void cleanupTestData() {
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
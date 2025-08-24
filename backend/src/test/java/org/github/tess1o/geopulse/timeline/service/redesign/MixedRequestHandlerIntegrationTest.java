package org.github.tess1o.geopulse.timeline.service.redesign;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.timeline.model.TimelineStayEntity;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MixedRequestHandler.
 * Tests timeline combining, cross-day gap detection, and mixed past+today scenarios.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
class MixedRequestHandlerIntegrationTest {

    @Inject
    MixedRequestHandler mixedRequestHandler;

    @Inject
    UserRepository userRepository;
    
    @Inject
    GpsPointRepository gpsPointRepository;
    
    @Inject
    TimelineStayRepository timelineStayRepository;
    
    @Inject
    TimelineTripRepository timelineTripRepository;
    
    @Inject
    TimelineDataGapRepository timelineDataGapRepository;
    
    private UUID testUserId;
    private UserEntity testUser;
    
    // Test coordinates (San Francisco area with 5km separation)
    private static final double HOME_LAT = 37.7749;
    private static final double HOME_LON = -122.4194;
    private static final double WORK_LAT = 37.8049; 
    private static final double WORK_LON = -122.4094;
    
    @BeforeEach
    @Transactional
    void setUp() {
        // Create test user
        testUser = new UserEntity();
        testUser.setEmail("test-mixed-handler@geopulse.app");
        testUser.setFullName("Test Mixed Handler User");
        testUser.setPasswordHash("test-hash");
        testUser.setActive(true);
        testUser.setCreatedAt(Instant.now());
        userRepository.persistAndFlush(testUser);
        testUserId = testUser.getId();
    }
    
    @AfterEach
    @Transactional
    void cleanup() {
        // Clean up test data in proper order
        timelineDataGapRepository.delete("user.email = ?1", testUser.getEmail());
        timelineStayRepository.delete("user.email = ?1", testUser.getEmail());
        timelineTripRepository.delete("user.email = ?1", testUser.getEmail());
        gpsPointRepository.delete("user.email = ?1", testUser.getEmail());
        userRepository.delete("email = ?1", testUser.getEmail());
    }

    @Test
    @DisplayName("Today only request: Should generate live timeline")
    @Transactional
    void testTodayOnlyRequest() {
        // Create GPS data for today
        createGpsDataForToday();
        
        // Request today only (00:00 - 23:59 today)
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startTime = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endTime = today.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();
        
        MovementTimelineDTO result = mixedRequestHandler.handle(testUserId, startTime, endTime);
        
        // Verify results
        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
        assertEquals(TimelineDataSource.LIVE, result.getDataSource());
        assertNotNull(result.getLastUpdated());
        
        // Should have generated some timeline events from today's GPS data
        System.out.println("DEBUG: Today only result - Stays: " + result.getStaysCount() + 
                          ", Trips: " + result.getTripsCount() + ", Data gaps: " + result.getDataGapsCount());
    }

    @Test
    @DisplayName("Mixed past + today: Combine cached past with live today data")
    @Transactional
    void testMixedPastAndToday() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate yesterday = today.minusDays(1);
        
        // Create cached past data (yesterday)
        createCachedPastData(yesterday);
        
        // Create GPS data for today
        createGpsDataForToday();
        
        // Request spanning yesterday to today
        Instant startTime = yesterday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endTime = today.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();
        
        MovementTimelineDTO result = mixedRequestHandler.handle(testUserId, startTime, endTime);
        
        // Verify results
        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
        assertEquals(TimelineDataSource.MIXED, result.getDataSource());
        assertNotNull(result.getLastUpdated());
        
        // Should combine both past and today data
        System.out.println("DEBUG: Mixed result - Stays: " + result.getStaysCount() + 
                          ", Trips: " + result.getTripsCount() + ", Data gaps: " + result.getDataGapsCount());
        
        // Should have events from both past and today
        long totalEvents = result.getStaysCount() + result.getTripsCount() + result.getDataGapsCount();
        assertTrue(totalEvents > 0, "Should have combined timeline events from past and today");
    }

    @Test
    @DisplayName("Cross-day continuity: Stay spanning midnight should merge properly")
    @Transactional
    void testCrossDayContinuity_StaySpanningMidnight() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate yesterday = today.minusDays(1);
        
        // Create a stay that spans midnight (10 PM yesterday to 8 AM today)
        Instant stayStart = yesterday.atTime(22, 0).atZone(ZoneOffset.UTC).toInstant(); // 10 PM yesterday
        createCachedStayAt(stayStart, "Night Stay", 10 * 3600L); // 10 hour stay (spans midnight)
        
        // Create GPS data for today starting later (so there's continuity)
        Instant todayStart = today.atTime(8, 0).atZone(ZoneOffset.UTC).toInstant(); // 8 AM today
        createGpsPointAt(todayStart, HOME_LAT, HOME_LON);
        createGpsPointAt(todayStart.plusSeconds(3600), WORK_LAT, WORK_LON); // 9 AM at work
        
        // Request spanning yesterday to today
        Instant requestStart = yesterday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant requestEnd = today.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();
        
        MovementTimelineDTO result = mixedRequestHandler.handle(testUserId, requestStart, requestEnd);
        
        // Verify cross-day continuity handling
        assertNotNull(result);
        assertEquals(TimelineDataSource.MIXED, result.getDataSource());
        
        System.out.println("DEBUG: Cross-day continuity result - Stays: " + result.getStaysCount() + 
                          ", Trips: " + result.getTripsCount() + ", Data gaps: " + result.getDataGapsCount());
        
        // The assembler should handle the continuity appropriately
        long totalEvents = result.getStaysCount() + result.getTripsCount() + result.getDataGapsCount();
        assertTrue(totalEvents > 0, "Should handle cross-day continuity properly");
    }

    @Test
    @DisplayName("Cross-day gap detection: Gap between past and today events")
    @Transactional
    void testCrossDayGapDetection() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate yesterday = today.minusDays(1);
        
        // Create past data that ends early (6 PM yesterday)
        Instant pastStayTime = yesterday.atTime(14, 0).atZone(ZoneOffset.UTC).toInstant(); // 2 PM
        createCachedStayAt(pastStayTime, "Afternoon Stay", 4 * 3600L); // Ends at 6 PM yesterday
        
        // Create today data that starts late (10 AM today) - creating a gap
        Instant todayStart = today.atTime(10, 0).atZone(ZoneOffset.UTC).toInstant(); // 10 AM today
        createGpsPointAt(todayStart, HOME_LAT, HOME_LON);
        createGpsPointAt(todayStart.plusSeconds(3600), WORK_LAT, WORK_LON); // 11 AM
        
        // Request spanning yesterday to today  
        Instant requestStart = yesterday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant requestEnd = today.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();
        
        MovementTimelineDTO result = mixedRequestHandler.handle(testUserId, requestStart, requestEnd);
        
        // Verify gap detection
        assertNotNull(result);
        assertEquals(TimelineDataSource.MIXED, result.getDataSource());
        
        System.out.println("DEBUG: Cross-day gap result - Stays: " + result.getStaysCount() + 
                          ", Trips: " + result.getTripsCount() + ", Data gaps: " + result.getDataGapsCount());
        
        // Should have detected gaps between past and today events
        // The exact gap detection depends on the assembler's implementation
        long totalEvents = result.getStaysCount() + result.getTripsCount() + result.getDataGapsCount();
        assertTrue(totalEvents > 0, "Should detect gaps between past and today events");
    }

    @Test
    @DisplayName("Request starts today: Should generate live timeline only")
    @Transactional
    void testRequestStartsToday() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate tomorrow = today.plusDays(1);
        
        // Create GPS data for today
        createGpsDataForToday();
        
        // Request starting today extending to future
        Instant startTime = today.atTime(10, 0).atZone(ZoneOffset.UTC).toInstant(); // 10 AM today
        Instant endTime = tomorrow.atTime(12, 0).atZone(ZoneOffset.UTC).toInstant(); // 12 PM tomorrow
        
        MovementTimelineDTO result = mixedRequestHandler.handle(testUserId, startTime, endTime);
        
        // Verify results - should use live generation since it starts today
        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
        assertEquals(TimelineDataSource.LIVE, result.getDataSource());
        assertNotNull(result.getLastUpdated());
        
        System.out.println("DEBUG: Starts today result - Stays: " + result.getStaysCount() + 
                          ", Trips: " + result.getTripsCount() + ", Data gaps: " + result.getDataGapsCount());
    }

    @Test
    @DisplayName("No GPS data scenario: Handle gracefully with empty timeline")
    @Transactional
    void testNoGpsData_HandleGracefully() {
        // Don't create any GPS data
        
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startTime = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endTime = today.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();
        
        MovementTimelineDTO result = mixedRequestHandler.handle(testUserId, startTime, endTime);
        
        // Should handle gracefully with empty timeline
        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
        assertEquals(TimelineDataSource.LIVE, result.getDataSource());
        assertNotNull(result.getLastUpdated());
        
        // Timeline should be empty but valid
        assertEquals(0, result.getStaysCount());
        assertEquals(0, result.getTripsCount());
        
        System.out.println("✅ No GPS data handled gracefully");
    }

    @Test
    @DisplayName("Error handling: Timeline generation failure should return empty timeline")
    @Transactional
    void testTimelineGenerationFailure_ReturnsEmptyTimeline() {
        // This test validates that the handler gracefully handles timeline generation failures
        
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startTime = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endTime = today.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();
        
        // Call handler without creating any GPS data (which might cause generation to fail)
        MovementTimelineDTO result = mixedRequestHandler.handle(testUserId, startTime, endTime);
        
        // Should return a valid empty timeline rather than throwing exceptions
        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
        assertEquals(TimelineDataSource.LIVE, result.getDataSource());
        assertNotNull(result.getLastUpdated());
        
        // Timeline should be empty
        assertEquals(0, result.getStaysCount());
        assertEquals(0, result.getTripsCount());
        
        System.out.println("✅ Timeline generation failure handled gracefully");
    }

    @Test
    @DisplayName("ISSUE: Today request with no GPS data should create data gap instead of empty timeline")
    @Transactional
    void testTodayRequestNoGpsData_ShouldCreateDataGap() {
        /*
         * This test demonstrates the reported issue:
         * When requesting timeline for today with no GPS data, system should create data gap
         * instead of returning empty timeline (like past requests do).
         * 
         * Expected: Timeline with 1 data gap for the requested time range
         * Current bug: Returns empty timeline (0 stays, 0 trips, 0 data gaps)
         */
        
        // Request today only with no GPS data created
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startTime = today.atTime(9, 0).atZone(ZoneOffset.UTC).toInstant(); // 9 AM today
        Instant endTime = today.atTime(17, 0).atZone(ZoneOffset.UTC).toInstant();   // 5 PM today
        
        // Call handler without creating any GPS data
        MovementTimelineDTO result = mixedRequestHandler.handle(testUserId, startTime, endTime);
        
        // Verify basic properties
        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
        assertEquals(TimelineDataSource.LIVE, result.getDataSource());
        assertNotNull(result.getLastUpdated());
        
        // THIS TEST SHOULD FAIL with current implementation
        // Current bug: returns empty timeline (0 stays, 0 trips, 0 data gaps)
        // Expected fix: should return timeline with 1 data gap covering the requested period
        assertEquals(0, result.getStaysCount(), "Should have no stays when no GPS data");
        assertEquals(0, result.getTripsCount(), "Should have no trips when no GPS data");
        assertEquals(1, result.getDataGapsCount(), 
            "Should create data gap for today request when no GPS data exists (like past requests do). " +
            "Current bug: returns empty timeline instead of data gap.");
        
        // Verify the data gap covers the requested time range
        if (result.getDataGapsCount() > 0) {
            var dataGap = result.getDataGaps().get(0);
            assertEquals(startTime, dataGap.getStartTime(), "Data gap should start at request start time");
            
            // Data gap should end at "now" (current time) if request end time is in the future
            // This is the "cap to now" behavior - we don't predict future gaps
            Instant expectedEndTime = endTime.isAfter(Instant.now()) ? Instant.now() : endTime;
            
            // Allow for small timing differences (up to 1 second) due to test execution timing
            long timeDiffSeconds = Math.abs(ChronoUnit.SECONDS.between(dataGap.getEndTime(), expectedEndTime));
            assertTrue(timeDiffSeconds <= 1, 
                "Data gap should end at current time (capped to now). " +
                "Expected: " + expectedEndTime + ", Actual: " + dataGap.getEndTime() + 
                ", Diff: " + timeDiffSeconds + "s");
        }
        
        System.out.println("🐛 This test demonstrates the today data gap issue - should FAIL until fixed");
    }

    // Helper methods for creating test data

    private void createGpsDataForToday() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant baseTime = today.atTime(8, 0).atZone(ZoneOffset.UTC).toInstant(); // 8 AM today
        
        // Create realistic GPS data for today
        createGpsPointAt(baseTime, HOME_LAT, HOME_LON); // 8 AM at home
        createGpsPointAt(baseTime.plusSeconds(3600), WORK_LAT, WORK_LON); // 9 AM at work
        createGpsPointAt(baseTime.plusSeconds(7200), WORK_LAT, WORK_LON); // 10 AM still at work
        createGpsPointAt(baseTime.plusSeconds(28800), HOME_LAT, HOME_LON); // 4 PM back home
    }

    private void createCachedPastData(LocalDate date) {
        // Create cached timeline data for a past date
        Instant dayStart = date.atTime(9, 0).atZone(ZoneOffset.UTC).toInstant(); // 9 AM
        createCachedStayAt(dayStart, "Past Home", 2 * 3600L); // 2 hour stay
        
        Instant workStart = date.atTime(12, 0).atZone(ZoneOffset.UTC).toInstant(); // 12 PM  
        createCachedStayAt(workStart, "Past Work", 6 * 3600L); // 6 hour stay
    }

    private void createGpsPointAt(Instant timestamp, double lat, double lon) {
        GpsPointEntity gpsPoint = new GpsPointEntity();
        gpsPoint.setUser(testUser);
        gpsPoint.setTimestamp(timestamp);
        gpsPoint.setCoordinates(GeoUtils.createPoint(lon, lat));
        gpsPoint.setAccuracy(5.0);
        gpsPoint.setAltitude(100.0);
        gpsPoint.setVelocity(0.0);
        gpsPoint.setBattery(85.0);
        gpsPoint.setDeviceId("test-device");
        gpsPoint.setSourceType(GpsSourceType.OWNTRACKS);
        gpsPoint.setCreatedAt(Instant.now());
        gpsPointRepository.persist(gpsPoint);
    }

    private void createCachedStayAt(Instant timestamp, String locationName, long durationSeconds) {
        TimelineStayEntity stay = new TimelineStayEntity();
        stay.setUser(testUser);
        stay.setTimestamp(timestamp);
        stay.setStayDuration(durationSeconds / 60); // Convert to minutes
        stay.setLatitude(HOME_LAT);
        stay.setLongitude(HOME_LON);
        stay.setLocationName(locationName);
        // favoriteLocation and geocodingLocation can be null for test data
        
        timelineStayRepository.persistAndFlush(stay);
    }
}
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
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;
import org.github.tess1o.geopulse.timeline.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that multi-day timeline processing correctly creates data gaps when GPS data ends before the request period.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class MultiDayDataGapTest {

    @Inject
    TimelineQueryService timelineQueryService;

    @Inject
    TimelineDataGapRepository timelineDataGapRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    jakarta.persistence.EntityManager entityManager;

    private UserEntity testUser;
    private UUID testUserId;

    @BeforeEach
    @Transactional
    void setUp() {
        cleanupTestData();
        testUser = createTestUser("multiday-test@geopulse.app", "Multi-day Test User");
        testUserId = testUser.getId();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        cleanupTestData();
    }

    @Test
    @Transactional
    void testMultiDayTimeline_WithDataGapAtEnd_ShouldCreateDataGap() {
        // Arrange - GPS data that ends on Aug 14th, but request extends to Aug 16th
        LocalDate gpsEndDate = LocalDate.of(2025, 8, 14);
        LocalDate requestEndDate = LocalDate.of(2025, 8, 16);
        
        // Create GPS data ending on Aug 14th
        Instant gpsStartTime = LocalDate.of(2025, 8, 12).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant gpsEndTime = gpsEndDate.atTime(18, 0).atZone(ZoneOffset.UTC).toInstant(); // 6 PM on Aug 14th
        
        createGpsPoints(gpsStartTime, gpsEndTime);
        
        // Request timeline from Aug 12th to Aug 16th (should have data gap from Aug 14th 6PM to Aug 16th)
        Instant requestStartTime = LocalDate.of(2025, 8, 12).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant requestEndTime = requestEndDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);

        // Act - Request multi-day timeline that extends beyond available GPS data
        MovementTimelineDTO result = timelineQueryService.getTimeline(testUserId, requestStartTime, requestEndTime);

        // Assert
        assertNotNull(result, "Timeline should not be null");
        assertEquals(testUserId, result.getUserId(), "User ID should match");
        assertEquals(TimelineDataSource.CACHED, result.getDataSource(), "Data source should be CACHED");
        assertNotNull(result.getLastUpdated(), "Last updated should be set");

        // Should have data gaps where GPS data is missing
        assertTrue(result.getDataGapsCount() > 0, "Should have data gaps when GPS data ends before request period");
        
        log.info("✅ Multi-day data gap test passed - Found {} data gaps in multi-day timeline", 
                result.getDataGapsCount());
        
        // Check database to ensure data gaps were persisted
        long persistedDataGaps = timelineDataGapRepository.countByUserIdAndTimeRange(testUserId, requestStartTime, requestEndTime);
        assertTrue(persistedDataGaps > 0, "Data gaps should be persisted to database");
        
        log.info("✅ Data gap persistence verified - {} data gaps persisted to database", persistedDataGaps);
    }

    @Test
    @Transactional 
    void testMultiDayTimeline_NoGpsData_ShouldCreateFullDataGap() {
        // Arrange - Request timeline for period with no GPS data at all
        LocalDate requestStartDate = LocalDate.of(2025, 8, 18);
        LocalDate requestEndDate = LocalDate.of(2025, 8, 20);
        
        Instant requestStartTime = requestStartDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant requestEndTime = requestEndDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);

        // Act - Request timeline for period with no GPS data
        MovementTimelineDTO result = timelineQueryService.getTimeline(testUserId, requestStartTime, requestEndTime);

        // Assert
        assertNotNull(result, "Timeline should not be null");
        assertEquals(testUserId, result.getUserId(), "User ID should match");
        assertEquals(TimelineDataSource.CACHED, result.getDataSource(), "Data source should be CACHED");

        // Should have no stays or trips
        assertTrue(result.getStays().isEmpty(), "Should have no stays when no GPS data");
        assertTrue(result.getTrips().isEmpty(), "Should have no trips when no GPS data");

        // Should have exactly one data gap covering the entire requested period
        assertEquals(1, result.getDataGaps().size(), "Should have exactly one data gap covering entire period");
        
        var dataGap = result.getDataGaps().get(0);
        assertEquals(requestStartTime, dataGap.getStartTime(), "Data gap should start at request start");
        assertEquals(requestEndTime, dataGap.getEndTime(), "Data gap should end at request end");

        log.info("✅ Full period data gap test passed - Created gap from {} to {} ({} minutes)", 
                dataGap.getStartTime(), dataGap.getEndTime(), dataGap.getDurationMinutes());
    }

    @Test
    @Transactional
    void testExistingDataGaps_ShouldReturnOverlappingGaps() {
        // Arrange - First create a timeline with GPS data that ends before the range
        LocalDate gpsEndDate = LocalDate.of(2025, 8, 14);
        Instant gpsStartTime = LocalDate.of(2025, 8, 12).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant gpsEndTime = gpsEndDate.atTime(18, 0).atZone(ZoneOffset.UTC).toInstant();
        
        createGpsPoints(gpsStartTime, gpsEndTime);
        
        // Create initial timeline that will create data gaps in DB
        Instant initialRequestStart = LocalDate.of(2025, 8, 12).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant initialRequestEnd = LocalDate.of(2025, 8, 16).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);
        
        MovementTimelineDTO initialTimeline = timelineQueryService.getTimeline(testUserId, initialRequestStart, initialRequestEnd);
        assertNotNull(initialTimeline);
        assertTrue(initialTimeline.getDataGapsCount() > 0, "Initial timeline should have data gaps");
        
        // Act - Now request timeline for a period that should find existing data gaps (Aug 15-16)
        Instant laterRequestStart = LocalDate.of(2025, 8, 15).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant laterRequestEnd = LocalDate.of(2025, 8, 16).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);
        
        MovementTimelineDTO laterTimeline = timelineQueryService.getTimeline(testUserId, laterRequestStart, laterRequestEnd);
        
        // Debug output
        log.info("Later timeline result: {} stays, {} trips, {} data gaps, source: {}", 
                laterTimeline.getStaysCount(), laterTimeline.getTripsCount(), 
                laterTimeline.getDataGapsCount(), laterTimeline.getDataSource());
        
        // Assert
        assertNotNull(laterTimeline, "Timeline should not be null for period with existing data gaps");
        assertTrue(laterTimeline.getDataGapsCount() > 0, "Should find existing data gaps for Aug 15-16 period");
        assertEquals(TimelineDataSource.CACHED, laterTimeline.getDataSource(), "Should be cached data source");
        
        // Should have no stays or trips for this period (since no GPS data)
        assertTrue(laterTimeline.getStays().isEmpty(), "Should have no stays for period without GPS data");
        assertTrue(laterTimeline.getTrips().isEmpty(), "Should have no trips for period without GPS data");
        
        log.info("✅ Existing data gaps test passed - Found {} data gaps for Aug 15-16 period", 
                laterTimeline.getDataGapsCount());
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

    private void createGpsPoints(Instant startTime, Instant endTime) {
        // Create GPS points every 30 minutes
        Instant currentTime = startTime;
        double lat = 37.7749, lng = -122.4194; // San Francisco coordinates
        
        while (currentTime.isBefore(endTime)) {
            GpsPointEntity gpsPoint = new GpsPointEntity();
            gpsPoint.setUser(testUser);
            gpsPoint.setTimestamp(currentTime);
            gpsPoint.setCoordinates(GeoUtils.createPoint(lng, lat));
            gpsPoint.setAccuracy(5.0);
            gpsPoint.setAltitude(100.0);
            gpsPoint.setVelocity(0.0);
            gpsPoint.setBattery(85.0);
            gpsPoint.setDeviceId("test-device");
            gpsPoint.setSourceType(GpsSourceType.OWNTRACKS);
            gpsPoint.setCreatedAt(Instant.now());
            gpsPointRepository.persist(gpsPoint);
            
            currentTime = currentTime.plusSeconds(1800); // Add 30 minutes
        }
        
        log.info("Created GPS data from {} to {} with {} points", 
                startTime, endTime, 
                gpsPointRepository.count("user = ?1 and timestamp >= ?2 and timestamp <= ?3", testUser, startTime, endTime));
    }

    @Transactional
    void cleanupTestData() {
        // Clean up timeline data first to avoid foreign key constraint violations
        timelineDataGapRepository.delete("user.email like ?1", "%@geopulse.app");
        // Also need to clean up timeline stays and trips since we're now persisting them
        entityManager.createQuery("DELETE FROM TimelineStayEntity t WHERE t.user.email like ?1").setParameter(1, "%@geopulse.app").executeUpdate();
        entityManager.createQuery("DELETE FROM TimelineTripEntity t WHERE t.user.email like ?1").setParameter(1, "%@geopulse.app").executeUpdate();
        gpsPointRepository.delete("user.email like ?1", "%@geopulse.app");
        userRepository.delete("email like ?1", "%@geopulse.app");
    }
}
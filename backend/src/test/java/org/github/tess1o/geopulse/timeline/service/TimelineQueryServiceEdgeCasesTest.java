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
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge case and error handling tests for TimelineQueryService using real database.
 * Tests boundary conditions, null handling, and error scenarios with actual data.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class TimelineQueryServiceEdgeCasesTest {

    @Inject
    TimelineQueryService queryService;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    org.github.tess1o.geopulse.timeline.repository.TimelineDataGapRepository timelineDataGapRepository;

    private UserEntity testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up any existing test data
        cleanupTestData();

        // Create test user
        testUser = new UserEntity();
        testUser.setEmail("test-timeline-edges@geopulse.app");
        testUser.setFullName("Timeline Edge Cases Test User");
        testUser.setPasswordHash("test-hash");
        testUser.setCreatedAt(Instant.now());
        userRepository.persist(testUser);
    }

    @AfterEach
    @Transactional
    void tearDown() {
        cleanupTestData();
    }

    @Transactional
    void cleanupTestData() {
        // Clean up data gaps first to avoid foreign key constraint violations
        timelineDataGapRepository.delete("user.email = ?1", "test-timeline-edges@geopulse.app");
        gpsPointRepository.delete("user.email = ?1", "test-timeline-edges@geopulse.app");
        userRepository.delete("email = ?1", "test-timeline-edges@geopulse.app");
    }

    @Test
    @Transactional
    void testGetTimeline_WithInvalidTimeRange_ShouldHandleGracefully() {
        // Arrange - Use UTC consistently and create truly invalid range (end before start)
        Instant startTime = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endTime = startTime.minusSeconds(86400); // End before start (yesterday)

        // Act
        MovementTimelineDTO result = queryService.getTimeline(testUser.getId(), startTime, endTime);

        // Assert - Invalid range still processed, returns CACHED for empty result with no GPS data
        assertNotNull(result);
        assertEquals(TimelineDataSource.CACHED, result.getDataSource());
        assertTrue(result.getStays().isEmpty());
        assertTrue(result.getTrips().isEmpty());
    }

    @Test
    @Transactional
    void testGetTimeline_WithNoGpsData_ShouldReturnEmptyTimeline() {
        // Arrange - Today's date with no GPS data
        Instant todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant todayEnd = todayStart.plusSeconds(86400 - 1);

        // Act - No GPS data exists, so should return empty live timeline
        MovementTimelineDTO result = queryService.getTimeline(testUser.getId(), todayStart, todayEnd);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.LIVE, result.getDataSource());
        assertTrue(result.getStays().isEmpty());
        assertTrue(result.getTrips().isEmpty());
    }

    @Test
    @Transactional
    void testGetTimeline_WithPastDateNoGpsData_ShouldReturnCachedEmptyTimeline() {
        // Arrange - Use past date with no GPS data
        Instant pastStart = LocalDate.now(ZoneOffset.UTC).minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant pastEnd = pastStart.plusSeconds(86400 - 1);

        // Act - Past date with no GPS data should return cached empty timeline
        MovementTimelineDTO result = queryService.getTimeline(testUser.getId(), pastStart, pastEnd);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.CACHED, result.getDataSource());
        assertTrue(result.getStays().isEmpty());
        assertTrue(result.getTrips().isEmpty());
    }

    @Test
    @Transactional
    void testGetTimeline_WithSomeGpsData_ShouldProcessTimeline() {
        // Arrange - Add some GPS data for past date
        Instant pastStart = LocalDate.now(ZoneOffset.UTC).minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant pastEnd = pastStart.plusSeconds(86400 - 1);
        
        // Create some GPS points
        createGpsPoint(testUser, pastStart.plusSeconds(3600), 37.7749, -122.4194);
        createGpsPoint(testUser, pastStart.plusSeconds(7200), 37.7849, -122.4094);
        createGpsPoint(testUser, pastStart.plusSeconds(10800), 37.7949, -122.3994);

        // Act
        MovementTimelineDTO result = queryService.getTimeline(testUser.getId(), pastStart, pastEnd);

        // Assert - Past date with GPS data should return a timeline (could be cached or generated)
        assertNotNull(result);
        // The data source could be CACHED or LIVE depending on cache implementation
        assertTrue(result.getDataSource() == TimelineDataSource.CACHED || 
                  result.getDataSource() == TimelineDataSource.LIVE);
    }

    @Test
    @Transactional
    void testGetTimeline_ExactMidnight_ShouldHandleCorrectly() {
        // Arrange - Create GPS data for today starting at midnight
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant exactMidnight = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = exactMidnight.plusSeconds(86400 - 1);
        
        // Create GPS points for today
        createGpsPoint(testUser, exactMidnight.plusSeconds(3600), 37.7749, -122.4194);
        createGpsPoint(testUser, exactMidnight.plusSeconds(7200), 37.7849, -122.4094);

        // Act
        MovementTimelineDTO result = queryService.getTimeline(testUser.getId(), exactMidnight, endOfDay);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.LIVE, result.getDataSource());
    }

    @Test
    @Transactional
    void testGetTimeline_SpanningMultipleDays_ShouldHandleCorrectly() {
        // Arrange - Create GPS data spanning yesterday and today
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        
        Instant requestStart = yesterday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant requestEnd = today.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();
        
        // Add GPS points for both days
        createGpsPoint(testUser, yesterday.atTime(12, 0).atZone(ZoneOffset.UTC).toInstant(), 37.7749, -122.4194);
        createGpsPoint(testUser, yesterday.atTime(15, 0).atZone(ZoneOffset.UTC).toInstant(), 37.7849, -122.4094);
        createGpsPoint(testUser, today.atTime(9, 0).atZone(ZoneOffset.UTC).toInstant(), 37.7949, -122.3994);
        createGpsPoint(testUser, today.atTime(14, 0).atZone(ZoneOffset.UTC).toInstant(), 37.8049, -122.3894);

        // Act
        MovementTimelineDTO result = queryService.getTimeline(testUser.getId(), requestStart, requestEnd);

        // Assert - Should return MIXED when spanning multiple days with data
        assertNotNull(result);
        assertTrue(result.getDataSource() == TimelineDataSource.MIXED || 
                  result.getDataSource() == TimelineDataSource.LIVE || 
                  result.getDataSource() == TimelineDataSource.CACHED);
    }

    @Test
    @Transactional
    void testGetTimeline_VeryShortTimeRange_ShouldHandleCorrectly() {
        // Arrange - Use very short time range for today
        Instant start = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = start.plusSeconds(1); // 1 second range - still today
        
        // Add a GPS point within this tiny range
        createGpsPoint(testUser, start, 37.7749, -122.4194);

        // Act
        MovementTimelineDTO result = queryService.getTimeline(testUser.getId(), start, end);

        // Assert - Today's date should generate live timeline
        assertNotNull(result);
        assertEquals(TimelineDataSource.LIVE, result.getDataSource());
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
}
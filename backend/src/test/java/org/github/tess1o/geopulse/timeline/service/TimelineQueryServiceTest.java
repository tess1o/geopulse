package org.github.tess1o.geopulse.timeline.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for TimelineQueryService.
 * Tests the core logic using real Postgres instead of mocks.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class TimelineQueryServiceTest {

    @Inject
    TimelineQueryService queryService;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    jakarta.persistence.EntityManager entityManager;

    private UserEntity testUser;
    private UUID testUserId;
    private Instant testStartTime;
    private Instant testEndTime;

    @BeforeEach
    @Transactional
    void setUp() {
        // Create test user with unique email
        testUser = new UserEntity();
        testUser.setEmail("timeline-test-" + System.currentTimeMillis() + "@example.com");
        testUser.setFullName("Timeline Test User");
        testUser.setPasswordHash("dummy-hash");
        testUser.setEmailVerified(true);
        testUser.setActive(true);
        testUser.setRole("USER");
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
        userRepository.persist(testUser);
        entityManager.flush();

        testUserId = testUser.getId();
        // Use past date for most tests
        testStartTime = LocalDate.now(ZoneOffset.UTC).minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        testEndTime = testStartTime.plusSeconds(86400 - 1); // End of same day
        
        log.info("Set up timeline test user: {}", testUserId);
    }

    @AfterEach
    @Transactional
    void tearDown() {
        if (testUser != null) {
            // Clean up timeline regeneration queue first to avoid foreign key constraint violations
            entityManager.createQuery("DELETE FROM TimelineRegenerationTask t WHERE t.user.id = :userId")
                    .setParameter("userId", testUser.getId())
                    .executeUpdate();
            // Clean up test timeline data
            entityManager.createQuery("DELETE FROM TimelineStayEntity t WHERE t.user.id = :userId")
                    .setParameter("userId", testUser.getId())
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM TimelineTripEntity t WHERE t.user.id = :userId")
                    .setParameter("userId", testUser.getId())
                    .executeUpdate();
            // Clean up GPS data
            entityManager.createQuery("DELETE FROM GpsPointEntity g WHERE g.user.id = :userId")
                    .setParameter("userId", testUser.getId())
                    .executeUpdate();
            // Clean up user
            userRepository.delete(testUser);
            entityManager.flush();
        }
    }

    @Test
    @Transactional
    void testGetTimeline_TodayOnly_ShouldGenerateLive() {
        // Arrange - Today's date with test GPS data
        Instant todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant todayEnd = todayStart.plusSeconds(86400 - 1);
        
        // Create GPS data for today to have something to generate timeline from
        createTestGpsData(todayStart, todayEnd);

        // Act
        MovementTimelineDTO result = queryService.getTimeline(testUserId, todayStart, todayEnd);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.LIVE, result.getDataSource());
        assertEquals(testUserId, result.getUserId());
        assertNotNull(result.getStays());
        assertNotNull(result.getTrips());
        
        log.info("Today timeline test completed - Data source: {}, {} stays, {} trips", 
                result.getDataSource(), result.getStaysCount(), result.getTripsCount());
    }

    @Test
    @Transactional
    void testGetTimeline_PastWithCache_ShouldReturnCached() {
        // Arrange - Create GPS data for the past day
        createTestGpsData(testStartTime, testEndTime);
        
        // First call should generate and cache the timeline
        MovementTimelineDTO firstResult = queryService.getTimeline(testUserId, testStartTime, testEndTime);
        assertNotNull(firstResult);
        assertEquals(TimelineDataSource.CACHED, firstResult.getDataSource());
        
        // Act - Second call should return cached data
        MovementTimelineDTO secondResult = queryService.getTimeline(testUserId, testStartTime, testEndTime);

        // Assert
        assertNotNull(secondResult);
        assertEquals(TimelineDataSource.CACHED, secondResult.getDataSource());
        assertEquals(testUserId, secondResult.getUserId());
        
        log.info("Past with cache test completed - Data source: {}", secondResult.getDataSource());
    }

    @Test
    @Transactional
    void testGetTimeline_PastWithoutCacheButWithGps_ShouldGenerateAndCache() {
        // Arrange - Create GPS data for the past day
        createTestGpsData(testStartTime, testEndTime);

        // Act - First call should generate timeline from GPS data and cache it
        MovementTimelineDTO result = queryService.getTimeline(testUserId, testStartTime, testEndTime);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.CACHED, result.getDataSource());
        assertEquals(testUserId, result.getUserId());
        
        log.info("Past without cache but with GPS test completed - Data source: {}", result.getDataSource());
    }

    @Test
    void testGetTimeline_PastWithoutCacheOrGps_ShouldReturnEmpty() {
        // Arrange - No GPS data created, cache should be empty

        // Act
        MovementTimelineDTO result = queryService.getTimeline(testUserId, testStartTime, testEndTime);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.CACHED, result.getDataSource());
        assertEquals(testUserId, result.getUserId());
        assertTrue(result.getStays().isEmpty());
        assertTrue(result.getTrips().isEmpty());
        
        log.info("Past without cache or GPS test completed - Empty timeline with {} stays, {} trips", 
                result.getStaysCount(), result.getTripsCount());
    }

    @Test
    @Transactional
    void testGetTimeline_MixedPastAndToday_ShouldCombine() {
        // Arrange - Request spanning yesterday and today
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate yesterday = today.minusDays(1);
        
        Instant requestStart = yesterday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant requestEnd = today.atTime(12, 0).atZone(ZoneOffset.UTC).toInstant(); // Noon today
        
        // Create GPS data for yesterday to have some cached data
        createTestGpsData(requestStart, yesterday.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1000000));

        // Act
        MovementTimelineDTO result = queryService.getTimeline(testUserId, requestStart, requestEnd);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.MIXED, result.getDataSource());
        assertEquals(testUserId, result.getUserId());
        
        log.info("Mixed past and today test completed - Data source: {}, {} stays, {} trips", 
                result.getDataSource(), result.getStaysCount(), result.getTripsCount());
    }

    @Test
    void testGetTimeline_FutureDates_ShouldReturnEmpty() {
        // Arrange - Future dates
        Instant futureStart = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant futureEnd = futureStart.plusSeconds(86400 - 1);

        // Act
        MovementTimelineDTO result = queryService.getTimeline(testUserId, futureStart, futureEnd);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.LIVE, result.getDataSource());
        assertEquals(testUserId, result.getUserId());
        assertTrue(result.getStays().isEmpty());
        assertTrue(result.getTrips().isEmpty());
        
        log.info("Future dates test completed - Empty timeline with {} stays, {} trips", 
                result.getStaysCount(), result.getTripsCount());
    }

    /**
     * Helper method to create test GPS data in the database that will generate stays and trips.
     * Creates a realistic pattern: Home stay -> Work commute -> Work stay -> Home commute -> Home stay
     */
    private void createTestGpsData(Instant startTime, Instant endTime) {
        GeometryFactory geometryFactory = new GeometryFactory();
        
        // Home location (San Francisco coordinates)
        double homeLat = 37.7749, homeLng = -122.4194;
        // Work location (about 2km away)
        double workLat = 37.7849, workLng = -122.4094;
        
        Instant currentTime = startTime;
        long totalDurationSeconds = endTime.getEpochSecond() - startTime.getEpochSecond();
        
        // Phase 1: Stay at home (first 30% of time)
        long homeStay1Duration = totalDurationSeconds * 30 / 100;
        currentTime = createStayPoints(geometryFactory, homeLat, homeLng, currentTime, homeStay1Duration, "Home morning");
        
        // Phase 2: Commute to work (5% of time)
        long commuteToWorkDuration = totalDurationSeconds * 5 / 100;
        currentTime = createTripPoints(geometryFactory, homeLat, homeLng, workLat, workLng, currentTime, commuteToWorkDuration, "Commute to work");
        
        // Phase 3: Stay at work (30% of time)
        long workStayDuration = totalDurationSeconds * 30 / 100;
        currentTime = createStayPoints(geometryFactory, workLat, workLng, currentTime, workStayDuration, "Work stay");
        
        // Phase 4: Commute back home (5% of time)
        long commuteHomeDuration = totalDurationSeconds * 5 / 100;
        currentTime = createTripPoints(geometryFactory, workLat, workLng, homeLat, homeLng, currentTime, commuteHomeDuration, "Commute home");
        
        // Phase 5: Stay at home (remaining 30% of time)
        long homeStay2Duration = endTime.getEpochSecond() - currentTime.getEpochSecond();
        createStayPoints(geometryFactory, homeLat, homeLng, currentTime, homeStay2Duration, "Home evening");
        
        entityManager.flush();
        log.info("Created realistic GPS data with stays and trips between {} and {}", startTime, endTime);
    }
    
    /**
     * Create GPS points for a stay at a specific location
     */
    private Instant createStayPoints(GeometryFactory geometryFactory, double lat, double lng, Instant startTime, long durationSeconds, String phase) {
        // Create a point every 2 minutes during the stay
        long intervalSeconds = 120; // 2 minutes
        long numPoints = Math.max(3, durationSeconds / intervalSeconds); // At least 3 points
        intervalSeconds = durationSeconds / numPoints; // Adjust interval to fit exact duration
        
        for (int i = 0; i < numPoints; i++) {
            GpsPointEntity gpsPoint = new GpsPointEntity();
            gpsPoint.setUser(testUser);
            
            // Add tiny random variations to simulate GPS accuracy (within ~10 meters)
            double latVariation = (Math.random() - 0.5) * 0.0001; // ~11m variation
            double lngVariation = (Math.random() - 0.5) * 0.0001; // ~11m variation
            
            Point point = geometryFactory.createPoint(new Coordinate(lng + lngVariation, lat + latVariation));
            point.setSRID(4326);
            gpsPoint.setCoordinates(point);
            
            gpsPoint.setTimestamp(startTime.plusSeconds(i * intervalSeconds));
            gpsPoint.setAccuracy(8.0 + Math.random() * 4); // 8-12m accuracy
            gpsPoint.setAltitude(100.0);
            gpsPoint.setVelocity(0.0); // Stationary
            gpsPoint.setSourceType(GpsSourceType.OWNTRACKS);
            gpsPoint.setCreatedAt(Instant.now());
            
            gpsPointRepository.persist(gpsPoint);
        }
        
        log.debug("Created {} stay points for {} ({}s duration)", numPoints, phase, durationSeconds);
        return startTime.plusSeconds(durationSeconds);
    }
    
    /**
     * Create GPS points showing movement between two locations
     */
    private Instant createTripPoints(GeometryFactory geometryFactory, double startLat, double startLng, double endLat, double endLng, 
                                   Instant startTime, long durationSeconds, String phase) {
        // Create a point every 30 seconds during the trip
        long intervalSeconds = 30;
        long numPoints = Math.max(3, durationSeconds / intervalSeconds); // At least 3 points for movement
        intervalSeconds = durationSeconds / numPoints; // Adjust interval
        
        for (int i = 0; i < numPoints; i++) {
            GpsPointEntity gpsPoint = new GpsPointEntity();
            gpsPoint.setUser(testUser);
            
            // Linear interpolation between start and end points
            double progress = (double) i / (numPoints - 1);
            double lat = startLat + (endLat - startLat) * progress;
            double lng = startLng + (endLng - startLng) * progress;
            
            Point point = geometryFactory.createPoint(new Coordinate(lng, lat));
            point.setSRID(4326);
            gpsPoint.setCoordinates(point);
            
            gpsPoint.setTimestamp(startTime.plusSeconds(i * intervalSeconds));
            gpsPoint.setAccuracy(6.0 + Math.random() * 3); // 6-9m accuracy (better during movement)
            gpsPoint.setAltitude(100.0);
            gpsPoint.setVelocity(i == 0 || i == numPoints - 1 ? 2.0 : 8.0 + Math.random() * 5); // Slower at start/end, faster in middle
            gpsPoint.setSourceType(GpsSourceType.OWNTRACKS);
            gpsPoint.setCreatedAt(Instant.now());
            
            gpsPointRepository.persist(gpsPoint);
        }
        
        log.debug("Created {} trip points for {} ({}s duration)", numPoints, phase, durationSeconds);
        return startTime.plusSeconds(durationSeconds);
    }
}
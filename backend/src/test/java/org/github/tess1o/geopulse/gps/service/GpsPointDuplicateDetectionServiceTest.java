package org.github.tess1o.geopulse.gps.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.CleanupHelper;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for GpsPointDuplicateDetectionService focusing on the isLocationDuplicate method.
 * Tests cover all edge cases including threshold disabling, time windows, historical data, and tolerance checks.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@TestProfile(GpsPointDuplicateDetectionServiceTest.DefaultThresholdTestProfile.class)
public class GpsPointDuplicateDetectionServiceTest {

    @Inject
    GpsPointDuplicateDetectionService duplicateDetectionService;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    CleanupHelper cleanupHelper;

    private final GeometryFactory geometryFactory = new GeometryFactory();

    private UserEntity testUser;
    private static final double TEST_LAT = 40.0;
    private static final double TEST_LON = -74.0;
    private static final GpsSourceType TEST_SOURCE = GpsSourceType.OWNTRACKS;

    @BeforeEach
    @Transactional
    public void setup() {
        cleanupHelper.cleanupAll();

        // Create test user
        testUser = new UserEntity();
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("hash");
        testUser.setFullName("Test User");
        testUser.setCreatedAt(Instant.now());
        userRepository.persist(testUser);
    }

    /**
     * Test 1: First point - no existing points - should return false
     */
    @Test
    @Transactional
    public void testNoExistingPoints_AllowsFirstPoint() {
        // Given: No existing GPS points for the user
        // (cleanup already done in setup)

        // When: Try to insert first GPS point
        Instant newTime = Instant.now();
        boolean isDuplicate = duplicateDetectionService.isLocationDuplicate(
                testUser.getId(), TEST_LAT, TEST_LON, newTime, TEST_SOURCE);

        // Then: Should return false (no duplicates possible)
        assertFalse(isDuplicate, "First GPS point should not be detected as duplicate");
    }

    /**
     * Test 2: Duplicate detection within time window - should return true
     */
    @Test
    @Transactional
    public void testDuplicateWithinTimeWindow_DetectedCorrectly() {
        // Given: Insert a GPS point at location A, time T
        Instant baseTime = Instant.now();
        createGpsPoint(testUser, TEST_LAT, TEST_LON, baseTime, TEST_SOURCE);

        // When: Try to insert another point at same location A, time T+1 minute (within 2-minute threshold)
        Instant newTime = baseTime.plus(1, ChronoUnit.MINUTES);
        boolean isDuplicate = duplicateDetectionService.isLocationDuplicate(
                testUser.getId(), TEST_LAT, TEST_LON, newTime, TEST_SOURCE);

        // Then: Should return true (is a duplicate)
        assertTrue(isDuplicate, "Same location within time window should be detected as duplicate");
    }

    /**
     * Test 3: No duplicate outside time window - should return false
     */
    @Test
    @Transactional
    public void testNoDuplicateOutsideTimeWindow_AllowsPoint() {
        // Given: Insert a GPS point at location A, time T
        Instant baseTime = Instant.now();
        createGpsPoint(testUser, TEST_LAT, TEST_LON, baseTime, TEST_SOURCE);

        // When: Try to insert another point at same location A, time T+5 minutes (outside 2-minute threshold)
        Instant newTime = baseTime.plus(5, ChronoUnit.MINUTES);
        boolean isDuplicate = duplicateDetectionService.isLocationDuplicate(
                testUser.getId(), TEST_LAT, TEST_LON, newTime, TEST_SOURCE);

        // Then: Should return false (not a duplicate)
        assertFalse(isDuplicate, "Same location outside time window should not be detected as duplicate");
    }

    /**
     * Test 4: Historical data insertion - should work correctly
     */
    @Test
    @Transactional
    public void testHistoricalDataInsertion_AllowsOldPoints() {
        // Given: Insert a GPS point at location A, time T (today 14:00)
        Instant todayTime = Instant.now();
        createGpsPoint(testUser, TEST_LAT, TEST_LON, todayTime, TEST_SOURCE);

        // When: Try to insert historical point at same location A, time T-1 day (yesterday 14:00)
        Instant yesterdayTime = todayTime.minus(1, ChronoUnit.DAYS);
        boolean isDuplicate = duplicateDetectionService.isLocationDuplicate(
                testUser.getId(), TEST_LAT, TEST_LON, yesterdayTime, TEST_SOURCE);

        // Then: Should return false (not a duplicate - outside time window)
        assertFalse(isDuplicate, "Historical data insertion should be allowed when outside time window");
    }

    /**
     * Test 5: Different locations within time window - should return false
     */
    @Test
    @Transactional
    public void testDifferentLocationsWithinTimeWindow_AllowsPoint() {
        // Given: Insert a GPS point at location A, time T
        Instant baseTime = Instant.now();
        createGpsPoint(testUser, TEST_LAT, TEST_LON, baseTime, TEST_SOURCE);

        // When: Try to insert point at location B (different), time T+1 minute
        double differentLat = TEST_LAT + 0.1; // ~11 km away
        Instant newTime = baseTime.plus(1, ChronoUnit.MINUTES);
        boolean isDuplicate = duplicateDetectionService.isLocationDuplicate(
                testUser.getId(), differentLat, TEST_LON, newTime, TEST_SOURCE);

        // Then: Should return false (different locations)
        assertFalse(isDuplicate, "Different locations should not be detected as duplicate");
    }

    /**
     * Test 6: Same location within tolerance (~11m) - should return true
     */
    @Test
    @Transactional
    public void testSameLocationWithinTolerance_DetectedAsDuplicate() {
        // Given: Insert a GPS point at location (40.0000, -74.0000), time T
        Instant baseTime = Instant.now();
        createGpsPoint(testUser, TEST_LAT, TEST_LON, baseTime, TEST_SOURCE);

        // When: Try to insert point at location (40.00005, -74.00005), time T+1 minute
        // This is within 0.0001° tolerance (~11 meters)
        double nearbyLat = TEST_LAT + 0.00005;
        double nearbyLon = TEST_LON + 0.00005;
        Instant newTime = baseTime.plus(1, ChronoUnit.MINUTES);
        boolean isDuplicate = duplicateDetectionService.isLocationDuplicate(
                testUser.getId(), nearbyLat, nearbyLon, newTime, TEST_SOURCE);

        // Then: Should return true (is a duplicate within tolerance)
        assertTrue(isDuplicate, "Nearby location within tolerance should be detected as duplicate");
    }

    /**
     * Test 7: Different source types - should not interfere
     */
    @Test
    @Transactional
    public void testDifferentSourceTypes_DoNotInterfere() {
        // Given: Insert OwnTracks point at location A, time T
        Instant baseTime = Instant.now();
        createGpsPoint(testUser, TEST_LAT, TEST_LON, baseTime, GpsSourceType.OWNTRACKS);

        // When: Try to insert Overland point at same location A, time T+1 minute
        Instant newTime = baseTime.plus(1, ChronoUnit.MINUTES);
        boolean isDuplicate = duplicateDetectionService.isLocationDuplicate(
                testUser.getId(), TEST_LAT, TEST_LON, newTime, GpsSourceType.OVERLAND);

        // Then: Should return false (different source types don't interfere)
        assertFalse(isDuplicate, "Different source types should not be detected as duplicates of each other");
    }

    /**
     * Test 8: Edge case - exactly at threshold boundary (2 minutes)
     */
    @Test
    @Transactional
    public void testExactlyAtThresholdBoundary_DetectedAsDuplicate() {
        // Given: Insert a GPS point at location A, time T
        Instant baseTime = Instant.now();
        createGpsPoint(testUser, TEST_LAT, TEST_LON, baseTime, TEST_SOURCE);

        // When: Try to insert point at same location A, time T+2 minutes (exactly at 2-minute threshold)
        Instant newTime = baseTime.plus(2, ChronoUnit.MINUTES);
        boolean isDuplicate = duplicateDetectionService.isLocationDuplicate(
                testUser.getId(), TEST_LAT, TEST_LON, newTime, TEST_SOURCE);

        // Then: Should return true (within window, includes boundary)
        assertTrue(isDuplicate, "Point exactly at threshold boundary should be detected as duplicate");
    }

    /**
     * Test 9: Multiple points in time window - detects any match
     */
    @Test
    @Transactional
    public void testMultiplePointsInTimeWindow_DetectsAnyMatch() {
        // Given: Insert multiple GPS points in different locations
        Instant baseTime = Instant.now();
        createGpsPoint(testUser, TEST_LAT + 0.1, TEST_LON, baseTime, TEST_SOURCE);
        createGpsPoint(testUser, TEST_LAT + 0.2, TEST_LON, baseTime.plus(30, ChronoUnit.SECONDS), TEST_SOURCE);
        createGpsPoint(testUser, TEST_LAT, TEST_LON, baseTime.plus(1, ChronoUnit.MINUTES), TEST_SOURCE);

        // When: Try to insert point at location matching the third point, time T+1.5 minutes
        Instant newTime = baseTime.plus(90, ChronoUnit.SECONDS);
        boolean isDuplicate = duplicateDetectionService.isLocationDuplicate(
                testUser.getId(), TEST_LAT, TEST_LON, newTime, TEST_SOURCE);

        // Then: Should return true (matches the third point)
        assertTrue(isDuplicate, "Should detect duplicate when matching any point in time window");
    }

    /**
     * Test 10: Future point insertion (reverse historical)
     */
    @Test
    @Transactional
    public void testFuturePointInsertion_WorksCorrectly() {
        // Given: Insert a GPS point at location A, time T (yesterday)
        Instant yesterdayTime = Instant.now().minus(1, ChronoUnit.DAYS);
        createGpsPoint(testUser, TEST_LAT, TEST_LON, yesterdayTime, TEST_SOURCE);

        // When: Try to insert future point at same location A, time T+1 day (today)
        Instant todayTime = Instant.now();
        boolean isDuplicate = duplicateDetectionService.isLocationDuplicate(
                testUser.getId(), TEST_LAT, TEST_LON, todayTime, TEST_SOURCE);

        // Then: Should return false (outside time window)
        assertFalse(isDuplicate, "Future point insertion should work correctly when outside time window");
    }

    // =================== Helper Methods ===================

    private void createGpsPoint(UserEntity user, double lat, double lon, Instant timestamp, GpsSourceType sourceType) {
        GpsPointEntity gpsPoint = new GpsPointEntity();
        gpsPoint.setUser(user);
        gpsPoint.setCoordinates(createPoint(lon, lat));
        gpsPoint.setTimestamp(timestamp);
        gpsPoint.setSourceType(sourceType);
        gpsPoint.setCreatedAt(Instant.now());
        gpsPoint.setAccuracy(10.0);
        gpsPointRepository.persist(gpsPoint);
    }

    private Point createPoint(double lon, double lat) {
        return geometryFactory.createPoint(new Coordinate(lon, lat));
    }

    // =================== Test Profile ===================

    /**
     * Default test profile with threshold = 2 minutes
     */
    public static class DefaultThresholdTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("geopulse.gps.duplicate-detection.location-time-threshold-minutes", "2");
        }
    }
}

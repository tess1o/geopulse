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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for GpsPointDuplicateDetectionService with threshold disabled (set to -1).
 * Verifies that duplicate detection is completely bypassed when threshold <= 0.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@TestProfile(GpsPointDuplicateDetectionServiceDisabledTest.DisabledThresholdTestProfile.class)
public class GpsPointDuplicateDetectionServiceDisabledTest {

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
     * Test: Threshold disabled (≤ 0) - should always return false even for duplicate locations
     */
    @Test
    @Transactional
    public void testThresholdDisabled_AllowsDuplicates() {
        // Given: Insert a GPS point at location A, time T
        Instant baseTime = Instant.now();
        createGpsPoint(testUser, TEST_LAT, TEST_LON, baseTime, TEST_SOURCE);

        // When: Try to insert another point at same location A, time T+1 minute
        Instant newTime = baseTime.plus(1, ChronoUnit.MINUTES);
        boolean isDuplicate = duplicateDetectionService.isLocationDuplicate(
                testUser.getId(), TEST_LAT, TEST_LON, newTime, TEST_SOURCE);

        // Then: Should return false (not a duplicate) because threshold is disabled
        assertFalse(isDuplicate, "Duplicate detection should be disabled when threshold <= 0");
    }

    /**
     * Test: With threshold disabled, even exact same location at same time should be allowed
     */
    @Test
    @Transactional
    public void testThresholdDisabled_AllowsExactDuplicates() {
        // Given: Insert a GPS point at location A, time T
        Instant baseTime = Instant.now();
        createGpsPoint(testUser, TEST_LAT, TEST_LON, baseTime, TEST_SOURCE);

        // When: Try to insert another point at exact same location and time
        boolean isDuplicate = duplicateDetectionService.isLocationDuplicate(
                testUser.getId(), TEST_LAT, TEST_LON, baseTime, TEST_SOURCE);

        // Then: Should return false because threshold is disabled
        assertFalse(isDuplicate, "Should allow exact duplicates when threshold is disabled");
    }

    /**
     * Test: With threshold disabled, historical data can be inserted without issues
     */
    @Test
    @Transactional
    public void testThresholdDisabled_AllowsHistoricalData() {
        // Given: Insert a GPS point at location A, time T (today)
        Instant todayTime = Instant.now();
        createGpsPoint(testUser, TEST_LAT, TEST_LON, todayTime, TEST_SOURCE);

        // When: Try to insert historical point at same location A, time T-1 day (yesterday)
        Instant yesterdayTime = todayTime.minus(1, ChronoUnit.DAYS);
        boolean isDuplicate = duplicateDetectionService.isLocationDuplicate(
                testUser.getId(), TEST_LAT, TEST_LON, yesterdayTime, TEST_SOURCE);

        // Then: Should return false
        assertFalse(isDuplicate, "Should allow historical data when threshold is disabled");
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
     * Test profile with threshold disabled (set to -1)
     */
    public static class DisabledThresholdTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("geopulse.gps.duplicate-detection.location-time-threshold-minutes", "-1");
        }
    }
}

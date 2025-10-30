package org.github.tess1o.geopulse.gps.service.filter;

import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GPS data filtering service.
 * Tests filtering logic for accuracy and speed thresholds.
 */
class GpsDataFilteringServiceTest {

    private GpsDataFilteringService filteringService;
    private UserEntity testUser;
    private GpsSourceConfigEntity config;

    @BeforeEach
    void setUp() {
        filteringService = new GpsDataFilteringService();

        // Create test user
        testUser = new UserEntity();
        testUser.setId(UUID.randomUUID());

        // Create default config
        config = new GpsSourceConfigEntity();
        config.setId(UUID.randomUUID());
        config.setFilterInaccurateData(false); // Disabled by default
        config.setMaxAllowedAccuracy(100); // 100 meters
        config.setMaxAllowedSpeed(250); // 250 km/h
    }

    @Test
    void testFilteringDisabled_AcceptsAllPoints() {
        // Given: Filtering is disabled
        config.setFilterInaccurateData(false);

        // When: Point with bad accuracy and speed
        GpsPointEntity entity = createGpsPoint(500.0, 400.0);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is accepted
        assertTrue(result.isAccepted());
        assertFalse(result.isRejected());
        assertNull(result.getRejectionReason());
    }

    @Test
    void testAccuracyFilter_AcceptsPointUnderThreshold() {
        // Given: Filtering enabled with 100m threshold
        config.setFilterInaccurateData(true);
        config.setMaxAllowedAccuracy(100);

        // When: Point with 50m accuracy (under threshold)
        GpsPointEntity entity = createGpsPoint(50.0, null);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is accepted
        assertTrue(result.isAccepted());
    }

    @Test
    void testAccuracyFilter_AcceptsPointAtThreshold() {
        // Given: Filtering enabled with 100m threshold
        config.setFilterInaccurateData(true);
        config.setMaxAllowedAccuracy(100);

        // When: Point with exactly 100m accuracy (at threshold)
        GpsPointEntity entity = createGpsPoint(100.0, null);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is accepted (not greater than threshold)
        assertTrue(result.isAccepted());
    }

    @Test
    void testAccuracyFilter_RejectsPointOverThreshold() {
        // Given: Filtering enabled with 100m threshold
        config.setFilterInaccurateData(true);
        config.setMaxAllowedAccuracy(100);

        // When: Point with 150m accuracy (over threshold)
        GpsPointEntity entity = createGpsPoint(150.0, null);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is rejected
        assertTrue(result.isRejected());
        assertFalse(result.isAccepted());
        assertNotNull(result.getRejectionReason());
        assertTrue(result.getRejectionReason().toLowerCase(Locale.ROOT).contains("accuracy"));
        assertTrue(result.getRejectionReason().contains("150"));
    }

    @Test
    void testAccuracyFilter_AcceptsNullAccuracy() {
        // Given: Filtering enabled
        config.setFilterInaccurateData(true);
        config.setMaxAllowedAccuracy(100);

        // When: Point with null accuracy
        GpsPointEntity entity = createGpsPoint(null, 50.0);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is accepted (null accuracy means unavailable, not inaccurate)
        assertTrue(result.isAccepted());
    }

    @Test
    void testSpeedFilter_AcceptsPointUnderThreshold() {
        // Given: Filtering enabled with 250 km/h threshold
        config.setFilterInaccurateData(true);
        config.setMaxAllowedSpeed(250);

        // When: Point with 120 km/h speed (under threshold)
        GpsPointEntity entity = createGpsPoint(null, 120.0);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is accepted
        assertTrue(result.isAccepted());
    }

    @Test
    void testSpeedFilter_AcceptsPointAtThreshold() {
        // Given: Filtering enabled with 250 km/h threshold
        config.setFilterInaccurateData(true);
        config.setMaxAllowedSpeed(250);

        // When: Point with exactly 250 km/h speed (at threshold)
        GpsPointEntity entity = createGpsPoint(null, 250.0);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is accepted (not greater than threshold)
        assertTrue(result.isAccepted());
    }

    @Test
    void testSpeedFilter_RejectsPointOverThreshold() {
        // Given: Filtering enabled with 250 km/h threshold
        config.setFilterInaccurateData(true);
        config.setMaxAllowedSpeed(250);

        // When: Point with 300 km/h speed (over threshold)
        GpsPointEntity entity = createGpsPoint(null, 300.0);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is rejected
        assertTrue(result.isRejected());
        assertFalse(result.isAccepted());
        assertNotNull(result.getRejectionReason());
        assertTrue(result.getRejectionReason().toLowerCase(Locale.ROOT).contains("speed"));
        assertTrue(result.getRejectionReason().contains("300"));
    }

    @Test
    void testSpeedFilter_AcceptsNullSpeed() {
        // Given: Filtering enabled
        config.setFilterInaccurateData(true);
        config.setMaxAllowedSpeed(250);

        // When: Point with null speed
        GpsPointEntity entity = createGpsPoint(50.0, null);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is accepted (null speed means unavailable)
        assertTrue(result.isAccepted());
    }

    @Test
    void testCombinedFilters_AcceptsBothUnderThreshold() {
        // Given: Filtering enabled with both thresholds
        config.setFilterInaccurateData(true);
        config.setMaxAllowedAccuracy(100);
        config.setMaxAllowedSpeed(250);

        // When: Point with good accuracy and speed
        GpsPointEntity entity = createGpsPoint(50.0, 120.0);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is accepted
        assertTrue(result.isAccepted());
    }

    @Test
    void testCombinedFilters_RejectsIfAccuracyExceeds() {
        // Given: Filtering enabled with both thresholds
        config.setFilterInaccurateData(true);
        config.setMaxAllowedAccuracy(100);
        config.setMaxAllowedSpeed(250);

        // When: Point with bad accuracy but good speed
        GpsPointEntity entity = createGpsPoint(150.0, 120.0);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is rejected due to accuracy
        assertTrue(result.isRejected());
        assertTrue(result.getRejectionReason().toLowerCase(Locale.ROOT).contains("accuracy"));
    }

    @Test
    void testCombinedFilters_RejectsIfSpeedExceeds() {
        // Given: Filtering enabled with both thresholds
        config.setFilterInaccurateData(true);
        config.setMaxAllowedAccuracy(100);
        config.setMaxAllowedSpeed(250);

        // When: Point with good accuracy but bad speed
        GpsPointEntity entity = createGpsPoint(50.0, 300.0);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is rejected due to speed
        assertTrue(result.isRejected());
        assertTrue(result.getRejectionReason().toLowerCase(Locale.ROOT).contains("speed"));
    }

    @Test
    void testNullThresholds_AcceptsAllPoints() {
        // Given: Filtering enabled but thresholds are null (no limits configured)
        config.setFilterInaccurateData(true);
        config.setMaxAllowedAccuracy(null);
        config.setMaxAllowedSpeed(null);

        // When: Point with any values
        GpsPointEntity entity = createGpsPoint(500.0, 400.0);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is accepted (null thresholds mean no filtering)
        assertTrue(result.isAccepted());
    }

    @Test
    void testAccuracyFilterOnly_IgnoresSpeed() {
        // Given: Filtering enabled with only accuracy threshold
        config.setFilterInaccurateData(true);
        config.setMaxAllowedAccuracy(100);
        config.setMaxAllowedSpeed(null); // No speed threshold

        // When: Point with bad accuracy but extreme speed
        GpsPointEntity entity = createGpsPoint(150.0, 1000.0);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is rejected due to accuracy only
        assertTrue(result.isRejected());
        assertTrue(result.getRejectionReason().toLowerCase(Locale.ROOT).contains("accuracy"));
    }

    @Test
    void testSpeedFilterOnly_IgnoresAccuracy() {
        // Given: Filtering enabled with only speed threshold
        config.setFilterInaccurateData(true);
        config.setMaxAllowedAccuracy(null); // No accuracy threshold
        config.setMaxAllowedSpeed(250);

        // When: Point with extreme accuracy but bad speed
        GpsPointEntity entity = createGpsPoint(500.0, 300.0);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is rejected due to speed only
        assertTrue(result.isRejected());
        assertTrue(result.getRejectionReason().toLowerCase(Locale.ROOT).contains("speed"));
    }

    @Test
    void testZeroValues_TreatedAsValid() {
        // Given: Filtering enabled
        config.setFilterInaccurateData(true);
        config.setMaxAllowedAccuracy(100);
        config.setMaxAllowedSpeed(250);

        // When: Point with zero accuracy and speed (stationary with perfect GPS)
        GpsPointEntity entity = createGpsPoint(0.0, 0.0);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is accepted
        assertTrue(result.isAccepted());
    }

    /**
     * Helper method to create a GPS point entity for testing
     */
    private GpsPointEntity createGpsPoint(Double accuracy, Double speedKmh) {
        GpsPointEntity entity = new GpsPointEntity();
        entity.setUser(testUser);
        entity.setSourceType(GpsSourceType.OWNTRACKS);
        entity.setAccuracy(accuracy);
        entity.setVelocity(speedKmh); // Already in km/h
        entity.setTimestamp(Instant.now());
        entity.setDeviceId("test-device");
        return entity;
    }
}

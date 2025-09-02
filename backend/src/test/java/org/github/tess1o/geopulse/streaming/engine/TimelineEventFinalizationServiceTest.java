package org.github.tess1o.geopulse.streaming.engine;

import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.UserState;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TimelineEventFinalizationService accuracy validation.
 */
@ExtendWith(MockitoExtension.class)
class TimelineEventFinalizationServiceTest {

    @InjectMocks
    private TimelineEventFinalizationService service;

    private TimelineConfig config;
    
    @BeforeEach
    void setUp() {
        config = TimelineConfig.builder()
            .staypointMaxAccuracyThreshold(50.0) // 50m threshold
            .staypointMinAccuracyRatio(0.7) // require 70% accurate points
            .useVelocityAccuracy(true) // enable validation
            .build();
    }

    @Test
    void shouldFinalizeStay_WhenAccuracyRatioSufficient() {
        UserState userState = new UserState();
        
        // Add points with good accuracy ratio (3/4 = 75% > 70% required)
        userState.addActivePoint(createGpsPoint("2024-08-15T08:00:00Z", 40.7589, -73.9851, 30.0)); // good
        userState.addActivePoint(createGpsPoint("2024-08-15T08:05:00Z", 40.7590, -73.9850, 20.0)); // good
        userState.addActivePoint(createGpsPoint("2024-08-15T08:10:00Z", 40.7588, -73.9852, 40.0)); // good
        userState.addActivePoint(createGpsPoint("2024-08-15T08:15:00Z", 40.7587, -73.9853, 80.0)); // bad

        Stay result = service.finalizeStayWithoutLocation(userState, config);

        assertNotNull(result);
        assertEquals(Instant.parse("2024-08-15T08:00:00Z"), result.getStartTime());
        // Should be approximately the centroid
        assertEquals(40.7588, result.getLatitude(), 0.001);
        assertEquals(-73.9851, result.getLongitude(), 0.001);
    }

    @Test
    void shouldRejectStay_WhenAccuracyRatioInsufficient() {
        UserState userState = new UserState();
        
        // Add points with poor accuracy ratio (1/4 = 25% < 70% required)
        userState.addActivePoint(createGpsPoint("2024-08-15T08:00:00Z", 40.7589, -73.9851, 30.0)); // good
        userState.addActivePoint(createGpsPoint("2024-08-15T08:05:00Z", 40.7590, -73.9850, 100.0)); // bad
        userState.addActivePoint(createGpsPoint("2024-08-15T08:10:00Z", 40.7588, -73.9852, 80.0)); // bad
        userState.addActivePoint(createGpsPoint("2024-08-15T08:15:00Z", 40.7587, -73.9853, 90.0)); // bad

        Stay result = service.finalizeStayWithoutLocation(userState, config);

        assertNull(result, "Stay should be rejected due to insufficient accuracy ratio");
    }

    @Test
    void shouldAllowStay_WhenAccuracyValidationDisabled() {
        TimelineConfig configDisabled = TimelineConfig.builder()
            .staypointMaxAccuracyThreshold(50.0)
            .staypointMinAccuracyRatio(0.7)
            .useVelocityAccuracy(false) // disabled
            .build();

        UserState userState = new UserState();
        
        // Add points with poor accuracy ratio
        userState.addActivePoint(createGpsPoint("2024-08-15T08:00:00Z", 40.7589, -73.9851, 30.0)); // good
        userState.addActivePoint(createGpsPoint("2024-08-15T08:05:00Z", 40.7590, -73.9850, 100.0)); // bad
        userState.addActivePoint(createGpsPoint("2024-08-15T08:10:00Z", 40.7588, -73.9852, 80.0)); // bad

        Stay result = service.finalizeStayWithoutLocation(userState, configDisabled);

        assertNotNull(result, "Stay should be allowed when validation is disabled");
    }

    @Test
    void shouldAllowStay_WhenThresholdsNotConfigured() {
        TimelineConfig configNoThresholds = TimelineConfig.builder()
            .staypointMaxAccuracyThreshold(null) // not set
            .staypointMinAccuracyRatio(null) // not set
            .useVelocityAccuracy(true)
            .build();

        UserState userState = new UserState();
        userState.addActivePoint(createGpsPoint("2024-08-15T08:00:00Z", 40.7589, -73.9851, 200.0)); // normally bad

        Stay result = service.finalizeStayWithoutLocation(userState, configNoThresholds);

        assertNotNull(result, "Stay should be allowed when thresholds are not configured");
    }

    @Test
    void shouldReturnNull_WhenUserStateEmpty() {
        UserState emptyState = new UserState();

        Stay result = service.finalizeStayWithoutLocation(emptyState, config);

        assertNull(result, "Should return null when user state has no active points");
    }

    @Test
    void shouldCalculateAccuracyRatioCorrectly() {
        UserState userState = new UserState();
        
        // Exactly 70% accurate (7/10)
        for (int i = 0; i < 7; i++) {
            userState.addActivePoint(createGpsPoint(
                Instant.parse("2024-08-15T08:0" + i + ":00Z"), 40.7589, -73.9851, 30.0)); // good
        }
        for (int i = 7; i < 10; i++) {
            userState.addActivePoint(createGpsPoint(
                Instant.parse("2024-08-15T08:0" + i + ":00Z"), 40.7589, -73.9851, 80.0)); // bad
        }

        Stay result = service.finalizeStayWithoutLocation(userState, config);

        assertNotNull(result, "Should allow stay when accuracy ratio exactly meets threshold");
    }

    private GPSPoint createGpsPoint(String timestamp, double lat, double lon, double accuracy) {
        return GPSPoint.builder()
            .timestamp(Instant.parse(timestamp))
            .latitude(lat)
            .longitude(lon)
            .speed(1.0)
            .accuracy(accuracy)
            .build();
    }

    private GPSPoint createGpsPoint(Instant timestamp, double lat, double lon, double accuracy) {
        return GPSPoint.builder()
            .timestamp(timestamp)
            .latitude(lat)
            .longitude(lon)
            .speed(1.0)
            .accuracy(accuracy)
            .build();
    }
}
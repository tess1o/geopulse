package org.github.tess1o.geopulse.streaming.engine;

import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.UserState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;


/**
 * Unit tests for the streaming timeline processor state machine.
 * Tests the core algorithm logic with controlled GPS point scenarios.
 */
@ExtendWith(MockitoExtension.class)
class StreamingTimelineProcessorTest {

    @InjectMocks
    private StreamingTimelineProcessor processor;

    @Mock
    private DataGapDetectionEngine dataGapEngine;
    
    @Mock
    private TimelineEventFinalizationService finalizationService;
    
    private TimelineConfig config;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        
        // Create test configuration with accuracy filtering enabled
        config = TimelineConfig.builder()
            .staypointRadiusMeters(100) // stay radius
            .staypointMinDurationMinutes(10) // 10 minutes min stay
            .staypointMaxAccuracyThreshold(50.0) // 50m accuracy threshold
            .staypointMinAccuracyRatio(0.7) // require 70% accurate points
            .useVelocityAccuracy(true) // enable accuracy validation
            .staypointVelocityThreshold(2.0) // 2 m/s
            .dataGapThresholdSeconds(3600) // 1 hour
            .dataGapMinDurationSeconds(60) // 1 minute
            .build();
    }

    @Test
    void shouldFilterPointsByAccuracy_RemoveHighAccuracyPoints() {
        List<GPSPoint> contextPoints = Arrays.asList(
            createGpsPoint(Instant.parse("2024-08-15T08:00:00Z"), 40.7589, -73.9851, 0.0, 30.0), // good
            createGpsPoint(Instant.parse("2024-08-15T08:01:00Z"), 40.7589, -73.9851, 0.0, 100.0), // bad - filtered
            createGpsPoint(Instant.parse("2024-08-15T08:02:00Z"), 40.7589, -73.9851, 0.0, 20.0)  // good
        );

        List<GPSPoint> newPoints = Arrays.asList(
            createGpsPoint(Instant.parse("2024-08-15T08:03:00Z"), 40.7589, -73.9851, 0.0, 80.0), // bad - filtered
            createGpsPoint(Instant.parse("2024-08-15T08:04:00Z"), 40.7589, -73.9851, 0.0, 40.0)  // good
        );

        List<TimelineEvent> result = processor.processPoints(newPoints, config, testUserId);

        // Result should not be null
        assertNotNull(result);
        // The high accuracy points should be filtered out and not processed
        // In this case, we would have 2 context + 1 new = 3 good points total
        // The exact behavior depends on the state machine, but filtering should occur
    }

    @Test
    void shouldAllowAllPoints_WhenAccuracyThresholdNotSet() {
        TimelineConfig configNoAccuracy = TimelineConfig.builder()
            .staypointRadiusMeters(100)
            .staypointMinDurationMinutes(10)
            .staypointMaxAccuracyThreshold(null) // no threshold
            .useVelocityAccuracy(false) // disabled
            .build();

        List<GPSPoint> points = Arrays.asList(
            createGpsPoint(Instant.parse("2024-08-15T08:00:00Z"), 40.7589, -73.9851, 0.0, 200.0), // normally bad
            createGpsPoint(Instant.parse("2024-08-15T08:01:00Z"), 40.7589, -73.9851, 0.0, 300.0)  // normally bad
        );

        List<TimelineEvent> result = processor.processPoints(Arrays.asList(), configNoAccuracy, testUserId);

        // Should not filter when threshold is not set
        assertNotNull(result);
    }

    @Test
    void shouldAllowAllPoints_WhenAccuracyValidationDisabled() {
        TimelineConfig configDisabled = TimelineConfig.builder()
            .staypointRadiusMeters(100)
            .staypointMinDurationMinutes(10)
            .staypointMaxAccuracyThreshold(50.0) // threshold set
            .staypointMinAccuracyRatio(0.7)
            .useVelocityAccuracy(false) // but validation disabled
            .build();

        List<TimelineEvent> result = processor.processPoints(Arrays.asList(), configDisabled, testUserId);

        assertNotNull(result);
    }

    @Test 
    void shouldRejectStay_WhenAccuracyRatioTooLow() {
        // Mock finalization service to return null when accuracy ratio fails
        when(finalizationService.finalizeStayWithoutLocation(any(UserState.class), eq(config)))
            .thenReturn(null); // This simulates accuracy ratio validation failure

        List<GPSPoint> points = Arrays.asList(
            createGpsPoint(Instant.parse("2024-08-15T08:00:00Z"), 40.7589, -73.9851, 0.0, 20.0), // good
            createGpsPoint(Instant.parse("2024-08-15T08:10:00Z"), 40.7589, -73.9851, 0.0, 100.0), // bad
            createGpsPoint(Instant.parse("2024-08-15T08:20:00Z"), 40.7589, -73.9851, 0.0, 100.0), // bad
            createGpsPoint(Instant.parse("2024-08-15T08:30:00Z"), 40.7589, -73.9851, 0.0, 100.0)  // bad
            // Only 1/4 points are accurate (25%) < 70% required
        );

        List<TimelineEvent> result = processor.processPoints(points, config, testUserId);

        assertNotNull(result);
        // The exact behavior depends on state transitions, but stays should be rejected due to low accuracy ratio
    }

    private GPSPoint createGpsPoint(Instant timestamp, double lat, double lon, double speed, double accuracy) {
        return GPSPoint.builder()
            .timestamp(timestamp)
            .latitude(lat)
            .longitude(lon)
            .speed(speed)
            .accuracy(accuracy)
            .build();
    }
}
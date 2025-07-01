package org.github.tess1o.geopulse.timeline.service;

import org.github.tess1o.geopulse.timeline.core.TimelineValidationService;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.timeline.model.TrackPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TimelineValidationServiceTest {

    private TimelineValidationService validationService;
    private TimelineConfig validConfig;

    @BeforeEach
    void setUp() {
        validationService = new TimelineValidationService();
        validConfig = createValidConfig();
    }

    @Test
    void testValidateTimelineConfig_Valid() {
        assertDoesNotThrow(() -> validationService.validateTimelineConfig(validConfig));
    }

    @Test
    void testValidateTimelineConfig_Null() {
        assertThrows(IllegalArgumentException.class, () -> 
            validationService.validateTimelineConfig(null));
    }

    @Test
    void testValidateTimelineConfig_NegativeVelocityThreshold() {
        TimelineConfig config = TimelineConfig.builder()
            .staypointDetectionAlgorithm(validConfig.getStaypointDetectionAlgorithm())
            .useVelocityAccuracy(validConfig.getUseVelocityAccuracy())
            .staypointVelocityThreshold(-1.0)
            .staypointMaxAccuracyThreshold(validConfig.getStaypointMaxAccuracyThreshold())
            .staypointMinAccuracyRatio(validConfig.getStaypointMinAccuracyRatio())
            .tripMinDistanceMeters(validConfig.getTripMinDistanceMeters())
            .tripMinDurationMinutes(validConfig.getTripMinDurationMinutes())
            .isMergeEnabled(validConfig.getIsMergeEnabled())
            .mergeMaxDistanceMeters(validConfig.getMergeMaxDistanceMeters())
            .mergeMaxTimeGapMinutes(validConfig.getMergeMaxTimeGapMinutes())
            .tripDetectionAlgorithm(validConfig.getTripDetectionAlgorithm())
            .build();
        
        assertThrows(IllegalArgumentException.class, () -> 
            validationService.validateTimelineConfig(config));
    }

    @Test
    void testValidateTimelineConfig_ZeroAccuracyThreshold() {
        TimelineConfig config = TimelineConfig.builder()
            .staypointDetectionAlgorithm(validConfig.getStaypointDetectionAlgorithm())
            .useVelocityAccuracy(validConfig.getUseVelocityAccuracy())
            .staypointVelocityThreshold(validConfig.getStaypointVelocityThreshold())
            .staypointMaxAccuracyThreshold(0.0)
            .staypointMinAccuracyRatio(validConfig.getStaypointMinAccuracyRatio())
            .tripMinDistanceMeters(validConfig.getTripMinDistanceMeters())
            .tripMinDurationMinutes(validConfig.getTripMinDurationMinutes())
            .isMergeEnabled(validConfig.getIsMergeEnabled())
            .mergeMaxDistanceMeters(validConfig.getMergeMaxDistanceMeters())
            .mergeMaxTimeGapMinutes(validConfig.getMergeMaxTimeGapMinutes())
            .tripDetectionAlgorithm(validConfig.getTripDetectionAlgorithm())
            .build();
        
        assertThrows(IllegalArgumentException.class, () -> 
            validationService.validateTimelineConfig(config));
    }

    @Test
    void testValidateTimelineConfig_InvalidAccuracyRatio() {
        TimelineConfig config = TimelineConfig.builder()
            .staypointDetectionAlgorithm(validConfig.getStaypointDetectionAlgorithm())
            .useVelocityAccuracy(validConfig.getUseVelocityAccuracy())
            .staypointVelocityThreshold(validConfig.getStaypointVelocityThreshold())
            .staypointMaxAccuracyThreshold(validConfig.getStaypointMaxAccuracyThreshold())
            .staypointMinAccuracyRatio(1.5)
            .tripMinDistanceMeters(validConfig.getTripMinDistanceMeters())
            .tripMinDurationMinutes(validConfig.getTripMinDurationMinutes())
            .isMergeEnabled(validConfig.getIsMergeEnabled())
            .mergeMaxDistanceMeters(validConfig.getMergeMaxDistanceMeters())
            .mergeMaxTimeGapMinutes(validConfig.getMergeMaxTimeGapMinutes())
            .tripDetectionAlgorithm(validConfig.getTripDetectionAlgorithm())
            .build();
        
        assertThrows(IllegalArgumentException.class, () -> 
            validationService.validateTimelineConfig(config));
    }

    @Test
    void testValidateTrackPoints_Valid() {
        List<TrackPoint> points = Arrays.asList(
            new TrackPoint(-122.4194, 37.7749, Instant.now(), 10.0, 5.0),
            new TrackPoint(-122.4195, 37.7750, Instant.now().plusSeconds(60), 15.0, 8.0)
        );
        
        assertDoesNotThrow(() -> validationService.validateTrackPoints(points));
    }

    @Test
    void testValidateTrackPoints_Null() {
        assertThrows(IllegalArgumentException.class, () -> 
            validationService.validateTrackPoints(null));
    }

    @Test
    void testValidateTrackPoints_Empty() {
        assertDoesNotThrow(() -> validationService.validateTrackPoints(List.of()));
    }

    @Test
    void testValidateTrackPoints_NullPoint() {
        List<TrackPoint> points = Arrays.asList(
            new TrackPoint(-122.4194, 37.7749, Instant.now(), 10.0, 5.0),
            null
        );
        
        assertThrows(IllegalArgumentException.class, () -> 
            validationService.validateTrackPoints(points));
    }

    @Test
    void testValidateTrackPoints_NullTimestamp() {
        List<TrackPoint> points = Arrays.asList(
            new TrackPoint(-122.4194, 37.7749, null, 10.0, 5.0)
        );
        
        assertThrows(IllegalArgumentException.class, () -> 
            validationService.validateTrackPoints(points));
    }

    @Test
    void testValidateTrackPoints_InvalidCoordinates() {
        List<TrackPoint> points = Arrays.asList(
            new TrackPoint(-122.4194, 91.0, Instant.now(), 10.0, 5.0) // Invalid latitude
        );
        
        assertThrows(IllegalArgumentException.class, () -> 
            validationService.validateTrackPoints(points));
    }

    @Test
    void testValidateUserId() {
        UUID validId = UUID.randomUUID();
        assertDoesNotThrow(() -> validationService.validateUserId(validId));
    }

    @Test
    void testValidateUserId_Null() {
        assertThrows(IllegalArgumentException.class, () -> 
            validationService.validateUserId(null));
    }

    @Test
    void testValidateTimeRange_Valid() {
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);
        
        assertDoesNotThrow(() -> validationService.validateTimeRange(start, end));
    }

    @Test
    void testValidateTimeRange_NullStart() {
        Instant end = Instant.now();
        
        assertThrows(IllegalArgumentException.class, () -> 
            validationService.validateTimeRange(null, end));
    }

    @Test
    void testValidateTimeRange_NullEnd() {
        Instant start = Instant.now();
        
        assertThrows(IllegalArgumentException.class, () -> 
            validationService.validateTimeRange(start, null));
    }

    @Test
    void testValidateTimeRange_StartAfterEnd() {
        Instant end = Instant.now();
        Instant start = end.plusSeconds(3600);
        
        assertThrows(IllegalArgumentException.class, () -> 
            validationService.validateTimeRange(start, end));
    }

    @Test
    void testValidateTimeRange_TooLong() {
        Instant start = Instant.now();
        Instant end = start.plusSeconds(366 * 24 * 3600); // More than 365 days
        
        assertThrows(IllegalArgumentException.class, () -> 
            validationService.validateTimeRange(start, end));
    }

    @Test
    void testPassesAccuracyAndVelocityChecks() {
        TrackPoint goodPoint = new TrackPoint(-122.4194, 37.7749, Instant.now(), 5.0, 2.0);
        TrackPoint badAccuracyPoint = new TrackPoint(-122.4194, 37.7749, Instant.now(), 100.0, 2.0);
        TrackPoint badVelocityPoint = new TrackPoint(-122.4194, 37.7749, Instant.now(), 5.0, 50.0);
        
        assertTrue(validationService.passesAccuracyAndVelocityChecks(goodPoint, validConfig));
        assertFalse(validationService.passesAccuracyAndVelocityChecks(badAccuracyPoint, validConfig));
        assertFalse(validationService.passesAccuracyAndVelocityChecks(badVelocityPoint, validConfig));
    }

    @Test
    void testPassesAccuracyAndVelocityChecks_VelocityDisabled() {
        TimelineConfig config = TimelineConfig.builder()
            .staypointDetectionAlgorithm(validConfig.getStaypointDetectionAlgorithm())
            .useVelocityAccuracy(false)
            .staypointVelocityThreshold(validConfig.getStaypointVelocityThreshold())
            .staypointMaxAccuracyThreshold(validConfig.getStaypointMaxAccuracyThreshold())
            .staypointMinAccuracyRatio(validConfig.getStaypointMinAccuracyRatio())
            .tripMinDistanceMeters(validConfig.getTripMinDistanceMeters())
            .tripMinDurationMinutes(validConfig.getTripMinDurationMinutes())
            .isMergeEnabled(validConfig.getIsMergeEnabled())
            .mergeMaxDistanceMeters(validConfig.getMergeMaxDistanceMeters())
            .mergeMaxTimeGapMinutes(validConfig.getMergeMaxTimeGapMinutes())
            .tripDetectionAlgorithm(validConfig.getTripDetectionAlgorithm())
            .build();
        
        TrackPoint badVelocityPoint = new TrackPoint(-122.4194, 37.7749, Instant.now(), 5.0, 50.0);
        
        assertTrue(validationService.passesAccuracyAndVelocityChecks(badVelocityPoint, config));
    }

    @Test
    void testIsValidCoordinate() {
        assertTrue(validationService.isValidCoordinate(37.7749, -122.4194));
        assertTrue(validationService.isValidCoordinate(90.0, 180.0));
        assertTrue(validationService.isValidCoordinate(-90.0, -180.0));
        
        assertFalse(validationService.isValidCoordinate(91.0, -122.4194));
        assertFalse(validationService.isValidCoordinate(37.7749, 181.0));
        assertFalse(validationService.isValidCoordinate(-91.0, -122.4194));
        assertFalse(validationService.isValidCoordinate(37.7749, -181.0));
    }

    @Test
    void testIsBetweenInclusive() {
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        Instant end = Instant.parse("2024-01-01T12:00:00Z");
        Instant middle = Instant.parse("2024-01-01T06:00:00Z");
        Instant before = Instant.parse("2023-12-31T23:00:00Z");
        Instant after = Instant.parse("2024-01-01T13:00:00Z");
        
        assertTrue(validationService.isBetweenInclusive(start, end, start));
        assertTrue(validationService.isBetweenInclusive(start, end, end));
        assertTrue(validationService.isBetweenInclusive(start, end, middle));
        assertFalse(validationService.isBetweenInclusive(start, end, before));
        assertFalse(validationService.isBetweenInclusive(start, end, after));
    }

    @Test
    void testIsBetweenInclusive_NullValues() {
        Instant time = Instant.now();
        
        assertFalse(validationService.isBetweenInclusive(null, time, time));
        assertFalse(validationService.isBetweenInclusive(time, null, time));
        assertFalse(validationService.isBetweenInclusive(time, time, null));
    }

    @Test
    void testValidateAlgorithmName() {
        List<String> allowedValues = Arrays.asList("original", "claude");
        
        assertDoesNotThrow(() -> 
            validationService.validateAlgorithmName("original", allowedValues));
        assertDoesNotThrow(() -> 
            validationService.validateAlgorithmName("claude", allowedValues));
    }

    @Test
    void testValidateAlgorithmName_Invalid() {
        List<String> allowedValues = Arrays.asList("original", "claude");
        
        assertThrows(IllegalArgumentException.class, () -> 
            validationService.validateAlgorithmName("unknown", allowedValues));
        assertThrows(IllegalArgumentException.class, () -> 
            validationService.validateAlgorithmName(null, allowedValues));
        assertThrows(IllegalArgumentException.class, () -> 
            validationService.validateAlgorithmName("", allowedValues));
    }

    @Test
    void testHasSufficientDataForProcessing() {
        List<TrackPoint> sufficientPoints = Arrays.asList(
            new TrackPoint(-122.4194, 37.7749, Instant.now(), 5.0, 2.0),
            new TrackPoint(-122.4195, 37.7750, Instant.now().plusSeconds(60), 5.0, 2.0),
            new TrackPoint(-122.4196, 37.7751, Instant.now().plusSeconds(120), 5.0, 2.0),
            new TrackPoint(-122.4197, 37.7752, Instant.now().plusSeconds(180), 5.0, 2.0),
            new TrackPoint(-122.4198, 37.7753, Instant.now().plusSeconds(240), 5.0, 2.0),
            new TrackPoint(-122.4199, 37.7754, Instant.now().plusSeconds(300), 5.0, 2.0),
            new TrackPoint(-122.4200, 37.7755, Instant.now().plusSeconds(360), 5.0, 2.0)
        );
        
        assertTrue(validationService.hasSufficientDataForProcessing(sufficientPoints, validConfig));
    }

    @Test
    void testHasSufficientDataForProcessing_InsufficientPoints() {
        List<TrackPoint> insufficientPoints = Arrays.asList(
            new TrackPoint(-122.4194, 37.7749, Instant.now(), 5.0, 2.0),
            new TrackPoint(-122.4195, 37.7750, Instant.now().plusSeconds(60), 5.0, 2.0)
        );
        
        assertFalse(validationService.hasSufficientDataForProcessing(insufficientPoints, validConfig));
    }

    @Test
    void testHasSufficientDataForProcessing_InsufficientAccuratePoints() {
        List<TrackPoint> inaccuratePoints = Arrays.asList(
            new TrackPoint(-122.4194, 37.7749, Instant.now(), 100.0, 2.0),
            new TrackPoint(-122.4195, 37.7750, Instant.now().plusSeconds(60), 100.0, 2.0),
            new TrackPoint(-122.4196, 37.7751, Instant.now().plusSeconds(120), 100.0, 2.0),
            new TrackPoint(-122.4197, 37.7752, Instant.now().plusSeconds(180), 100.0, 2.0),
            new TrackPoint(-122.4198, 37.7753, Instant.now().plusSeconds(240), 100.0, 2.0)
        );
        
        assertFalse(validationService.hasSufficientDataForProcessing(inaccuratePoints, validConfig));
    }

    @Test
    void testHasSufficientDataForProcessing_EmptyList() {
        assertFalse(validationService.hasSufficientDataForProcessing(List.of(), validConfig));
    }

    @Test
    void testHasSufficientDataForProcessing_NullList() {
        assertFalse(validationService.hasSufficientDataForProcessing(null, validConfig));
    }

    private TimelineConfig createValidConfig() {
        return TimelineConfig.builder()
            .staypointVelocityThreshold(20.0)
            .staypointMaxAccuracyThreshold(40.0)
            .staypointMinAccuracyRatio(0.7)
            .tripMinDistanceMeters(200)
            .tripMinDurationMinutes(25)
            .isMergeEnabled(true)
            .mergeMaxDistanceMeters(400)
            .mergeMaxTimeGapMinutes(35)
            .useVelocityAccuracy(true)
            .build();
    }
}
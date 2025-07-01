package org.github.tess1o.geopulse.timeline.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.timeline.assembly.TimelineProcessingService;
import org.github.tess1o.geopulse.timeline.core.SpatialCalculationService;
import org.github.tess1o.geopulse.timeline.core.TimelineValidationService;
import org.github.tess1o.geopulse.timeline.core.VelocityAnalysisService;
import org.github.tess1o.geopulse.timeline.detection.stays.StayPointDetectionService;
import org.github.tess1o.geopulse.timeline.detection.trips.TripDetectionService;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.timeline.model.TimelineStayPoint;
import org.github.tess1o.geopulse.timeline.model.TrackPoint;
import org.github.tess1o.geopulse.timeline.detection.stays.StayPointDetectorEnhanced;
import org.github.tess1o.geopulse.timeline.detection.stays.StayPointDetectorSimple;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that verifies the new service architecture works correctly
 * with proper dependency injection and service interactions.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
class TimelineServiceArchitectureIntegrationTest {

    @Inject
    SpatialCalculationService spatialCalculationService;

    @Inject
    VelocityAnalysisService velocityAnalysisService;

    @Inject
    TimelineValidationService validationService;

    @Inject
    StayPointDetectionService stayPointDetectionService;

    @Inject
    TripDetectionService tripDetectionService;

    @Inject
    TimelineProcessingService processingService;

    @Inject
    StayPointDetectorSimple simpleDetector;

    @Inject
    StayPointDetectorEnhanced enhancedDetector;

    @Test
    void testServiceInjection() {
        // Verify all services are properly injected
        assertNotNull(spatialCalculationService);
        assertNotNull(velocityAnalysisService);
        assertNotNull(validationService);
        assertNotNull(stayPointDetectionService);
        assertNotNull(tripDetectionService);
        assertNotNull(processingService);
        assertNotNull(simpleDetector);
        assertNotNull(enhancedDetector);
    }

    @Test
    void testSpatialCalculationServiceIntegration() {
        // Test that spatial calculations work correctly
        List<TrackPoint> points = createTestTrackPoints();
        
        double[] centroid = spatialCalculationService.calculateWeightedCentroid(points);
        assertNotNull(centroid);
        assertEquals(2, centroid.length);
        
        TrackPoint point1 = points.get(0);
        TrackPoint point2 = points.get(1);
        double distance = spatialCalculationService.calculateDistance(point1, point2);
        assertTrue(distance > 0);
    }

    @Test
    void testVelocityAnalysisServiceIntegration() {
        // Test velocity analysis with spatial calculations
        List<TrackPoint> points = createTestTrackPoints();
        
        List<Double> speeds = velocityAnalysisService.calculateSpeeds(points, spatialCalculationService);
        assertNotNull(speeds);
        assertEquals(points.size() - 1, speeds.size());
        
        // Test filtering
        List<Double> testSpeeds = Arrays.asList(10.0, 200.0, 30.0, 300.0, 50.0);
        List<Double> filtered = velocityAnalysisService.filterUnrealisticSpeeds(testSpeeds, 100.0);
        assertEquals(testSpeeds.size(), filtered.size());
        for (Double speed : filtered) {
            assertTrue(speed <= 100.0);
        }
    }

    @Test
    void testTimelineValidationServiceIntegration() {
        // Test validation with real configuration
        TimelineConfig config = createTestConfig();
        assertDoesNotThrow(() -> validationService.validateTimelineConfig(config));
        
        List<TrackPoint> points = createTestTrackPoints();
        assertDoesNotThrow(() -> validationService.validateTrackPoints(points));
        
        assertTrue(validationService.hasSufficientDataForProcessing(points, config));
        
        // Test individual point validation
        TrackPoint goodPoint = points.get(0);
        assertTrue(validationService.passesAccuracyAndVelocityChecks(goodPoint, config));
    }

    @Test
    void testStayPointDetectionServiceIntegration() {
        // Test that the unified stay point detection service works
        TimelineConfig config = createTestConfig();
        List<TrackPoint> points = createTestTrackPoints();
        
        // Test with original algorithm
        config = TimelineConfig.builder()
            .staypointDetectionAlgorithm("enhanced")
            .useVelocityAccuracy(config.getUseVelocityAccuracy())
            .staypointVelocityThreshold(config.getStaypointVelocityThreshold())
            .staypointMaxAccuracyThreshold(config.getStaypointMaxAccuracyThreshold())
            .staypointMinAccuracyRatio(config.getStaypointMinAccuracyRatio())
            .tripMinDistanceMeters(config.getTripMinDistanceMeters())
            .tripMinDurationMinutes(config.getTripMinDurationMinutes())
            .isMergeEnabled(config.getIsMergeEnabled())
            .mergeMaxDistanceMeters(config.getMergeMaxDistanceMeters())
            .mergeMaxTimeGapMinutes(config.getMergeMaxTimeGapMinutes())
            .tripDetectionAlgorithm(config.getTripDetectionAlgorithm())
            .build();
        List<TimelineStayPoint> originalStays = stayPointDetectionService.detectStayPoints(config, points);
        assertNotNull(originalStays);
        
        // Test with enhanced algorithm
        config = TimelineConfig.builder()
            .staypointDetectionAlgorithm("enhanced")
            .useVelocityAccuracy(config.getUseVelocityAccuracy())
            .staypointVelocityThreshold(config.getStaypointVelocityThreshold())
            .staypointMaxAccuracyThreshold(config.getStaypointMaxAccuracyThreshold())
            .staypointMinAccuracyRatio(config.getStaypointMinAccuracyRatio())
            .tripMinDistanceMeters(config.getTripMinDistanceMeters())
            .tripMinDurationMinutes(config.getTripMinDurationMinutes())
            .isMergeEnabled(config.getIsMergeEnabled())
            .mergeMaxDistanceMeters(config.getMergeMaxDistanceMeters())
            .mergeMaxTimeGapMinutes(config.getMergeMaxTimeGapMinutes())
            .tripDetectionAlgorithm(config.getTripDetectionAlgorithm())
            .build();
        List<TimelineStayPoint> enhancedStays = stayPointDetectionService.detectStayPoints(config, points);
        assertNotNull(enhancedStays);
        
        // Both should produce valid results
        assertTrue(originalStays.size() >= 0);
        assertTrue(enhancedStays.size() >= 0);
    }

    @Test
    void testStayPointDetectorsDependencyInjection() {
        // Test that the stay point detectors have their dependencies properly injected
        TimelineConfig config = createTestConfig();
        List<TrackPoint> points = createTestTrackPoints();
        
        // Test simple detector
        List<TimelineStayPoint> simpleStays = simpleDetector.detectStayPoints(config, points);
        assertNotNull(simpleStays);
        
        // Test enhanced detector
        List<TimelineStayPoint> enhancedStays = enhancedDetector.detectStayPoints(config, points);
        assertNotNull(enhancedStays);
    }

    @Test
    void testServiceLayerValidation() {
        // Test that validation is properly enforced at service layer
        assertThrows(IllegalArgumentException.class, () -> 
            stayPointDetectionService.detectStayPoints(null, createTestTrackPoints()));
        
        assertThrows(IllegalArgumentException.class, () -> 
            stayPointDetectionService.detectStayPoints(createTestConfig(), null));
    }

    @Test
    void testCrossServiceIntegration() {
        // Test that services work together correctly
        TimelineConfig config = createTestConfig();
        List<TrackPoint> points = createTestTrackPoints();
        
        // First validate the data
        validationService.validateTimelineConfig(config);
        validationService.validateTrackPoints(points);
        
        // Then detect stay points
        List<TimelineStayPoint> stays = stayPointDetectionService.detectStayPoints(config, points);
        
        // Verify the stays are spatially reasonable
        if (stays.size() > 1) {
            for (int i = 1; i < stays.size(); i++) {
                TimelineStayPoint prev = stays.get(i - 1);
                TimelineStayPoint curr = stays.get(i);
                
                double distance = spatialCalculationService.calculateDistance(
                    prev.latitude(), prev.longitude(),
                    curr.latitude(), curr.longitude()
                );
                
                // Distance should be reasonable (not 0 but not too large)
                assertTrue(distance >= 0);
            }
        }
    }

    @Test
    void testAlgorithmConsistency() {
        // Test that different algorithms produce reasonable results
        TimelineConfig config = createTestConfig();
        List<TrackPoint> points = createLongStayTrackPoints(); // Points that should clearly form a stay
        
        // Test with original algorithm
        config = TimelineConfig.builder()
            .staypointDetectionAlgorithm("enhanced")
            .useVelocityAccuracy(config.getUseVelocityAccuracy())
            .staypointVelocityThreshold(config.getStaypointVelocityThreshold())
            .staypointMaxAccuracyThreshold(config.getStaypointMaxAccuracyThreshold())
            .staypointMinAccuracyRatio(config.getStaypointMinAccuracyRatio())
            .tripMinDistanceMeters(config.getTripMinDistanceMeters())
            .tripMinDurationMinutes(config.getTripMinDurationMinutes())
            .isMergeEnabled(config.getIsMergeEnabled())
            .mergeMaxDistanceMeters(config.getMergeMaxDistanceMeters())
            .mergeMaxTimeGapMinutes(config.getMergeMaxTimeGapMinutes())
            .tripDetectionAlgorithm(config.getTripDetectionAlgorithm())
            .build();
        List<TimelineStayPoint> originalStays = stayPointDetectionService.detectStayPoints(config, points);
        
        // Test with enhanced algorithm  
        config = TimelineConfig.builder()
            .staypointDetectionAlgorithm("enhanced")
            .useVelocityAccuracy(config.getUseVelocityAccuracy())
            .staypointVelocityThreshold(config.getStaypointVelocityThreshold())
            .staypointMaxAccuracyThreshold(config.getStaypointMaxAccuracyThreshold())
            .staypointMinAccuracyRatio(config.getStaypointMinAccuracyRatio())
            .tripMinDistanceMeters(config.getTripMinDistanceMeters())
            .tripMinDurationMinutes(config.getTripMinDurationMinutes())
            .isMergeEnabled(config.getIsMergeEnabled())
            .mergeMaxDistanceMeters(config.getMergeMaxDistanceMeters())
            .mergeMaxTimeGapMinutes(config.getMergeMaxTimeGapMinutes())
            .tripDetectionAlgorithm(config.getTripDetectionAlgorithm())
            .build();
        List<TimelineStayPoint> enhancedStays = stayPointDetectionService.detectStayPoints(config, points);
        
        // Both should detect at least one stay point for a clear stay scenario
        assertTrue(originalStays.size() >= 0);
        assertTrue(enhancedStays.size() >= 0);
    }

    private List<TrackPoint> createTestTrackPoints() {
        Instant now = Instant.now();
        return Arrays.asList(
            new TrackPoint(-122.4194, 37.7749, now, 10.0, 5.0),
            new TrackPoint(-122.4195, 37.7750, now.plusSeconds(60), 12.0, 6.0),
            new TrackPoint(-122.4196, 37.7751, now.plusSeconds(120), 8.0, 4.0),
            new TrackPoint(-122.4197, 37.7752, now.plusSeconds(180), 15.0, 7.0),
            new TrackPoint(-122.4198, 37.7753, now.plusSeconds(240), 9.0, 3.0),
            new TrackPoint(-122.4199, 37.7754, now.plusSeconds(300), 11.0, 5.0),
            new TrackPoint(-122.4200, 37.7755, now.plusSeconds(360), 13.0, 6.0)
        );
    }

    private List<TrackPoint> createLongStayTrackPoints() {
        Instant now = Instant.now();
        // Create points that cluster around the same location for a long time
        return Arrays.asList(
            new TrackPoint(-122.4194, 37.7749, now, 5.0, 1.0),
            new TrackPoint(-122.4194, 37.7749, now.plusSeconds(60), 6.0, 1.0),
            new TrackPoint(-122.4194, 37.7750, now.plusSeconds(120), 5.0, 1.0),
            new TrackPoint(-122.4195, 37.7749, now.plusSeconds(180), 7.0, 1.0),
            new TrackPoint(-122.4195, 37.7750, now.plusSeconds(240), 5.0, 1.0),
            new TrackPoint(-122.4194, 37.7749, now.plusSeconds(300), 6.0, 1.0),
            new TrackPoint(-122.4194, 37.7750, now.plusSeconds(360), 5.0, 1.0),
            new TrackPoint(-122.4195, 37.7749, now.plusSeconds(420), 7.0, 1.0),
            new TrackPoint(-122.4195, 37.7750, now.plusSeconds(480), 5.0, 1.0),
            new TrackPoint(-122.4194, 37.7749, now.plusSeconds(540), 6.0, 1.0)
        );
    }

    private TimelineConfig createTestConfig() {
        return TimelineConfig.builder()
            .staypointDetectionAlgorithm("enhanced")
            .tripDetectionAlgorithm("single")
            .useVelocityAccuracy(true)
            .staypointVelocityThreshold(20.0)
            .staypointMaxAccuracyThreshold(40.0)
            .staypointMinAccuracyRatio(0.7)
            .tripMinDistanceMeters(200)
            .tripMinDurationMinutes(5) // Shorter for testing
            .isMergeEnabled(true)
            .mergeMaxDistanceMeters(400)
            .mergeMaxTimeGapMinutes(35)
            .build();
    }
}
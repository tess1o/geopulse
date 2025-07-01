package org.github.tess1o.geopulse.timeline.service;

import org.github.tess1o.geopulse.timeline.core.SpatialCalculationService;
import org.github.tess1o.geopulse.timeline.model.TrackPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpatialCalculationServiceTest {

    private SpatialCalculationService spatialCalculationService;

    @BeforeEach
    void setUp() {
        spatialCalculationService = new SpatialCalculationService();
    }

    @Test
    void testCalculateWeightedCentroid() {
        // Create test points with different accuracies (longitude, latitude, timestamp, accuracy, velocity)
        List<TrackPoint> points = Arrays.asList(
            new TrackPoint(-122.4194, 37.7749, Instant.now(), 5.0, null), // High accuracy (low value)
            new TrackPoint(-122.4195, 37.7750, Instant.now(), 20.0, null), // Lower accuracy
            new TrackPoint(-122.4196, 37.7751, Instant.now(), 5.0, null)  // High accuracy
        );

        double[] centroid = spatialCalculationService.calculateWeightedCentroid(points);

        // Should be weighted toward high accuracy points
        assertNotNull(centroid);
        assertEquals(2, centroid.length);
        assertTrue(centroid[0] > 37.774); // Latitude
        assertTrue(centroid[1] < -122.419); // Longitude
    }

    @Test
    void testCalculateWeightedCentroid_EmptyList() {
        assertThrows(IllegalArgumentException.class, () -> 
            spatialCalculationService.calculateWeightedCentroid(List.of()));
    }

    @Test
    void testCalculateWeightedCentroid_NullList() {
        assertThrows(IllegalArgumentException.class, () -> 
            spatialCalculationService.calculateWeightedCentroid(null));
    }

    @Test
    void testCalculateDistance() {
        TrackPoint point1 = new TrackPoint(-122.4194, 37.7749, Instant.now(), null, null);
        TrackPoint point2 = new TrackPoint(-122.4294, 37.7849, Instant.now(), null, null);

        double distance = spatialCalculationService.calculateDistance(point1, point2);

        assertTrue(distance > 0);
        assertTrue(distance < 2000); // Should be less than 2km for this small delta
    }

    @Test
    void testCalculateDistance_Coordinates() {
        double distance = spatialCalculationService.calculateDistance(
            37.7749, -122.4194, 37.7849, -122.4294);

        assertTrue(distance > 0);
        assertTrue(distance < 2000);
    }

    @Test
    void testCalculateDistance_SamePoints() {
        TrackPoint point = new TrackPoint(-122.4194, 37.7749, Instant.now(), null, null);

        double distance = spatialCalculationService.calculateDistance(point, point);

        assertEquals(0.0, distance, 0.001);
    }

    @Test
    void testCalculateDistance_NullPoints() {
        TrackPoint point = new TrackPoint(-122.4194, 37.7749, Instant.now(), null, null);

        assertThrows(IllegalArgumentException.class, () -> 
            spatialCalculationService.calculateDistance(null, point));
        
        assertThrows(IllegalArgumentException.class, () -> 
            spatialCalculationService.calculateDistance(point, null));
    }

    @Test
    void testArePointsWithinDistance() {
        TrackPoint point1 = new TrackPoint(-122.4194, 37.7749, Instant.now(), null, null);
        TrackPoint point2 = new TrackPoint(-122.4195, 37.7750, Instant.now(), null, null);

        // Distance between these points is approximately 14.17 meters
        assertTrue(spatialCalculationService.arePointsWithinDistance(point1, point2, 20.0));
        assertFalse(spatialCalculationService.arePointsWithinDistance(point1, point2, 10.0));
    }

    @Test
    void testArePointsWithinDistance_NullPoints() {
        TrackPoint point = new TrackPoint(-122.4194, 37.7749, Instant.now(), null, null);

        assertFalse(spatialCalculationService.arePointsWithinDistance(null, point, 100.0));
        assertFalse(spatialCalculationService.arePointsWithinDistance(point, null, 100.0));
    }

    @Test
    void testAreCoordinatesWithinDistance() {
        assertTrue(spatialCalculationService.areCoordinatesWithinDistance(
            37.7749, -122.4194, 37.7750, -122.4195, 200.0));
        
        assertFalse(spatialCalculationService.areCoordinatesWithinDistance(
            37.7749, -122.4194, 37.7850, -122.4295, 100.0));
    }

    @Test
    void testCalculateCenterPoint() {
        List<TrackPoint> points = Arrays.asList(
            new TrackPoint(-122.4194, 37.7749, Instant.now(), null, null),
            new TrackPoint(-122.4195, 37.7750, Instant.now(), null, null),
            new TrackPoint(-122.4196, 37.7751, Instant.now(), null, null)
        );

        double[] center = spatialCalculationService.calculateCenterPoint(points);

        assertNotNull(center);
        assertEquals(2, center.length);
        assertEquals(37.775, center[0], 0.001); // Average latitude
        assertEquals(-122.4195, center[1], 0.001); // Average longitude
    }

    @Test
    void testCalculateCenterPoint_EmptyList() {
        assertThrows(IllegalArgumentException.class, () -> 
            spatialCalculationService.calculateCenterPoint(List.of()));
    }

    @Test
    void testCalculateCenterPoint_NullList() {
        assertThrows(IllegalArgumentException.class, () -> 
            spatialCalculationService.calculateCenterPoint(null));
    }
}
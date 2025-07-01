package org.github.tess1o.geopulse.timeline.service;

import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.timeline.core.SpatialCalculationService;
import org.github.tess1o.geopulse.timeline.core.VelocityAnalysisService;
import org.github.tess1o.geopulse.timeline.model.TrackPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VelocityAnalysisServiceTest {

    private VelocityAnalysisService velocityAnalysisService;
    private SpatialCalculationService spatialCalculationService;

    @BeforeEach
    void setUp() {
        velocityAnalysisService = new VelocityAnalysisService();
        spatialCalculationService = new SpatialCalculationService();
    }

    @Test
    void testAnalyzeVelocityWindow() {
        List<GpsPointPathPointDTO> points = Arrays.asList(
            createGpsPoint(10.0), // 10 km/h
            createGpsPoint(20.0), // 20 km/h
            createGpsPoint(30.0), // 30 km/h
            createGpsPoint(40.0), // 40 km/h
            createGpsPoint(50.0)  // 50 km/h
        );

        VelocityAnalysisService.VelocityWindow window = velocityAnalysisService.analyzeVelocityWindow(points, 0);

        assertNotNull(window);
        assertEquals(0, window.startIndex());
        assertEquals(30.0, window.median(), 0.1); // Middle value
        assertEquals(50.0, window.max(), 0.1);    // Maximum value
        assertEquals(30.0, window.average(), 0.1); // Average
        assertTrue(window.p95() >= 40.0); // 95th percentile
    }

    @Test
    void testAnalyzeVelocityWindow_EmptyList() {
        VelocityAnalysisService.VelocityWindow window = velocityAnalysisService.analyzeVelocityWindow(List.of(), 0);

        assertNotNull(window);
        assertEquals(0, window.startIndex());
        assertEquals(0.0, window.median());
        assertEquals(0.0, window.max());
        assertEquals(0.0, window.average());
        assertEquals(0.0, window.p95());
    }

    @Test
    void testAnalyzeVelocityWindow_NullVelocities() {
        List<GpsPointPathPointDTO> points = Arrays.asList(
            createGpsPoint(null),
            createGpsPoint(null)
        );

        VelocityAnalysisService.VelocityWindow window = velocityAnalysisService.analyzeVelocityWindow(points, 0);

        assertNotNull(window);
        assertEquals(0.0, window.median());
        assertEquals(0.0, window.max());
    }

    @Test
    void testApplyMovingAverage() {
        List<Double> speeds = Arrays.asList(10.0, 20.0, 30.0, 40.0, 50.0);

        List<Double> smoothed = velocityAnalysisService.applyMovingAverage(speeds, 3);

        assertNotNull(smoothed);
        assertEquals(speeds.size(), smoothed.size());
        
        // First value should be average of first 3 values (considering window positioning)
        assertTrue(smoothed.getFirst() > 10.0);
        assertTrue(smoothed.getLast() < 50.0);
    }

    @Test
    void testApplyMovingAverage_EmptyList() {
        List<Double> result = velocityAnalysisService.applyMovingAverage(List.of(), 3);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testApplyMovingAverage_NullList() {
        List<Double> result = velocityAnalysisService.applyMovingAverage(null, 3);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCalculateInstantSpeed() {
        Instant now = Instant.now();
        TrackPoint point1 = new TrackPoint(-122.4194, 37.7749, now, null, null);
        TrackPoint point2 = new TrackPoint(-122.4204, 37.7759, now.plusSeconds(60), null, null);

        double speed = velocityAnalysisService.calculateInstantSpeed(point1, point2, spatialCalculationService);

        assertTrue(speed > 0);
        assertTrue(speed < 100); // Should be reasonable speed in km/h
    }

    @Test
    void testCalculateInstantSpeed_SameTime() {
        Instant now = Instant.now();
        TrackPoint point1 = new TrackPoint(-122.4194, 37.7749, now, null, null);
        TrackPoint point2 = new TrackPoint(-122.4204, 37.7759, now, null, null);

        double speed = velocityAnalysisService.calculateInstantSpeed(point1, point2, spatialCalculationService);

        assertEquals(0.0, speed);
    }

    @Test
    void testCalculateInstantSpeed_NullPoints() {
        TrackPoint point = new TrackPoint(-122.4194, 37.7749, Instant.now(), null, null);

        assertEquals(0.0, velocityAnalysisService.calculateInstantSpeed(null, point, spatialCalculationService));
        assertEquals(0.0, velocityAnalysisService.calculateInstantSpeed(point, null, spatialCalculationService));
    }

    @Test
    void testCalculateSpeeds() {
        Instant now = Instant.now();
        List<TrackPoint> points = Arrays.asList(
            new TrackPoint(-122.4194, 37.7749, now, null, null),
            new TrackPoint(-122.4204, 37.7759, now.plusSeconds(60), null, null),
            new TrackPoint(-122.4214, 37.7769, now.plusSeconds(120), null, null)
        );

        List<Double> speeds = velocityAnalysisService.calculateSpeeds(points, spatialCalculationService);

        assertEquals(2, speeds.size()); // n-1 speeds for n points
        for (Double speed : speeds) {
            assertTrue(speed >= 0);
        }
    }

    @Test
    void testCalculateSpeeds_InsufficientPoints() {
        List<TrackPoint> points = List.of(
                new TrackPoint(-122.4194, 37.7749, Instant.now(), null, null)
        );

        List<Double> speeds = velocityAnalysisService.calculateSpeeds(points, spatialCalculationService);

        assertTrue(speeds.isEmpty());
    }

    @Test
    void testIsSpeedReasonable() {
        assertTrue(velocityAnalysisService.isSpeedReasonable(50.0, 100.0));
        assertFalse(velocityAnalysisService.isSpeedReasonable(150.0, 100.0));
        assertFalse(velocityAnalysisService.isSpeedReasonable(-10.0, 100.0));
    }

    @Test
    void testIsSuspiciousSpeed() {
        assertFalse(velocityAnalysisService.isSuspiciousSpeed(50.0, 100.0));
        assertTrue(velocityAnalysisService.isSuspiciousSpeed(150.0, 100.0));
    }

    @Test
    void testFilterUnrealisticSpeeds() {
        List<Double> speeds = Arrays.asList(10.0, 200.0, 30.0, 300.0, 50.0);

        List<Double> filtered = velocityAnalysisService.filterUnrealisticSpeeds(speeds, 100.0);

        assertEquals(speeds.size(), filtered.size());
        for (Double speed : filtered) {
            assertTrue(speed <= 100.0);
            assertTrue(speed >= 0.0);
        }
    }

    @Test
    void testFilterUnrealisticSpeeds_EmptyList() {
        List<Double> result = velocityAnalysisService.filterUnrealisticSpeeds(List.of(), 100.0);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFilterUnrealisticSpeeds_NullList() {
        List<Double> result = velocityAnalysisService.filterUnrealisticSpeeds(null, 100.0);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    private GpsPointPathPointDTO createGpsPoint(Double velocity) {
        GpsPointPathPointDTO point = new GpsPointPathPointDTO();
        point.setVelocity(velocity);
        point.setTimestamp(Instant.now());
        point.setLatitude(37.7749);
        point.setLongitude(-122.4194);
        return point;
    }
}
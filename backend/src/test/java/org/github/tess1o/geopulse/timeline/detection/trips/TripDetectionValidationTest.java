package org.github.tess1o.geopulse.timeline.detection.trips;

import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.timeline.core.SpatialCalculationService;
import org.github.tess1o.geopulse.timeline.core.TimelineValidationService;
import org.github.tess1o.geopulse.timeline.core.VelocityAnalysisService;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.timeline.model.TimelineStayPoint;
import org.github.tess1o.geopulse.timeline.model.TimelineTrip;
import org.github.tess1o.geopulse.timeline.model.TravelMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to reproduce and verify fixes for 0-duration, 0-distance trip detection issues.
 */
public class TripDetectionValidationTest {

    private TimelineTripsDetectorSingle singleDetector;
    private TimelineTripsDetectorMulti multiDetector;
    private TravelClassification travelClassification;
    private TimelineConfig config;

    private static GpsPointPathPointDTO createTestGpsPoint(double longitude, double latitude, Instant timestamp, Double velocity, Double accuracy) {
        return new GpsPointPathPointDTO(
            0L, // id
            longitude,
            latitude, 
            timestamp,
            accuracy,
            null, // altitude
            velocity,
            null, // userId - not needed for test
            "test" // sourceType
        );
    }

    @BeforeEach
    void setUp() {
        SpatialCalculationService spatialService = new SpatialCalculationService();
        VelocityAnalysisService velocityService = new VelocityAnalysisService();
        TimelineValidationService validationService = new TimelineValidationService();
        travelClassification = new TravelClassification(spatialService, velocityService);
        
        singleDetector = new TimelineTripsDetectorSingle(travelClassification, spatialService, validationService);
        multiDetector = new TimelineTripsDetectorMulti(travelClassification, velocityService, validationService, spatialService);

        // Configuration with reasonable minimum thresholds matching user setup
        config = new TimelineConfig();
        config.setTripMinDurationMinutes(5);  // 5 minutes minimum
        config.setTripMinDistanceMeters(25);  // 25 meters minimum (matching user)
        config.setStaypointVelocityThreshold(1.5); // 1.5 km/h (matching user)
        config.setStaypointMaxAccuracyThreshold(50.0); // 50m accuracy threshold
        config.setUseVelocityAccuracy(true);
        config.setTripDetectionAlgorithm("single");
    }

    @Test
    void testSingleDetector_ShouldNotCreateZeroDurationTrip_AfterFix() {
        // Test data based on user's example - adjacent stay points with same timestamp
        Instant baseTime = Instant.parse("2025-07-02T05:24:22Z");
        
        // Create stay points that are adjacent (same end/start time)
        TimelineStayPoint stay1 = new TimelineStayPoint(
            25.595883, 49.547089, 
            baseTime, baseTime.plusSeconds(300), // 5 minute stay
            Duration.ofMinutes(5)
        );
        
        TimelineStayPoint stay2 = new TimelineStayPoint(
            25.591462, 49.555561,
            baseTime.plusSeconds(300), baseTime.plusSeconds(600), // Adjacent start time
            Duration.ofMinutes(5)
        );

        // GPS points that would create a 0-duration trip
        List<GpsPointPathPointDTO> gpsPoints = List.of(
            createTestGpsPoint(25.595883, 49.547089, baseTime.plusSeconds(300), 0.0, 18.0),
            createTestGpsPoint(25.591462, 49.555561, baseTime.plusSeconds(300), 0.0, 16.0) // Same timestamp!
        );

        List<TimelineStayPoint> stayPoints = List.of(stay1, stay2);

        // This should create a 0-duration trip with current implementation
        List<TimelineTrip> trips = singleDetector.detectTrips(config, gpsPoints, stayPoints);

        // Debug output to see what's happening
        System.out.println("Number of trips detected: " + trips.size());
        for (int i = 0; i < trips.size(); i++) {
            TimelineTrip trip = trips.get(i);
            System.out.println("Trip " + i + ": duration=" + trip.getDuration().toMinutes() + " min, pathSize=" + trip.path().size());
            System.out.println("  Start: " + trip.startTime() + ", End: " + trip.endTime());
        }

        // After fix: should NOT create 0-duration trips
        assertTrue(trips.isEmpty(), "Fixed implementation should not create 0-duration trips");
    }

    @Test 
    void testSingleDetector_ShouldNotCreateZeroDistanceTrip_AfterFix() {
        // Test case: GPS points at same location creating 0-distance trip
        Instant baseTime = Instant.parse("2025-07-02T05:24:22Z");
        
        TimelineStayPoint stay1 = new TimelineStayPoint(
            25.595883, 49.547089, 
            baseTime, baseTime.plusSeconds(300),
            Duration.ofMinutes(5)
        );
        
        TimelineStayPoint stay2 = new TimelineStayPoint(
            25.595883, 49.547089, // Same location as stay1
            baseTime.plusSeconds(600), baseTime.plusSeconds(900),
            Duration.ofMinutes(5)
        );

        // GPS points at exactly the same location
        List<GpsPointPathPointDTO> gpsPoints = List.of(
            createTestGpsPoint(25.595883, 49.547089, baseTime.plusSeconds(300), 0.0, 18.0),
            createTestGpsPoint(25.595883, 49.547089, baseTime.plusSeconds(450), 0.0, 16.0),
            createTestGpsPoint(25.595883, 49.547089, baseTime.plusSeconds(600), 0.0, 14.0)
        );

        List<TimelineStayPoint> stayPoints = List.of(stay1, stay2);

        List<TimelineTrip> trips = singleDetector.detectTrips(config, gpsPoints, stayPoints);

        // With 'trust stay points' approach: should create trip but classify as UNKNOWN due to 0-distance
        assertFalse(trips.isEmpty(), "Should create trip between consecutive stay points");
        assertEquals(1, trips.size());
        assertEquals(TravelMode.UNKNOWN, trips.get(0).travelMode(), "Zero-distance trip should be classified as UNKNOWN");
    }

    @Test
    void testMultiDetector_ShouldNotCreateInvalidTrips_AfterFix() {
        // Test multi-detector with insufficient data
        Instant baseTime = Instant.parse("2025-07-02T05:24:22Z");
        
        TimelineStayPoint stay1 = new TimelineStayPoint(
            25.595883, 49.547089, 
            baseTime, baseTime.plusSeconds(60), // Very short stay
            Duration.ofMinutes(1)
        );
        
        TimelineStayPoint stay2 = new TimelineStayPoint(
            25.595884, 49.547090, // Almost same location
            baseTime.plusSeconds(120), baseTime.plusSeconds(180),
            Duration.ofMinutes(1)
        );

        // Single GPS point in trip segment (insufficient for meaningful analysis)
        List<GpsPointPathPointDTO> gpsPoints = List.of(
            createTestGpsPoint(25.595883, 49.547089, baseTime.plusSeconds(60), 0.0, 18.0),
            createTestGpsPoint(25.595884, 49.547090, baseTime.plusSeconds(90), 0.0, 16.0), // Only point in trip
            createTestGpsPoint(25.595884, 49.547090, baseTime.plusSeconds(120), 0.0, 14.0)
        );

        List<TimelineStayPoint> stayPoints = List.of(stay1, stay2);

        List<TimelineTrip> trips = multiDetector.detectTrips(config, gpsPoints, stayPoints);

        // With 'trust stay points' approach: should create trip but may classify as UNKNOWN due to poor data
        assertFalse(trips.isEmpty(), "Should create trip between consecutive stay points");
        assertTrue(trips.size() >= 1, "Should have at least one trip between stays");
        // Trip may be classified as UNKNOWN due to insufficient data quality
    }

    @Test
    void testShouldFilterValidTrips_AfterFix() {
        // This test verifies the expected behavior AFTER implementing fixes
        Instant baseTime = Instant.parse("2025-07-02T05:24:22Z");
        
        // Create stay points with proper time gaps and distance
        TimelineStayPoint stay1 = new TimelineStayPoint(
            25.595883, 49.547089, 
            baseTime, baseTime.plusSeconds(300),
            Duration.ofMinutes(5)
        );
        
        TimelineStayPoint stay2 = new TimelineStayPoint(
            25.601234, 49.551234, // ~500m away
            baseTime.plusSeconds(600), baseTime.plusSeconds(900), // 5 minute gap
            Duration.ofMinutes(5)
        );

        // GPS points based on real Ковчег → Дім case: stationary then moving then stationary
        List<GpsPointPathPointDTO> gpsPoints = List.of(
            createTestGpsPoint(25.595883, 49.547089, baseTime.plusSeconds(180), 0.0, 13.0),  // stationary
            createTestGpsPoint(25.595883, 49.547089, baseTime.plusSeconds(240), 0.0, 15.0),  // stationary  
            createTestGpsPoint(25.595883, 49.547089, baseTime.plusSeconds(300), 0.0, 12.0),  // end stay1
            createTestGpsPoint(25.596500, 49.548000, baseTime.plusSeconds(360), 2.5, 14.0),  // moving > 1.5 km/h
            createTestGpsPoint(25.598000, 49.549500, baseTime.plusSeconds(420), 3.0, 16.0),  // moving > 1.5 km/h
            createTestGpsPoint(25.600500, 49.550800, baseTime.plusSeconds(480), 3.5, 18.0),  // moving > 1.5 km/h
            createTestGpsPoint(25.601234, 49.551234, baseTime.plusSeconds(540), 1.0, 17.0),  // slowing down
            createTestGpsPoint(25.601234, 49.551234, baseTime.plusSeconds(600), 0.0, 14.0),  // start stay2
            createTestGpsPoint(25.601234, 49.551234, baseTime.plusSeconds(660), 0.0, 13.0)   // stationary
        );

        List<TimelineStayPoint> stayPoints = List.of(stay1, stay2);

        List<TimelineTrip> trips = singleDetector.detectTrips(config, gpsPoints, stayPoints);

        // After fix: should create valid trips that meet minimum criteria
        assertFalse(trips.isEmpty(), "Should create valid trips that meet minimum distance and duration");
        
        TimelineTrip trip = trips.get(0);
        assertTrue(trip.getDuration().toMinutes() >= config.getTripMinDurationMinutes(), 
                  "Trip duration should meet minimum threshold");
        assertFalse(trip.path().isEmpty(), "Trip should have GPS points");
        assertTrue(trip.path().size() >= 2, "Trip should have at least 2 GPS points for distance calculation");
    }
}
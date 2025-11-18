package org.github.tess1o.geopulse.service;

import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.service.trips.GpsStatisticsCalculator;
import org.github.tess1o.geopulse.streaming.service.trips.TravelClassification;
import org.github.tess1o.geopulse.streaming.service.trips.TripGpsStatistics;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TimelineTripClassificationTest {

    private static final TravelClassification classification = new TravelClassification();
    private static final GpsStatisticsCalculator gpsStatisticsCalculator = new GpsStatisticsCalculator();

    private static final TimelineConfig config = TimelineConfig.builder()
            .staypointRadiusMeters(200)
            .carMinAvgSpeed(10.0)
            .carMinMaxSpeed(15.0)
            .walkingMaxAvgSpeed(6.0)
            .walkingMaxMaxSpeed(8.0)
            .shortDistanceKm(1.0)
            // Optional types disabled by default
            .bicycleEnabled(false)
            .trainEnabled(false)
            .flightEnabled(false)
            .build();

    // Helper to create LocationPoint with relative seconds offset
    private GPSPoint point(double lon, double lat, long secondsOffset, double speed) {
        return new GPSPoint(lat, lon, speed, 0, Instant.parse("2025-01-01T00:00:00Z").plusSeconds(secondsOffset));
    }

    @Test
    void testWalkingClassification() {
        List<GPSPoint> path = List.of(
                point(0.0, 0.0, 0, 0),             // start
                point(0.0, 0.00030, 30, 1.1),        // ~33 m (4 km/h)
                point(0.0, 0.00068, 60, 1.38),        // ~42 m (5 km/h)
                point(0.0, 0.00110, 90, 1.45),        // ~47 m (5.6 km/h)
                point(0.0, 0.00145, 120, 1.29),       // ~39 m (4.6 km/h)
                point(0.0, 0.00188, 150, 1.5),       // ~48 m (5.8 km/h)
                point(0.0, 0.00220, 180, 1.1),       // ~34 m (4.1 km/h)
                point(0.0, 0.00260, 210, 1.4),       // ~44 m (5.3 km/h)
                point(0.0, 0.00290, 240, 1.1),       // ~33 m (4 km/h)
                point(0.0, 0.00330, 270, 1.3),       // ~44 m (5.3 km/h)
                point(0.0, 0.00365, 300, 1.4)        // ~39 m (4.6 km/h)
        );

        TripGpsStatistics statistics = gpsStatisticsCalculator.calculateStatistics(path);
        double tripDistance = calculateTripDistance(path);
        TripType tripType = classification.classifyTravelType(statistics, Duration.ofSeconds(300), Double.valueOf(tripDistance).longValue(), config);

        assertEquals(TripType.WALK, tripType);
    }

    @Test
    void testCarClassification() {
        List<GPSPoint> path = List.of(
                point(0.0, 0.0, 0, 0),
                point(0.0, 0.01, 30, 37),  // ~1.11 km in 30s → 133 km/h (too fast, filtered out)
                point(0.0, 0.005, 90, 11), // ~555 m in 60s → 33 km/h (valid)
                point(0.0, 0.02, 180, 22)  // ~1.66 km in 90s → 66 km/h
        );
        TripGpsStatistics statistics = gpsStatisticsCalculator.calculateStatistics(path);
        double tripDistance = calculateTripDistance(path);
        TripType tripType = classification.classifyTravelType(statistics, Duration.ofSeconds(180), Double.valueOf(tripDistance).longValue(), config);

        assertEquals(TripType.CAR, tripType);
    }

    private double calculateTripDistance(List<? extends GpsPoint> path) {
        if (path == null || path.size() < 2) {
            return 0.0;
        }

        double totalDistance = 0.0;
        for (int i = 1; i < path.size(); i++) {
            GpsPoint p1 = path.get(i - 1);
            GpsPoint p2 = path.get(i);
            totalDistance += GeoUtils.haversine(p1.getLatitude(), p1.getLongitude(),
                    p2.getLatitude(), p2.getLongitude());
        }

        return totalDistance / 1000.0; // Convert meters to kilometers
    }

    @Test
    void testEmptyGpsStatistics() {
        TripGpsStatistics statistics = TripGpsStatistics.empty();
        TripType tripType = classification.classifyTravelType(statistics, Duration.ZERO, 0, config);
        assertEquals(TripType.UNKNOWN, tripType);
    }

    @Test
    void testNoSpeedCalculationCar() {
        TripType car20kmh = classification.classifyTravelType(TripGpsStatistics.empty(), Duration.ofMinutes(30), 10500, config);
        assertEquals(TripType.CAR, car20kmh);

        TripType car50kmh = classification.classifyTravelType(TripGpsStatistics.empty(), Duration.ofMinutes(60), 50*1000, config);
        assertEquals(TripType.CAR, car50kmh);

    }

    @Test
    void testNoSpeedCalculationWalk() {
        TripType walk1 = classification.classifyTravelType(TripGpsStatistics.empty(), Duration.ofMinutes(10), 600, config);
        assertEquals(TripType.WALK, walk1);
        TripType walk2 = classification.classifyTravelType(TripGpsStatistics.empty(), Duration.ofMinutes(60), 3000, config);
        assertEquals(TripType.WALK, walk2);
    }

    // ====================
    // BICYCLE TESTS
    // ====================

    @Test
    void testBicycleClassification_WhenDisabled() {
        // Bicycle speeds but bicycle detection disabled
        TripGpsStatistics stats = new TripGpsStatistics(15.0/3.6, 25.0/3.6, 12.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofMinutes(30), 7500, config);

        // Should fall back to CAR (since avgSpeed 15 >= carMinAvg 10)
        assertEquals(TripType.CAR, result);
    }

    @Test
    void testBicycleClassification_WhenEnabled() {
        TimelineConfig bicycleConfig = TimelineConfig.builder()
                .bicycleEnabled(true)
                .bicycleMinAvgSpeed(8.0)
                .bicycleMaxAvgSpeed(25.0)
                .bicycleMaxMaxSpeed(35.0)
                .carMinAvgSpeed(10.0)
                .carMinMaxSpeed(15.0)
                .walkingMaxAvgSpeed(6.0)
                .walkingMaxMaxSpeed(8.0)
                .build();

        // Typical cycling: 15 km/h avg, 25 km/h max
        TripGpsStatistics stats = new TripGpsStatistics(15.0/3.6, 25.0/3.6, 12.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofMinutes(30), 7500, bicycleConfig);

        assertEquals(TripType.BICYCLE, result);
    }

    @Test
    void testBicycleToCarBoundary_ExceedsMaxAvgSpeed() {
        TimelineConfig bicycleConfig = TimelineConfig.builder()
                .bicycleEnabled(true)
                .bicycleMinAvgSpeed(8.0)
                .bicycleMaxAvgSpeed(25.0)
                .bicycleMaxMaxSpeed(35.0)
                .carMinAvgSpeed(10.0)
                .carMinMaxSpeed(15.0)
                .build();

        // Fast cycling: 28 km/h avg exceeds bicycleMaxAvgSpeed (25)
        TripGpsStatistics stats = new TripGpsStatistics(28.0/3.6, 33.0/3.6, 8.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofMinutes(20), 9000, bicycleConfig);

        assertEquals(TripType.CAR, result);
    }

    @Test
    void testBicycleToCarBoundary_ExceedsMaxMaxSpeed() {
        TimelineConfig bicycleConfig = TimelineConfig.builder()
                .bicycleEnabled(true)
                .bicycleMinAvgSpeed(8.0)
                .bicycleMaxAvgSpeed(25.0)
                .bicycleMaxMaxSpeed(35.0)
                .carMinAvgSpeed(10.0)
                .carMinMaxSpeed(15.0)
                .build();

        // E-bike: 22 km/h avg, but 38 km/h max exceeds bicycleMaxMaxSpeed (35)
        TripGpsStatistics stats = new TripGpsStatistics(22.0/3.6, 38.0/3.6, 10.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofMinutes(15), 5500, bicycleConfig);

        assertEquals(TripType.CAR, result);
    }

    @Test
    void testRunningCapturedAsBicycle() {
        TimelineConfig bicycleConfig = TimelineConfig.builder()
                .bicycleEnabled(true)
                .bicycleMinAvgSpeed(8.0)
                .bicycleMaxAvgSpeed(25.0)
                .bicycleMaxMaxSpeed(35.0)
                .carMinAvgSpeed(10.0)
                .carMinMaxSpeed(15.0)
                .walkingMaxAvgSpeed(6.0)
                .walkingMaxMaxSpeed(8.0)
                .build();

        // Fast jogging: 12 km/h avg, 14 km/h max
        TripGpsStatistics stats = new TripGpsStatistics(12.0/3.6, 14.0/3.6, 5.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofMinutes(20), 4000, bicycleConfig);

        // Running captured as BICYCLE (consider displaying as "Cycling/Running" in UI)
        assertEquals(TripType.BICYCLE, result);
    }

    // ====================
    // TRAIN TESTS
    // ====================

    @Test
    void testTrainClassification_WhenDisabled() {
        // Train speeds but train detection disabled
        TripGpsStatistics stats = new TripGpsStatistics(80.0/3.6, 100.0/3.6, 8.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofMinutes(60), 80000, config);

        // Should fall back to CAR
        assertEquals(TripType.CAR, result);
    }

    @Test
    void testTrainClassification_RegionalTrain() {
        TimelineConfig trainConfig = TimelineConfig.builder()
                .trainEnabled(true)
                .trainMinAvgSpeed(30.0)
                .trainMaxAvgSpeed(150.0)
                .trainMinMaxSpeed(80.0)
                .trainMaxMaxSpeed(180.0)
                .trainMaxSpeedVariance(15.0)
                .carMinAvgSpeed(10.0)
                .carMinMaxSpeed(15.0)
                .build();

        // Regional train: 80 km/h avg, 120 km/h max, low variance (8)
        TripGpsStatistics stats = new TripGpsStatistics(80.0/3.6, 120.0/3.6, 8.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofMinutes(60), 80000, trainConfig);

        assertEquals(TripType.TRAIN, result);
    }

    @Test
    void testTrainVsCarDiscrimination_HighVariance() {
        TimelineConfig trainConfig = TimelineConfig.builder()
                .trainEnabled(true)
                .trainMinAvgSpeed(30.0)
                .trainMaxAvgSpeed(150.0)
                .trainMinMaxSpeed(80.0)
                .trainMaxMaxSpeed(180.0)
                .trainMaxSpeedVariance(15.0)
                .carMinAvgSpeed(10.0)
                .carMinMaxSpeed(15.0)
                .build();

        // Car on highway: 80 km/h avg, 120 km/h max, high variance (28)
        TripGpsStatistics stats = new TripGpsStatistics(80.0/3.6, 120.0/3.6, 28.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofMinutes(45), 60000, trainConfig);

        // High variance (28 > 15) fails train check → CAR
        assertEquals(TripType.CAR, result);
    }

    @Test
    void testTrainStationOnly_FilteredOut() {
        TimelineConfig trainConfig = TimelineConfig.builder()
                .trainEnabled(true)
                .trainMinAvgSpeed(30.0)
                .trainMaxAvgSpeed(150.0)
                .trainMinMaxSpeed(80.0)
                .trainMaxMaxSpeed(180.0)
                .trainMaxSpeedVariance(15.0)
                .carMinAvgSpeed(10.0)
                .carMinMaxSpeed(15.0)
                .build();

        // Station only: 15 km/h avg, 25 km/h max, low variance (5)
        // Low maxSpeed (25 < 80) fails trainMinMaxSpeed check
        TripGpsStatistics stats = new TripGpsStatistics(15.0/3.6, 25.0/3.6, 5.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofMinutes(10), 2500, trainConfig);

        assertEquals(TripType.CAR, result);
    }

    @Test
    void testHighSpeedTrain() {
        TimelineConfig trainConfig = TimelineConfig.builder()
                .trainEnabled(true)
                .trainMinAvgSpeed(30.0)
                .trainMaxAvgSpeed(150.0)
                .trainMinMaxSpeed(80.0)
                .trainMaxMaxSpeed(180.0)
                .trainMaxSpeedVariance(15.0)
                .carMinAvgSpeed(10.0)
                .build();

        // High-speed train: 150 km/h avg, 175 km/h max, low variance (10)
        TripGpsStatistics stats = new TripGpsStatistics(150.0/3.6, 175.0/3.6, 10.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofMinutes(120), 300000, trainConfig);

        assertEquals(TripType.TRAIN, result);
    }

    // ====================
    // FLIGHT TESTS
    // ====================

    @Test
    void testFlightClassification_WhenDisabled() {
        // Flight speeds but flight detection disabled
        TripGpsStatistics stats = new TripGpsStatistics(450.0/3.6, 850.0/3.6, 200.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofHours(2), 900000, config);

        // Should fall back to CAR (or UNKNOWN depending on implementation)
        assertEquals(TripType.CAR, result);
    }

    @Test
    void testFlightClassification_LongHaul() {
        TimelineConfig flightConfig = TimelineConfig.builder()
                .flightEnabled(true)
                .flightMinAvgSpeed(400.0)
                .flightMinMaxSpeed(500.0)
                .carMinAvgSpeed(10.0)
                .build();

        // Long-haul flight: 850 km/h avg, 900 km/h max
        TripGpsStatistics stats = new TripGpsStatistics(850.0/3.6, 900.0/3.6, 120.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofHours(8), 6800000, flightConfig);

        assertEquals(TripType.FLIGHT, result);
    }

    @Test
    void testFlightWithLongTaxiTime_AvgSpeedFails_MaxSpeedSucceeds() {
        TimelineConfig flightConfig = TimelineConfig.builder()
                .flightEnabled(true)
                .flightMinAvgSpeed(400.0)
                .flightMinMaxSpeed(500.0)
                .carMinAvgSpeed(10.0)
                .build();

        // Flight with taxi: 350 km/h avg (< 400), but 750 km/h max (>= 500)
        // OR logic should catch this via maxSpeed
        TripGpsStatistics stats = new TripGpsStatistics(350.0/3.6, 750.0/3.6, 200.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofMinutes(90), 52500, flightConfig);

        assertEquals(TripType.FLIGHT, result);
    }

    @Test
    void testFlightWithVeryLongTaxiTime_BothThresholdsFail() {
        TimelineConfig flightConfig = TimelineConfig.builder()
                .flightEnabled(true)
                .flightMinAvgSpeed(400.0)
                .flightMinMaxSpeed(500.0)
                .carMinAvgSpeed(10.0)
                .build();

        // Flight with extreme delays: 220 km/h avg, 450 km/h max
        // Both thresholds fail (220 < 400, 450 < 500)
        TripGpsStatistics stats = new TripGpsStatistics(220.0/3.6, 450.0/3.6, 150.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofHours(2), 44000, flightConfig);

        // Should fall back to CAR
        assertEquals(TripType.CAR, result);
    }

    @Test
    void testShortFlight() {
        TimelineConfig flightConfig = TimelineConfig.builder()
                .flightEnabled(true)
                .flightMinAvgSpeed(400.0)
                .flightMinMaxSpeed(500.0)
                .carMinAvgSpeed(10.0)
                .build();

        // Short flight: 420 km/h avg, 650 km/h max
        TripGpsStatistics stats = new TripGpsStatistics(420.0/3.6, 650.0/3.6, 180.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofMinutes(45), 31500, flightConfig);

        assertEquals(TripType.FLIGHT, result);
    }

    // ====================
    // PRIORITY ORDER TESTS
    // ====================

    @Test
    void testPriorityOrder_BicycleBeforeCar() {
        // CRITICAL: This test ensures BICYCLE is checked before CAR
        TimelineConfig allEnabledConfig = TimelineConfig.builder()
                .bicycleEnabled(true)
                .bicycleMinAvgSpeed(8.0)
                .bicycleMaxAvgSpeed(25.0)
                .bicycleMaxMaxSpeed(35.0)
                .carMinAvgSpeed(10.0)
                .carMinMaxSpeed(15.0)
                .build();

        // Speed 15 km/h matches both BICYCLE and CAR thresholds
        // Should be BICYCLE because it's checked first
        TripGpsStatistics stats = new TripGpsStatistics(15.0/3.6, 20.0/3.6, 10.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofMinutes(30), 7500, allEnabledConfig);

        assertEquals(TripType.BICYCLE, result);
    }

    @Test
    void testPriorityOrder_FlightBeforeTrain() {
        TimelineConfig allEnabledConfig = TimelineConfig.builder()
                .flightEnabled(true)
                .flightMinAvgSpeed(400.0)
                .flightMinMaxSpeed(500.0)
                .trainEnabled(true)
                .trainMinAvgSpeed(30.0)
                .trainMaxAvgSpeed(150.0)
                .trainMinMaxSpeed(80.0)
                .trainMaxMaxSpeed(180.0)
                .trainMaxSpeedVariance(15.0)
                .carMinAvgSpeed(10.0)
                .build();

        // Very fast but within train's max range: 140 km/h avg, 600 km/h max
        // maxSpeed >= 500 should trigger FLIGHT before TRAIN is checked
        TripGpsStatistics stats = new TripGpsStatistics(140.0/3.6, 600.0/3.6, 100.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofHours(2), 280000, allEnabledConfig);

        assertEquals(TripType.FLIGHT, result);
    }

    @Test
    void testCompleteClassificationChain() {
        // Test with all types enabled
        TimelineConfig allEnabledConfig = TimelineConfig.builder()
                .flightEnabled(true)
                .flightMinAvgSpeed(400.0)
                .flightMinMaxSpeed(500.0)
                .trainEnabled(true)
                .trainMinAvgSpeed(30.0)
                .trainMaxAvgSpeed(150.0)
                .trainMinMaxSpeed(80.0)
                .trainMaxMaxSpeed(180.0)
                .trainMaxSpeedVariance(15.0)
                .bicycleEnabled(true)
                .bicycleMinAvgSpeed(8.0)
                .bicycleMaxAvgSpeed(25.0)
                .bicycleMaxMaxSpeed(35.0)
                .carMinAvgSpeed(10.0)
                .carMinMaxSpeed(15.0)
                .walkingMaxAvgSpeed(6.0)
                .walkingMaxMaxSpeed(8.0)
                .build();

        // Walking
        assertEquals(TripType.WALK,
                classification.classifyTravelType(
                        new TripGpsStatistics(4.0/3.6, 6.0/3.6, 3.0, 0),
                        Duration.ofMinutes(10), 600, allEnabledConfig));

        // Bicycle
        assertEquals(TripType.BICYCLE,
                classification.classifyTravelType(
                        new TripGpsStatistics(15.0/3.6, 22.0/3.6, 8.0, 0),
                        Duration.ofMinutes(30), 7500, allEnabledConfig));

        // Car
        assertEquals(TripType.CAR,
                classification.classifyTravelType(
                        new TripGpsStatistics(50.0/3.6, 80.0/3.6, 25.0, 0),
                        Duration.ofMinutes(30), 25000, allEnabledConfig));

        // Train
        assertEquals(TripType.TRAIN,
                classification.classifyTravelType(
                        new TripGpsStatistics(100.0/3.6, 130.0/3.6, 10.0, 0),
                        Duration.ofMinutes(60), 100000, allEnabledConfig));

        // Flight
        assertEquals(TripType.FLIGHT,
                classification.classifyTravelType(
                        new TripGpsStatistics(500.0/3.6, 850.0/3.6, 150.0, 0),
                        Duration.ofHours(2), 1000000, allEnabledConfig));
    }
}

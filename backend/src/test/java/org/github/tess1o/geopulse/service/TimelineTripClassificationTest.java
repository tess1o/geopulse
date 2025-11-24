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
    // RUNNING TESTS
    // ====================

    @Test
    void testRunningClassification_WhenEnabled() {
        TimelineConfig runningConfig = TimelineConfig.builder()
                .runningEnabled(true)
                .runningMinAvgSpeed(7.0)
                .runningMaxAvgSpeed(14.0)
                .runningMaxMaxSpeed(18.0)
                .carMinAvgSpeed(10.0)
                .carMinMaxSpeed(15.0)
                .walkingMaxAvgSpeed(6.0)
                .walkingMaxMaxSpeed(8.0)
                .build();

        // Typical jogging: 12 km/h avg, 14 km/h max
        TripGpsStatistics stats = new TripGpsStatistics(12.0/3.6, 14.0/3.6, 5.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofMinutes(20), 4000, runningConfig);

        assertEquals(TripType.RUNNING, result);
    }

    @Test
    void testRunningClassification_WhenDisabled() {
        // Running speeds but running detection disabled
        TripGpsStatistics stats = new TripGpsStatistics(12.0/3.6, 14.0/3.6, 5.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofMinutes(20), 4000, config);

        // Should fall back to CAR (since avgSpeed 12 >= carMinAvg 10)
        assertEquals(TripType.CAR, result);
    }

    @Test
    void testRunningToWalkingBoundary() {
        TimelineConfig runningConfig = TimelineConfig.builder()
                .runningEnabled(true)
                .runningMinAvgSpeed(7.0)
                .runningMaxAvgSpeed(14.0)
                .runningMaxMaxSpeed(18.0)
                .carMinAvgSpeed(10.0)
                .carMinMaxSpeed(15.0)
                .walkingMaxAvgSpeed(6.0)
                .walkingMaxMaxSpeed(8.0)
                .build();

        // Fast walking: 5.5 km/h avg, 7.5 km/h max - below running min (7.0), within walk limits
        TripGpsStatistics stats = new TripGpsStatistics(5.5/3.6, 7.5/3.6, 3.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofMinutes(15), 1400, runningConfig);

        // Should be WALK (below runningMinAvgSpeed 7.0, within walkingMaxAvgSpeed 6.0 and walkingMaxMaxSpeed 8.0)
        assertEquals(TripType.WALK, result);
    }

    @Test
    void testRunningToCyclingBoundary_ExceedsMaxAvgSpeed() {
        TimelineConfig runningConfig = TimelineConfig.builder()
                .runningEnabled(true)
                .runningMinAvgSpeed(7.0)
                .runningMaxAvgSpeed(14.0)
                .runningMaxMaxSpeed(18.0)
                .bicycleEnabled(false)
                .carMinAvgSpeed(10.0)
                .carMinMaxSpeed(15.0)
                .build();

        // Fast running: 15 km/h avg exceeds runningMaxAvgSpeed (14)
        TripGpsStatistics stats = new TripGpsStatistics(15.0/3.6, 17.0/3.6, 6.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofMinutes(10), 2500, runningConfig);

        // Should fall back to CAR (bicycle disabled, exceeds running max)
        assertEquals(TripType.CAR, result);
    }

    @Test
    void testRunningToCyclingBoundary_ExceedsMaxMaxSpeed() {
        TimelineConfig runningConfig = TimelineConfig.builder()
                .runningEnabled(true)
                .runningMinAvgSpeed(7.0)
                .runningMaxAvgSpeed(14.0)
                .runningMaxMaxSpeed(18.0)
                .bicycleEnabled(false)
                .carMinAvgSpeed(10.0)
                .carMinMaxSpeed(15.0)
                .build();

        // Sprint interval run: 13 km/h avg, but 20 km/h max exceeds runningMaxMaxSpeed (18)
        TripGpsStatistics stats = new TripGpsStatistics(13.0/3.6, 20.0/3.6, 7.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofMinutes(12), 2600, runningConfig);

        // Should fall back to CAR (exceeds running max peak speed)
        assertEquals(TripType.CAR, result);
    }

    @Test
    void testBicycleTakesPriorityOverRunning() {
        TimelineConfig bothConfig = TimelineConfig.builder()
                .bicycleEnabled(true)
                .bicycleMinAvgSpeed(8.0)
                .bicycleMaxAvgSpeed(25.0)
                .bicycleMaxMaxSpeed(35.0)
                .runningEnabled(true)
                .runningMinAvgSpeed(7.0)
                .runningMaxAvgSpeed(14.0)
                .runningMaxMaxSpeed(18.0)
                .carMinAvgSpeed(10.0)
                .carMinMaxSpeed(15.0)
                .build();

        // Speed in overlap range (12 km/h): matches both BICYCLE and RUNNING
        TripGpsStatistics stats = new TripGpsStatistics(12.0/3.6, 14.0/3.6, 5.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofMinutes(20), 4000, bothConfig);

        // BICYCLE should win (checked before RUNNING in priority order)
        assertEquals(TripType.BICYCLE, result);
    }

    @Test
    void testRunningWithBicycleEnabled_SlowRun() {
        TimelineConfig bothConfig = TimelineConfig.builder()
                .bicycleEnabled(true)
                .bicycleMinAvgSpeed(8.0)
                .bicycleMaxAvgSpeed(25.0)
                .bicycleMaxMaxSpeed(35.0)
                .runningEnabled(true)
                .runningMinAvgSpeed(7.0)
                .runningMaxAvgSpeed(14.0)
                .runningMaxMaxSpeed(18.0)
                .carMinAvgSpeed(10.0)
                .carMinMaxSpeed(15.0)
                .walkingMaxAvgSpeed(6.0)
                .walkingMaxMaxSpeed(8.0)
                .build();

        // Slow run: 7.5 km/h avg - below bicycle min (8.0), within running range
        TripGpsStatistics stats = new TripGpsStatistics(7.5/3.6, 9.0/3.6, 4.0, 0);

        TripType result = classification.classifyTravelType(stats, Duration.ofMinutes(25), 3100, bothConfig);

        // Should be RUNNING (below bicycleMinAvgSpeed, within running range)
        assertEquals(TripType.RUNNING, result);
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

    // ====================
    // GPS SANITY CHECK TESTS (Bug Fix for Issue #492146)
    // ====================

    @Test
    void testUnreliableGpsSpeed_ShouldFallbackToCalculated() {
        // Real-world bug: Trip ID 492146 / 493646
        // Distance: 999m, Duration: 718s (calculated avg: 5.0 km/h)
        // But GPS reported: avgSpeed 11.4 km/h, maxSpeed 29.0 km/h
        // Was incorrectly classified as BICYCLE, then as CAR, should be WALK

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

        // GPS reports unreliable high speeds
        TripGpsStatistics unreliableStats = new TripGpsStatistics(
                3.1667,  // 11.4 km/h GPS avg (unreliable - 2.3x calculated!)
                8.0556,  // 29.0 km/h GPS max (unreliable - 5.8x calculated! GPS noise)
                12.086,
                0
        );

        TripType result = classification.classifyTravelType(
                unreliableStats,
                Duration.ofSeconds(718),
                999,
                bicycleConfig
        );

        // Sanity check detects unreliable GPS avg (11.4 > 5.0 * 2)
        // GPS max also unreliable (29.0 > 5.0 * 5) - likely GPS noise
        // Uses calculated avg (5.0 km/h) and estimated max (7.5 km/h)
        // Result: WALK (avg 5.0 <= 6.0, max 7.5 <= 8.0)
        assertEquals(TripType.WALK, result,
                "Walking trip with GPS noise should be WALK, not BICYCLE or CAR");
    }

    @Test
    void testCarTripWithTrafficStops_ShouldBeCarNotBicycle() {
        // Real-world bug: Trip ID 492862
        // Distance: 2282m, Duration: 687s (calculated avg: 10.8 km/h - slow due to traffic)
        // GPS: avgSpeed 32.6 km/h, maxSpeed 49.0 km/h (shows actual driving speed)
        // Was incorrectly classified as BICYCLE, should be CAR

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

        // Car trip with heavy traffic/stops
        TripGpsStatistics trafficStats = new TripGpsStatistics(
                9.047,   // 32.6 km/h GPS avg (unreliable - 3x calculated)
                13.611,  // 49.0 km/h GPS max (reliable - shows actual car speed!)
                11.618,
                0
        );

        TripType result = classification.classifyTravelType(
                trafficStats,
                Duration.ofSeconds(687),
                2282,
                bicycleConfig
        );

        // GPS avg unreliable (32.6 vs calculated 10.8) - use calculated avg
        // Keep GPS max speed (49.0 km/h) - reliable indicator of car
        // Result: CAR (maxSpeed 49.0 >= carMinMaxSpeed 15.0)
        assertEquals(TripType.CAR, result,
                "Car trip with traffic stops should be CAR (max speed 49 km/h), not BICYCLE");
    }

    @Test
    void testReliableGpsSpeed_ShouldUsGpsSpeed() {
        // GPS and calculated speeds match closely - GPS is reliable
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

        // Calculated: 2500m / 600s = 4.17 m/s = 15.0 km/h
        // GPS: 4.2 m/s = 15.12 km/h (very close!)
        TripGpsStatistics reliableStats = new TripGpsStatistics(
                4.2,   // 15.12 km/h (reliable - matches calculated)
                6.0,   // 21.6 km/h
                8.0,
                0
        );

        TripType result = classification.classifyTravelType(
                reliableStats,
                Duration.ofSeconds(600),
                2500,
                bicycleConfig
        );

        // GPS is reliable, should use it → BICYCLE
        assertEquals(TripType.BICYCLE, result,
                "Trip with reliable GPS 15 km/h should be BICYCLE");
    }

    @Test
    void testBoundary_GpsExactly2xCalculated_ShouldStillUseGps() {
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

        // Calculated: 1000m / 600s = 1.67 m/s = 6.0 km/h
        // GPS: 3.33 m/s = 12.0 km/h (exactly 2x)
        TripGpsStatistics boundaryStats = new TripGpsStatistics(
                3.33,  // 12.0 km/h (exactly 2x calculated)
                5.0,   // 18.0 km/h
                8.0,
                0
        );

        TripType result = classification.classifyTravelType(
                boundaryStats,
                Duration.ofSeconds(600),
                1000,
                bicycleConfig
        );

        // At exactly 2x, sanity check should NOT trigger (threshold is > 2.0)
        // Should use GPS speed (12 km/h) → BICYCLE
        assertEquals(TripType.BICYCLE, result,
                "GPS at exactly 2x calculated should still be trusted");
    }

    @Test
    void testJustOver2x_ShouldTriggerSanityCheck() {
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

        // Calculated: 1000m / 600s = 1.67 m/s = 6.0 km/h
        // GPS: 3.4 m/s = 12.24 km/h (2.04x calculated)
        TripGpsStatistics overThresholdStats = new TripGpsStatistics(
                3.4,   // 12.24 km/h (2.04x calculated - triggers sanity check for speeds < 20 km/h)
                5.0,   // 18.0 km/h max (3x calculated - keep this, not > 5x)
                8.0,
                0
        );

        TripType result = classification.classifyTravelType(
                overThresholdStats,
                Duration.ofSeconds(600),
                1000,
                bicycleConfig
        );

        // Over 2x threshold for low speeds (< 20 km/h), sanity check triggers
        // GPS max (18.0) is 3x calculated but < 5x threshold, so keep it
        // Uses calculated avg (6.0 km/h) but keeps GPS max (18.0 km/h)
        // Result: CAR (maxSpeed 18.0 >= carMinMaxSpeed 15.0)
        assertEquals(TripType.CAR, result,
                "GPS over 2x calculated should use calculated avg but keep GPS max if < 5x");
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

    // ====================
    // GPS MAX SPEED SPIKE NOISE DETECTION (Bug Fix for Trip ID 2133574)
    // ====================

    @Test
    void testMaxSpeedSpikeWithReliableAvg_ShouldFilterMaxSpike() {
        // Real-world bug: Trip ID 2133574
        // Distance: 1325m, Duration: 2346s (39 minutes)
        // GPS avg: 0.98 m/s = 3.5 km/h (reliable - matches calculated 2.03 km/h within 2x)
        // GPS max: 10.8 m/s = 39 km/h (noise spike - 11x the avg!)
        // Was incorrectly classified as CAR due to max speed >= 15 km/h
        // Should be WALK

        TripGpsStatistics spikeStats = new TripGpsStatistics(
                0.9841269841269843,  // 3.5 km/h avg
                10.833333333333334,  // 39 km/h max (noise spike!)
                2.933610481229529,
                0
        );

        TripType result = classification.classifyTravelType(
                spikeStats,
                Duration.ofSeconds(2346),
                1325,
                config
        );

        // GPS avg (3.5 km/h) is reliable (calculated 2.03 km/h, ratio 1.7x < 2.0)
        // But GPS max (39 km/h) is 11x avg - clearly noise spike
        // Max should be adjusted to avg * 1.5 = 5.25 km/h
        // Result: WALK (avg 3.5 <= 6.0, adjusted max 5.25 <= 8.0)
        assertEquals(TripType.WALK, result,
                "Walking trip with max speed noise spike should be WALK, not CAR");
    }

    @Test
    void testMaxSpeedSpikeAtBoundary_5xAvg_ShouldNotFilter() {
        // Test boundary: max speed exactly 5x avg should NOT be filtered
        TripGpsStatistics boundaryStats = new TripGpsStatistics(
                1.0,   // 3.6 km/h avg
                5.0,   // 18.0 km/h max (exactly 5x avg)
                3.0,
                0
        );

        TripType result = classification.classifyTravelType(
                boundaryStats,
                Duration.ofSeconds(600),  // 10 minutes
                600,  // 600m → calculated 3.6 km/h (matches GPS avg)
                config
        );

        // GPS max (18.0 km/h) is exactly 5x avg - at boundary, should NOT filter
        // Result: CAR (maxSpeed 18.0 >= carMinMaxSpeed 15.0)
        assertEquals(TripType.CAR, result,
                "Max speed at exactly 5x avg should NOT be filtered");
    }

    @Test
    void testMaxSpeedSpikeJustOver5x_ShouldFilter() {
        // Test just over 5x threshold - should filter
        TripGpsStatistics overBoundaryStats = new TripGpsStatistics(
                1.0,   // 3.6 km/h avg
                5.1,   // 18.36 km/h max (5.1x avg - over threshold)
                3.0,
                0
        );

        TripType result = classification.classifyTravelType(
                overBoundaryStats,
                Duration.ofSeconds(600),
                600,
                config
        );

        // GPS max (18.36 km/h) is 5.1x avg - over 5x threshold, filter to 3.6 * 1.5 = 5.4 km/h
        // Result: WALK (avg 3.6 <= 6.0, adjusted max 5.4 <= 8.0)
        assertEquals(TripType.WALK, result,
                "Max speed just over 5x avg should be filtered to estimated max");
    }

    // ====================
    // GPS NOISE DETECTION TESTS (Critical Fix for Trip ID 494443)
    // ====================

    @Test
    void testSupersonicGpsNoise_ShouldBeUnknown() {
        // Real-world bug: Trip ID 494443
        // Distance: 1,068,059m, Duration: 270s → Calculated: 14,238 km/h (11.6x speed of sound!)
        // GPS: avgSpeed 0.35 m/s (1.25 km/h), maxSpeed 1.04 m/s (3.74 km/h) → walking speeds
        // This is clearly GPS noise with two inaccurate points far apart
        // Was incorrectly classified as FLIGHT, should be UNKNOWN

        TimelineConfig flightConfig = TimelineConfig.builder()
                .flightEnabled(true)
                .flightMinAvgSpeed(400.0)
                .flightMinMaxSpeed(500.0)
                .carMinAvgSpeed(10.0)
                .carMinMaxSpeed(15.0)
                .walkingMaxAvgSpeed(6.0)
                .walkingMaxMaxSpeed(8.0)
                .build();

        // GPS shows walking speeds but calculated is supersonic
        TripGpsStatistics noiseStats = new TripGpsStatistics(
                0.346737787037037,  // 1.25 km/h GPS avg (walking speed!)
                1.040213361111111,  // 3.74 km/h GPS max (walking speed!)
                0.2404541859186833,
                0
        );

        TripType result = classification.classifyTravelType(
                noiseStats,
                Duration.ofSeconds(270),  // 4.5 minutes
                1068059,  // 1,068 km - impossible in 4.5 minutes!
                flightConfig
        );

        // Calculated speed: 1068 km / (270s / 3600) = 14,238 km/h (supersonic!)
        // MAX_REALISTIC_SPEED check (1200 km/h) should catch this
        // Result: UNKNOWN (GPS noise detected)
        assertEquals(TripType.UNKNOWN, result,
                "GPS noise creating impossible 14,238 km/h trip should be UNKNOWN, not FLIGHT");
    }

    @Test
    void testLegitimateFlightNotFiltered() {
        // Verify legitimate flights are NOT filtered by the supersonic check
        TimelineConfig flightConfig = TimelineConfig.builder()
                .flightEnabled(true)
                .flightMinAvgSpeed(400.0)
                .flightMinMaxSpeed(500.0)
                .carMinAvgSpeed(10.0)
                .build();

        // Real flight: 900 km in 1 hour = 900 km/h (below 1200 threshold)
        TripGpsStatistics flightStats = new TripGpsStatistics(
                250.0,  // 900 km/h GPS avg
                260.0,  // 936 km/h GPS max
                120.0,
                0
        );

        TripType result = classification.classifyTravelType(
                flightStats,
                Duration.ofHours(1),
                900000,
                flightConfig
        );

        // Should pass supersonic check (900 < 1200) and be classified as FLIGHT
        assertEquals(TripType.FLIGHT, result,
                "Legitimate 900 km/h flight should be FLIGHT, not filtered as noise");
    }

    @Test
    void testEdgeOfRealistic_JustUnder1200kmh() {
        // Test boundary at MAX_REALISTIC_SPEED (1200 km/h)
        TimelineConfig flightConfig = TimelineConfig.builder()
                .flightEnabled(true)
                .flightMinAvgSpeed(400.0)
                .flightMinMaxSpeed(500.0)
                .build();

        // Right at the edge: 1199 km/h
        TripGpsStatistics edgeStats = new TripGpsStatistics(
                332.5,  // 1197 km/h
                333.0,  // 1198.8 km/h (just under 1200)
                50.0,
                0
        );

        TripType result = classification.classifyTravelType(
                edgeStats,
                Duration.ofHours(1),
                1199000,
                flightConfig
        );

        // Should pass (1199 < 1200) → FLIGHT
        assertEquals(TripType.FLIGHT, result,
                "Speed of 1199 km/h should pass as FLIGHT");
    }

    @Test
    void testSupersonic_JustOver1200kmh() {
        // Test just over MAX_REALISTIC_SPEED threshold
        TimelineConfig flightConfig = TimelineConfig.builder()
                .flightEnabled(true)
                .flightMinAvgSpeed(400.0)
                .flightMinMaxSpeed(500.0)
                .build();

        // Just over the edge: 1201 km/h
        TripGpsStatistics supersonicStats = new TripGpsStatistics(
                333.6,  // 1200.96 km/h (just over)
                334.0,  // 1202.4 km/h
                50.0,
                0
        );

        TripType result = classification.classifyTravelType(
                supersonicStats,
                Duration.ofHours(1),
                1201000,
                flightConfig
        );

        // Should fail (1201 > 1200) → UNKNOWN
        assertEquals(TripType.UNKNOWN, result,
                "Speed of 1201 km/h should be filtered as GPS noise → UNKNOWN");
    }
}

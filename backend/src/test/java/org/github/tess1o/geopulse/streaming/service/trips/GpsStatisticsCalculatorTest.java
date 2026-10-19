package org.github.tess1o.geopulse.streaming.service.trips;

import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class GpsStatisticsCalculatorTest {

    private static final Instant START = Instant.parse("2026-10-17T09:35:35Z");
    private static final double KMH_DELTA = 0.01;

    private final GpsStatisticsCalculator calculator = new GpsStatisticsCalculator();
    private final TravelClassification classification = new TravelClassification();

    private static final TimelineConfig CONFIG = TimelineConfig.builder()
            .carEnabled(true)
            .carMinAvgSpeed(10.0)
            .carMinMaxSpeed(15.0)
            .walkingMaxAvgSpeed(6.0)
            .walkingMaxMaxSpeed(8.0)
            .shortDistanceKm(1.0)
            .bicycleEnabled(false)
            .runningEnabled(false)
            .trainEnabled(false)
            .flightEnabled(false)
            .build();

    /**
     * Real OwnTracks walk: the tracker woke up with a burst of three readings 3 s apart
     * (17 and 16 km/h while the fix settled), then walked at 0-4 km/h every 30-75 s.
     * The raw max (17 km/h) used to pass the car max-speed check.
     */
    @Test
    void walkWithStartupSpeedBurst_isClassifiedAsWalk() {
        long[] offsets = {0, 3, 6, 53, 124, 153, 199, 273, 303, 374, 449, 515, 573, 603, 671, 729, 753, 808, 866, 903, 960};
        double[] speedsKmh = {17, 16, 7, 3, 3, 2, 3, 2, 2, 2, 2, 2, 1, 0, 2, 2, 3, 3, 3, 3, 4};
        List<GPSPoint> path = path(offsets, speedsKmh);

        TripGpsStatistics stats = calculator.calculateStatistics(path);

        assertEquals(3.0, kmh(stats.maxGpsSpeed()), KMH_DELTA);
        assertEquals(2.48, kmh(stats.avgGpsSpeed()), KMH_DELTA);
        assertEquals(TripType.WALK,
                classification.classifyTravelType(stats, Duration.ofSeconds(903), 786, CONFIG));
    }

    @Test
    void denseWalkWithShortSpike_ignoresSpikeForMaxSpeed() {
        List<Long> offsets = new ArrayList<>();
        List<Double> speeds = new ArrayList<>();
        for (int t = 0; t <= 300; t++) {
            offsets.add((long) t);
            speeds.add(t >= 100 && t < 105 ? 20.0 : 5.0);
        }

        TripGpsStatistics stats = calculator.calculateStatistics(path(offsets, speeds));

        assertEquals(5.0, kmh(stats.maxGpsSpeed()), KMH_DELTA);
    }

    @Test
    void sparseCarTrip_keepsSustainedPeakAndIsClassifiedAsCar() {
        long[] offsets = {0, 60, 120, 180, 240, 300, 360, 420, 480};
        double[] speedsKmh = {0, 30, 45, 50, 20, 0, 35, 40, 10};

        TripGpsStatistics stats = calculator.calculateStatistics(path(offsets, speedsKmh));

        assertEquals(45.0, kmh(stats.maxGpsSpeed()), KMH_DELTA);
        assertEquals(28.13, kmh(stats.avgGpsSpeed()), KMH_DELTA);
        assertEquals(TripType.CAR,
                classification.classifyTravelType(stats, Duration.ofSeconds(480), 3750, CONFIG));
    }

    @Test
    void denseCarTripWithTrafficLight_keepsCruisingSpeedAsMax() {
        List<Long> offsets = new ArrayList<>();
        List<Double> speeds = new ArrayList<>();
        for (int t = 0; t <= 240; t += 5) {
            offsets.add((long) t);
            boolean driving = (t >= 30 && t < 90) || (t >= 180 && t < 240);
            speeds.add(driving ? 35.0 : 0.0);
        }

        TripGpsStatistics stats = calculator.calculateStatistics(path(offsets, speeds));

        assertEquals(35.0, kmh(stats.maxGpsSpeed()), KMH_DELTA);
    }

    @Test
    void tripTooShortForSustainedWindow_usesRawMax() {
        TripGpsStatistics twoPoints = calculator.calculateStatistics(path(new long[]{0, 120}, new double[]{0, 40}));
        TripGpsStatistics shortBurst = calculator.calculateStatistics(path(new long[]{0, 5, 10, 15}, new double[]{10, 40, 30, 20}));

        assertEquals(40.0, kmh(twoPoints.maxGpsSpeed()), KMH_DELTA);
        assertEquals(40.0, kmh(shortBurst.maxGpsSpeed()), KMH_DELTA);
    }

    @Test
    void pointsWithoutTimestamps_useEqualWeightsAndRawMax() {
        List<GPSPoint> path = List.of(
                new GPSPoint(0.0, 0.0, 2.0, 5.0),
                new GPSPoint(0.0, 0.0, 4.0, 5.0),
                new GPSPoint(0.0, 0.0, 6.0, 5.0)
        );

        TripGpsStatistics stats = calculator.calculateStatistics(path);

        assertEquals(4.0, stats.avgGpsSpeed(), 1e-9);
        assertEquals(6.0, stats.maxGpsSpeed(), 1e-9);
        assertEquals(8.0 / 3.0, stats.speedVariance(), 1e-9);
    }

    private static List<GPSPoint> path(long[] offsets, double[] speedsKmh) {
        List<GPSPoint> points = new ArrayList<>();
        for (int i = 0; i < offsets.length; i++) {
            points.add(new GPSPoint(0.0, 0.0, speedsKmh[i] / 3.6, 5.0, START.plusSeconds(offsets[i])));
        }
        return points;
    }

    private static List<GPSPoint> path(List<Long> offsets, List<Double> speedsKmh) {
        return path(offsets.stream().mapToLong(Long::longValue).toArray(),
                speedsKmh.stream().mapToDouble(Double::doubleValue).toArray());
    }

    private static double kmh(double metersPerSecond) {
        return metersPerSecond * 3.6;
    }
}

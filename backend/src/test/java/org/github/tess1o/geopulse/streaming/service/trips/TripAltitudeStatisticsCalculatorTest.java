package org.github.tess1o.geopulse.streaming.service.trips;

import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class TripAltitudeStatisticsCalculatorTest {

    private static final Instant START = Instant.parse("2026-10-10T10:30:00Z");
    private static final int INTERVAL_SECONDS = 18;
    private static final double AIRFIELD = 235.0;
    private static final TimelineConfig ACCURACY_FILTER = config(true);

    private final TripAltitudeStatisticsCalculator calculator = new TripAltitudeStatisticsCalculator();

    /**
     * Shaped like the glider flights from issue #621: aerotow at ~3.5 m/s, thermalling between 1300 and 1500 m
     * (climbing 2 m/s, gliding down 1 m/s), then a glide back to the airfield.
     */
    @Test
    void gliderFlight_HasHighSustainedHeightAndFastVerticalSpeed() {
        List<Double> altitudes = new ArrayList<>();
        repeat(altitudes, 7, AIRFIELD);                                   // take-off roll
        double altitude = AIRFIELD;
        while (altitude < 1500) {                                         // aerotow
            altitude += 3.5 * INTERVAL_SECONDS;
            altitudes.add(altitude);
        }
        for (int cycle = 0; cycle < 6; cycle++) {
            while (altitude > 1300) {                                     // glide to the next thermal
                altitude -= 1.0 * INTERVAL_SECONDS;
                altitudes.add(altitude);
            }
            while (altitude < 1500) {                                     // circle in the thermal
                altitude += 2.0 * INTERVAL_SECONDS;
                altitudes.add(altitude);
            }
        }
        while (altitude > AIRFIELD) {                                     // final glide
            altitude = Math.max(AIRFIELD, altitude - 2.5 * INTERVAL_SECONDS);
            altitudes.add(altitude);
        }
        repeat(altitudes, 7, AIRFIELD);                                   // landing roll

        TripAltitudeStatistics stats = calculator.calculateStatistics(points(altitudes), ACCURACY_FILTER);

        assertTrue(stats.hasEvidence());
        assertTrue(stats.sustainedHeightMeters() > 1000, "height " + stats.sustainedHeightMeters());
        assertTrue(stats.verticalSpeedP90() > 1.5, "vertical speed " + stats.verticalSpeedP90());
        assertEquals(altitudes.size(), stats.altitudeSampleCount());
    }

    @Test
    void flatCarTrip_StaysNearEndpoints() {
        List<GPSPoint> path = points(120, i -> 80.0 + noise(i));

        TripAltitudeStatistics stats = calculator.calculateStatistics(path, ACCURACY_FILTER);

        assertTrue(stats.hasEvidence());
        assertTrue(stats.sustainedHeightMeters() < 20, "height " + stats.sustainedHeightMeters());
        assertTrue(stats.verticalSpeedP90() < 0.6, "vertical speed " + stats.verticalSpeedP90());
    }

    @Test
    void driveUpToHill_IsMeasuredFromTheHigherEndpoint() {
        // Climbs 520 m at 0.25 m/s and ends on top
        List<GPSPoint> path = points(120, i -> 80.0 + i * 0.25 * INTERVAL_SECONDS + noise(i));

        TripAltitudeStatistics stats = calculator.calculateStatistics(path, ACCURACY_FILTER);

        assertTrue(stats.sustainedHeightMeters() <= 0, "height " + stats.sustainedHeightMeters());
        assertTrue(stats.verticalSpeedP90() < 1.2, "vertical speed " + stats.verticalSpeedP90());
    }

    @Test
    void mountainPassLoop_IsHighButSlow() {
        // 500 m → 2000 m → 500 m at 0.6 m/s, like a drive over a pass and back
        int half = (int) (1500 / (0.6 * INTERVAL_SECONDS));
        List<GPSPoint> path = points(2 * half + 1,
                i -> 500.0 + Math.min(i, 2 * half - i) * 0.6 * INTERVAL_SECONDS + noise(i));

        TripAltitudeStatistics stats = calculator.calculateStatistics(path, ACCURACY_FILTER);

        assertTrue(stats.sustainedHeightMeters() > 300, "height " + stats.sustainedHeightMeters());
        assertTrue(stats.verticalSpeedP90() < 1.2, "vertical speed " + stats.verticalSpeedP90());
    }

    @Test
    void singleAltitudeSpike_IsSmoothedOut() {
        List<GPSPoint> path = points(60, i -> i == 30 ? 2500.0 : 80.0 + noise(i));

        TripAltitudeStatistics stats = calculator.calculateStatistics(path, ACCURACY_FILTER);

        assertTrue(stats.sustainedHeightMeters() < 20, "height " + stats.sustainedHeightMeters());
        assertTrue(stats.verticalSpeedP90() < 0.6, "vertical speed " + stats.verticalSpeedP90());
    }

    @Test
    void pointsWithoutAltitude_HaveNoEvidence() {
        TripAltitudeStatistics stats = calculator.calculateStatistics(points(60, i -> null), ACCURACY_FILTER);

        assertFalse(stats.hasEvidence());
        assertEquals(0, stats.altitudeSampleCount());
    }

    @Test
    void zeroAltitude_IsTreatedAsMissing() {
        TripAltitudeStatistics stats = calculator.calculateStatistics(points(60, i -> 0.0), ACCURACY_FILTER);

        assertFalse(stats.hasEvidence());
        assertEquals(0, stats.altitudeSampleCount());
    }

    @Test
    void lowAltitudeCoverage_HasNoEvidence() {
        // Only every third point carries an altitude
        TripAltitudeStatistics stats = calculator.calculateStatistics(points(60, i -> i % 3 == 0 ? 1500.0 : null), ACCURACY_FILTER);

        assertFalse(stats.hasEvidence());
        assertEquals(20, stats.altitudeSampleCount());
    }

    @Test
    void tooFewPoints_HaveNoEvidence() {
        TripAltitudeStatistics stats = calculator.calculateStatistics(points(5, i -> 1000.0 + i * 100), ACCURACY_FILTER);

        assertFalse(stats.hasEvidence());
        assertEquals(5, stats.altitudeSampleCount());
    }

    /**
     * Network fixes far off the track can carry made-up altitudes. Timeline generation leaves them out, so the
     * evidence must too, or a merged trip or a reclassification would see a flight the timeline never saw.
     */
    @Test
    void inaccuratePoints_AreLeftOut() {
        List<GPSPoint> path = points(120, i -> i % 2 == 0 ? 80.0 + noise(i) : 1500.0 + noise(i),
                i -> i % 2 == 0 ? 5.0 : 900.0);

        TripAltitudeStatistics stats = calculator.calculateStatistics(path, ACCURACY_FILTER);

        assertTrue(stats.hasEvidence());
        assertTrue(stats.sustainedHeightMeters() < 20, "height " + stats.sustainedHeightMeters());
        assertEquals(60, stats.altitudeSampleCount());
    }

    @Test
    void inaccuratePoints_AreKeptWhenTheAccuracyFilterIsOff() {
        List<GPSPoint> path = points(120, i -> i % 2 == 0 ? 80.0 + noise(i) : 1500.0 + noise(i),
                i -> i % 2 == 0 ? 5.0 : 900.0);

        TripAltitudeStatistics stats = calculator.calculateStatistics(path, config(false));

        assertEquals(120, stats.altitudeSampleCount());
    }

    @Test
    void emptyPath_HasNoEvidence() {
        TripAltitudeStatistics stats = calculator.calculateStatistics(List.of(), ACCURACY_FILTER);

        assertFalse(stats.hasEvidence());
        assertEquals(0, stats.altitudeSampleCount());
    }

    private static void repeat(List<Double> altitudes, int count, double altitude) {
        for (int i = 0; i < count; i++) {
            altitudes.add(altitude);
        }
    }

    /** Deterministic GPS altitude noise of up to ±6 m. */
    private static double noise(int i) {
        return ((i * 7) % 13) - 6;
    }

    private static List<GPSPoint> points(List<Double> altitudes) {
        return points(altitudes.size(), altitudes::get);
    }

    private static List<GPSPoint> points(int count, IntFunction<Double> altitude) {
        return points(count, altitude, i -> 5.0);
    }

    private static List<GPSPoint> points(int count, IntFunction<Double> altitude, IntFunction<Double> accuracy) {
        List<GPSPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            GPSPoint point = new GPSPoint(57.5 + i * 0.001, 16.8, 20.0, accuracy.apply(i),
                    START.plusSeconds((long) i * INTERVAL_SECONDS));
            point.setAltitude(altitude.apply(i));
            points.add(point);
        }
        return points;
    }

    private static TimelineConfig config(boolean useVelocityAccuracy) {
        return TimelineConfig.builder()
                .useVelocityAccuracy(useVelocityAccuracy)
                .staypointMaxAccuracyThreshold(60.0)
                .build();
    }
}

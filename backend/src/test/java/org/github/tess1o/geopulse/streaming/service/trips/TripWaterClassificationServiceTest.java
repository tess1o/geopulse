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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class TripWaterClassificationServiceTest {

    private static final Instant START = Instant.parse("2026-05-03T09:10:00Z");
    private static final int ACCURATE_FIXES = 20;
    private static final double TOWER_LATITUDE = 40.66181;
    private static final double TOWER_LONGITUDE = 15.51317;

    private final TripWaterClassificationService service = new TripWaterClassificationService();
    private final TravelClassification classification = new TravelClassification();

    /**
     * Boat trip reported in issue #631: GPS fixes on water every ~300 m at ~14 km/h, interleaved with network fixes
     * that keep landing on the same spot on land, 900 m accuracy. The jumps to the land spot used to make up most of
     * the measured distance, so the water ratio stayed far below the boat threshold.
     */
    @Test
    void boatTripWithLandNetworkFixes_usesOnlyAccurateFixes() {
        List<GPSPoint> points = boatTripWithNetworkFixes(false);

        TripWaterStatistics statistics = service.calculateStatistics(points, config(true));

        assertTrue(statistics.hasEvidence());
        assertEquals(1.0, statistics.waterDistanceRatio(), 0.0001);
        assertTrue(statistics.longestWaterSegmentMeters() > 5_000);
        assertEquals(ACCURATE_FIXES - 1, statistics.waterSampleCount());
        assertEquals(TripType.BOAT, classify(statistics));
    }

    @Test
    void networkFixesWithoutEnvironmentData_doNotMakeEvidenceUnavailable() {
        List<GPSPoint> points = boatTripWithNetworkFixes(true);

        TripWaterStatistics statistics = service.calculateStatistics(points, config(true));

        assertTrue(statistics.hasEvidence());
        assertEquals(1.0, statistics.waterDistanceRatio(), 0.0001);
    }

    @Test
    void accuracyFilterDisabled_keepsEveryFix() {
        List<GPSPoint> points = boatTripWithNetworkFixes(false);

        TripWaterStatistics statistics = service.calculateStatistics(points, config(false));

        assertTrue(statistics.hasEvidence());
        assertTrue(statistics.waterDistanceRatio() < 0.6);
        assertNotEquals(TripType.BOAT, classify(statistics));
    }

    @Test
    void onlyNetworkFixes_hasNoEvidence() {
        List<GPSPoint> points = boatTripWithNetworkFixes(false).stream()
                .filter(point -> point.getAccuracy() > 100)
                .toList();

        assertFalse(service.calculateStatistics(points, config(true)).hasEvidence());
    }

    private TripType classify(TripWaterStatistics waterStatistics) {
        double speed = 14.0 / 3.6;
        return classification.classifyTravelType(new TripGpsStatistics(speed, speed, 0.5, 0),
                waterStatistics, Duration.ofMinutes(25), 5_600, config(true));
    }

    private static List<GPSPoint> boatTripWithNetworkFixes(boolean networkFixesUnclassified) {
        List<GPSPoint> points = new ArrayList<>();
        Instant time = START;
        for (int i = 0; i < ACCURATE_FIXES; i++) {
            points.add(point(time, 40.645, 15.50 + i * 0.0035, 14.0, 4.0, true));
            time = time.plusSeconds(40);
            points.add(point(time, TOWER_LATITUDE, TOWER_LONGITUDE, 0.0, 900.0,
                    networkFixesUnclassified ? null : false));
            time = time.plusSeconds(35);
        }
        return points;
    }

    private static GPSPoint point(Instant timestamp, double latitude, double longitude, double speedKmh,
                                  double accuracy, Boolean onWater) {
        GPSPoint point = new GPSPoint(latitude, longitude, speedKmh / 3.6, accuracy, timestamp);
        point.setOnWater(onWater);
        return point;
    }

    private static TimelineConfig config(boolean useVelocityAccuracy) {
        return TimelineConfig.builder()
                .useVelocityAccuracy(useVelocityAccuracy)
                .staypointMaxAccuracyThreshold(60.0)
                .boatEnabled(true)
                .boatMinWaterRatio(0.6)
                .boatMinWaterDistanceMeters(2000.0)
                .boatMinContinuousWaterDistanceMeters(1000.0)
                .boatMaxPlausibleSpeed(120.0)
                .carEnabled(true)
                .carMinAvgSpeed(10.0)
                .carMinMaxSpeed(15.0)
                .walkingMaxAvgSpeed(6.0)
                .walkingMaxMaxSpeed(8.0)
                .shortDistanceKm(1.0)
                .bicycleEnabled(true)
                .bicycleMinAvgSpeed(8.0)
                .bicycleMaxAvgSpeed(25.0)
                .bicycleMaxMaxSpeed(35.0)
                .runningEnabled(false)
                .trainEnabled(false)
                .flightEnabled(false)
                .build();
    }
}

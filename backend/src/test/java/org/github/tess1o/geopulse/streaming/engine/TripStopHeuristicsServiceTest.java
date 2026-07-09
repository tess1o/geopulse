package org.github.tess1o.geopulse.streaming.engine;

import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class TripStopHeuristicsServiceTest {
    private TripStopHeuristicsService service;
    private TimelineConfig config;
    @BeforeEach
    void setUp() {
        service = new TripStopHeuristicsService();
        config = TimelineConfig.builder()
                .staypointRadiusMeters(80)
                .staypointVelocityThreshold(2.0)
                .tripArrivalMinPoints(3)
                .tripArrivalDetectionMinDurationSeconds(90)
                .tripSustainedStopMinDurationSeconds(60)
                .build();
    }
    @Test
    void shouldDetectTripStopFromRecentWindow_WhenArrivalClusterIsSlowAndLongEnough() {
        List<GPSPoint> activePoints = List.of(
                point("2024-01-01T10:00:00Z", 40.7120, -74.0100, 9.0),
                point("2024-01-01T10:10:00Z", 40.7130, -74.0080, 11.0),
                point("2024-01-01T10:20:00Z", 40.7140, -74.0060, 10.0),
                point("2024-01-01T10:30:00Z", 40.71480, -74.00580, 0.7),
                point("2024-01-01T10:31:00Z", 40.71484, -74.00576, 0.5),
                point("2024-01-01T10:31:40Z", 40.71482, -74.00574, 0.4));
        TripStopHeuristicsService.TripStopDetection result =
                service.detectTripStopFromRecentWindow(activePoints, config);
        assertTrue(result.isStopDetected());
        assertEquals(3, result.getStoppedClusterStartIndex());
    }
    @Test
    void shouldNotDetectTripStopFromRecentWindow_WhenRecentClusterTooShort() {
        List<GPSPoint> activePoints = List.of(
                point("2024-01-01T10:00:00Z", 40.7120, -74.0100, 9.0),
                point("2024-01-01T10:10:00Z", 40.7130, -74.0080, 11.0),
                point("2024-01-01T10:20:00Z", 40.7140, -74.0060, 10.0),
                point("2024-01-01T10:30:00Z", 40.71480, -74.00580, 0.7),
                point("2024-01-01T10:30:20Z", 40.71484, -74.00576, 0.5),
                point("2024-01-01T10:30:40Z", 40.71482, -74.00574, 0.4));
        TripStopHeuristicsService.TripStopDetection result =
                service.detectTripStopFromRecentWindow(activePoints, config);
        assertFalse(result.isStopDetected());
    }

    @Test
    void shouldDetectTripStopFromFullTrailingCluster_WhenHighFrequencyPointsSpanSustainedStop() {
        List<GPSPoint> activePoints = List.of(
                point("2024-01-01T10:00:00Z", 40.7120, -74.0100, 6.0),
                point("2024-01-01T10:01:00Z", 40.7130, -74.0080, 7.0),
                point("2024-01-01T10:02:00Z", 40.71400, -74.00600, 1.4),
                point("2024-01-01T10:02:05Z", 40.71402, -74.00600, 1.2),
                point("2024-01-01T10:02:10Z", 40.71403, -74.00601, 0.8),
                point("2024-01-01T10:02:15Z", 40.71404, -74.00601, 0.6),
                point("2024-01-01T10:02:20Z", 40.71405, -74.00602, 0.4),
                point("2024-01-01T10:02:25Z", 40.71406, -74.00602, 0.3),
                point("2024-01-01T10:02:30Z", 40.71407, -74.00603, 0.2),
                point("2024-01-01T10:02:35Z", 40.71408, -74.00603, 0.2),
                point("2024-01-01T10:02:40Z", 40.71409, -74.00604, 0.1),
                point("2024-01-01T10:02:45Z", 40.71410, -74.00604, 0.1),
                point("2024-01-01T10:02:50Z", 40.71411, -74.00605, 0.1),
                point("2024-01-01T10:02:55Z", 40.71412, -74.00605, 0.1),
                point("2024-01-01T10:03:00Z", 40.71413, -74.00606, 0.1),
                point("2024-01-01T10:03:05Z", 40.71414, -74.00606, 0.1));

        TripStopHeuristicsService.TripStopDetection result =
                service.detectTripStopFromRecentWindow(activePoints, config);

        assertTrue(result.isStopDetected());
        assertEquals(2, result.getStoppedClusterStartIndex());
    }

    @Test
    void shouldNotDetectTripStopFromRecentWindow_WhenSlowCrawlIsNotSpatiallyClustered() {
        List<GPSPoint> activePoints = List.of(
                point("2024-01-01T10:00:00Z", 40.7120, -74.0100, 8.0),
                point("2024-01-01T10:01:00Z", 40.7130, -74.0080, 9.0),
                point("2024-01-01T10:02:00Z", 40.7140, -74.0060, 1.0),
                point("2024-01-01T10:02:30Z", 40.7147, -74.0060, 1.0),
                point("2024-01-01T10:03:00Z", 40.7154, -74.0060, 1.0),
                point("2024-01-01T10:03:30Z", 40.7161, -74.0060, 1.0));

        TripStopHeuristicsService.TripStopDetection result =
                service.detectTripStopFromRecentWindow(activePoints, config);

        assertFalse(result.isStopDetected());
    }

    @Test
    void shouldNotDetectTripStopFromRecentWindow_WhenClusterHasFewerThanMinimumPoints() {
        List<GPSPoint> activePoints = List.of(
                point("2024-01-01T10:00:00Z", 40.7120, -74.0100, 8.0),
                point("2024-01-01T10:01:00Z", 40.7130, -74.0080, 0.5),
                point("2024-01-01T10:03:00Z", 40.7130, -74.0080, 0.4));

        TripStopHeuristicsService.TripStopDetection result =
                service.detectTripStopFromRecentWindow(activePoints, config);

        assertFalse(result.isStopDetected());
    }

    @Test
    void shouldDetectTripStopFromRecentWindow_WithTwoStoppedPointsWhenConfigured() {
        TimelineConfig twoPointConfig = TimelineConfig.builder()
                .staypointRadiusMeters(80)
                .staypointVelocityThreshold(2.0)
                .tripArrivalMinPoints(2)
                .tripArrivalDetectionMinDurationSeconds(90)
                .tripSustainedStopMinDurationSeconds(60)
                .build();

        List<GPSPoint> activePoints = List.of(
                point("2024-01-01T10:00:00Z", 40.7120, -74.0100, 9.0),
                point("2024-01-01T10:15:00Z", 40.7140, -74.0060, 0.5),
                point("2024-01-01T10:30:00Z", 40.7140, -74.0060, 0.4));

        TripStopHeuristicsService.TripStopDetection result =
                service.detectTripStopFromRecentWindow(activePoints, twoPointConfig);

        assertTrue(result.isStopDetected());
        assertEquals(1, result.getStoppedClusterStartIndex());
    }

    @Test
    void shouldMatchGapTailArrivalCluster_WhenTailAndPostGapPointLookStationary() {
        List<GPSPoint> activeTripPoints = List.of(
                point("2024-01-01T17:35:00Z", 40.7120, -74.0100, 10.0),
                point("2024-01-01T17:40:00Z", 40.7130, -74.0080, 12.0),
                point("2024-01-01T17:50:00Z", 40.7145, -74.0060, 11.0),
                point("2024-01-01T17:57:06Z", 40.71510, -74.00580, 0.7),
                point("2024-01-01T17:57:26Z", 40.71500, -74.00576, 0.5),
                point("2024-01-01T17:57:46Z", 40.71492, -74.00574, 0.6),
                point("2024-01-01T17:57:57Z", 40.71486, -74.00572, 0.4));
        TripStopHeuristicsService.TailArrivalClusterMatch match =
                service.findGapTailArrivalClusterMatch(
                        activeTripPoints,
                        point("2024-01-02T10:03:03Z", 40.71482, -74.00570, 0.0),
                        config
                );
        assertTrue(match.isMatched());
        assertEquals(3, match.getStartIndex());
        assertTrue(match.getResumeDistanceMeters() <= 80.0);
    }
    @Test
    void shouldNotMatchGapTailArrivalCluster_WhenPostGapPointIsMovingFast() {
        List<GPSPoint> activeTripPoints = List.of(
                point("2024-01-01T17:57:06Z", 40.71510, -74.00580, 0.7),
                point("2024-01-01T17:57:26Z", 40.71500, -74.00576, 0.5),
                point("2024-01-01T17:57:46Z", 40.71492, -74.00574, 0.6),
                point("2024-01-01T17:57:57Z", 40.71486, -74.00572, 0.4));
        TripStopHeuristicsService.TailArrivalClusterMatch match =
                service.findGapTailArrivalClusterMatch(
                        activeTripPoints,
                        point("2024-01-02T10:03:03Z", 40.71482, -74.00570, 6.0),
                        config
                );
        assertFalse(match.isMatched());
    }
    private GPSPoint point(String isoTs, double lat, double lon, double speed) {
        return GPSPoint.builder()
                .timestamp(Instant.parse(isoTs))
                .latitude(lat)
                .longitude(lon)
                .speed(speed)
                .accuracy(10.0)
                .build();
    }
}

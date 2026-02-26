package org.github.tess1o.geopulse.streaming.engine;

import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

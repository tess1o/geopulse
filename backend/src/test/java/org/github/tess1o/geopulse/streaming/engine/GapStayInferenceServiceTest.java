package org.github.tess1o.geopulse.streaming.engine;

import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.ProcessorMode;
import org.github.tess1o.geopulse.streaming.model.domain.UserState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class GapStayInferenceServiceTest {

    private GapStayInferenceService service;
    private TimelineConfig config;

    @BeforeEach
    void setUp() {
        service = new GapStayInferenceService();
        service.tripStopHeuristicsService = new TripStopHeuristicsService();

        config = TimelineConfig.builder()
                .gapStayInferenceEnabled(true)
                .gapStayInferenceMaxGapHours(24)
                .staypointRadiusMeters(80)
                .staypointVelocityThreshold(2.0)
                .tripArrivalMinPoints(3)
                .tripArrivalDetectionMinDurationSeconds(90)
                .build();
    }

    @Test
    void shouldInferContinueExistingStayPlan_WhenStayModeAndSameLocation() {
        UserState state = createUserState(ProcessorMode.CONFIRMED_STAY,
                point("2024-01-01T20:00:00Z", 40.7128, -74.0060, 0.1),
                point("2024-01-01T20:10:00Z", 40.71282, -74.00602, 0.0));

        GapStayInferencePlan plan = service.tryInfer(
                point("2024-01-02T08:00:00Z", 40.71283, -74.00601, 0.0),
                state,
                config,
                Duration.ofHours(12)
        );

        assertTrue(plan.isInferred());
        assertFalse(plan.hasTripToFinalize());
        assertFalse(plan.hasReplacementStayPoints());
    }

    @Test
    void shouldInferReplacementStayPlan_WhenShortLocalInTripExcursionReturnsWithinRadius() {
        UserState state = createUserState(ProcessorMode.IN_TRIP,
                point("2024-01-01T20:00:00Z", 40.71375, -74.0060, 1.2),
                point("2024-01-01T20:00:30Z", 40.71335, -74.0060, 1.0),
                point("2024-01-01T20:01:00Z", 40.71300, -74.0060, 0.8),
                point("2024-01-01T20:01:30Z", 40.71292, -74.0060, 0.4));

        GapStayInferencePlan plan = service.tryInfer(
                point("2024-01-02T08:00:00Z", 40.71284, -74.0060, 0.0),
                state,
                config,
                Duration.ofHours(12)
        );

        assertTrue(plan.isInferred());
        assertFalse(plan.hasTripToFinalize());
        assertTrue(plan.hasReplacementStayPoints());
        assertTrue(plan.getReplacementStayPoints().size() < 4);
    }

    @Test
    void shouldInferTripTailArrivalPlan_WhenTripEndedBeforeGapAndResumesSameLocation() {
        UserState state = createUserState(ProcessorMode.IN_TRIP,
                point("2024-01-01T17:35:00Z", 40.7120, -74.0100, 10.0),
                point("2024-01-01T17:40:00Z", 40.7130, -74.0080, 12.0),
                point("2024-01-01T17:50:00Z", 40.7145, -74.0060, 11.0),
                point("2024-01-01T17:57:06Z", 40.71510, -74.00580, 0.7),
                point("2024-01-01T17:57:26Z", 40.71500, -74.00576, 0.5),
                point("2024-01-01T17:57:46Z", 40.71492, -74.00574, 0.6),
                point("2024-01-01T17:57:57Z", 40.71486, -74.00572, 0.4));

        GapStayInferencePlan plan = service.tryInfer(
                point("2024-01-02T10:03:03Z", 40.71482, -74.00570, 0.0),
                state,
                config,
                Duration.ofHours(16)
        );

        assertTrue(plan.isInferred());
        assertTrue(plan.hasTripToFinalize());
        assertTrue(plan.hasReplacementStayPoints());
        assertTrue(plan.getTripPointsToFinalize().size() >= 2);
        assertTrue(plan.getReplacementStayPoints().size() >= 3);
    }

    @Test
    void shouldNotInfer_WhenGapExceedsConfiguredMaxHours() {
        config.setGapStayInferenceMaxGapHours(4);

        UserState state = createUserState(ProcessorMode.CONFIRMED_STAY,
                point("2024-01-01T20:00:00Z", 40.7128, -74.0060, 0.0));

        GapStayInferencePlan plan = service.tryInfer(
                point("2024-01-02T08:00:00Z", 40.7128, -74.0060, 0.0),
                state,
                config,
                Duration.ofHours(12)
        );

        assertFalse(plan.isInferred());
    }

    private UserState createUserState(ProcessorMode mode, GPSPoint... points) {
        UserState state = new UserState();
        state.setCurrentMode(mode);
        for (GPSPoint point : points) {
            state.addActivePoint(point);
        }
        if (points.length > 0) {
            state.setLastProcessedPoint(points[points.length - 1]);
        }
        return state;
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

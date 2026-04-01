package org.github.tess1o.geopulse.streaming.engine.datagap.rules;

import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.engine.TimelineEventFinalizationService;
import org.github.tess1o.geopulse.streaming.engine.datagap.model.DataGapContext;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.ProcessorMode;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.domain.UserState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class StationaryBoundaryStayGapRuleTest {

    @Mock
    private TimelineEventFinalizationService finalizationService;

    private TimelineConfig config;

    @BeforeEach
    void setUp() {
        config = TimelineConfig.builder()
                .gapStayInferenceEnabled(true)
                .dataGapThresholdSeconds(10800)
                .staypointRadiusMeters(100)
                .staypointVelocityThreshold(3.5)
                .tripArrivalMinPoints(2)
                .build();
    }

    @Test
    void shouldApply_WhenEnabledAndBoundaryLooksStationary() {
        StationaryBoundaryStayGapRule rule = new StationaryBoundaryStayGapRule(
                finalizationService,
                true,
                3L,
                100.0d,
                1.0d
        );
        GPSPoint lastPoint = point("2026-03-30T10:27:59Z", 42.1978710, 35.5798030, 0.70);
        GPSPoint currentPoint = point("2026-03-30T16:53:52Z", 42.1978200, 35.5797800, 0.0);
        UserState state = createUserState(
                point("2026-03-30T10:14:34Z", 42.1935374, 35.5638919, 41.76),
                point("2026-03-30T10:27:47Z", 42.1977320, 35.5796580, 5.70),
                point("2026-03-30T10:27:53Z", 42.1978710, 35.5797640, 1.10),
                lastPoint
        );
        DataGapContext context = new DataGapContext(
                lastPoint,
                currentPoint,
                state,
                config,
                Duration.between(lastPoint.getTimestamp(), currentPoint.getTimestamp())
        );

        when(finalizationService.finalizeTrip(any(), any()))
                .thenReturn(createTrip(Instant.parse("2026-03-30T10:14:34Z")));

        List<TimelineEvent> gapEvents = new ArrayList<>();
        boolean applied = rule.apply(context, gapEvents);

        assertTrue(applied);
        assertEquals(2, gapEvents.size());
        assertTrue(gapEvents.stream().anyMatch(event -> event instanceof Trip));
        assertTrue(gapEvents.stream().anyMatch(event -> event instanceof Stay));
        assertEquals(ProcessorMode.UNKNOWN, state.getCurrentMode());
        verify(finalizationService).finalizeTrip(any(), any());
    }

    @Test
    void shouldNotApply_WhenRuleDisabled() {
        StationaryBoundaryStayGapRule rule = new StationaryBoundaryStayGapRule(
                finalizationService,
                false,
                3L,
                100.0d,
                1.0d
        );
        GPSPoint lastPoint = point("2026-03-30T10:27:59Z", 42.1978710, 35.5798030, 0.70);
        GPSPoint currentPoint = point("2026-03-30T16:53:52Z", 42.1978200, 35.5797800, 0.0);
        UserState state = createUserState(
                point("2026-03-30T10:14:34Z", 42.1935374, 35.5638919, 41.76),
                point("2026-03-30T10:27:47Z", 42.1977320, 35.5796580, 5.70),
                point("2026-03-30T10:27:53Z", 42.1978710, 35.5797640, 1.10),
                lastPoint
        );
        DataGapContext context = new DataGapContext(
                lastPoint,
                currentPoint,
                state,
                config,
                Duration.between(lastPoint.getTimestamp(), currentPoint.getTimestamp())
        );

        List<TimelineEvent> gapEvents = new ArrayList<>();
        boolean applied = rule.apply(context, gapEvents);

        assertFalse(applied);
        assertTrue(gapEvents.isEmpty());
        assertEquals(ProcessorMode.IN_TRIP, state.getCurrentMode());
        verifyNoInteractions(finalizationService);
    }

    @Test
    void shouldNotApply_WhenBoundaryDistanceExceedsThreshold() {
        StationaryBoundaryStayGapRule rule = new StationaryBoundaryStayGapRule(
                finalizationService,
                true,
                3L,
                50.0d,
                1.0d
        );
        GPSPoint lastPoint = point("2026-03-30T10:27:59Z", 42.1978710, 35.5798030, 0.70);
        GPSPoint currentPoint = point("2026-03-30T16:53:52Z", 42.2038710, 35.5798030, 0.0);
        UserState state = createUserState(
                point("2026-03-30T10:14:34Z", 42.1935374, 35.5638919, 41.76),
                point("2026-03-30T10:27:47Z", 42.1977320, 35.5796580, 5.70),
                point("2026-03-30T10:27:53Z", 42.1978710, 35.5797640, 1.10),
                lastPoint
        );
        DataGapContext context = new DataGapContext(
                lastPoint,
                currentPoint,
                state,
                config,
                Duration.between(lastPoint.getTimestamp(), currentPoint.getTimestamp())
        );

        List<TimelineEvent> gapEvents = new ArrayList<>();
        boolean applied = rule.apply(context, gapEvents);

        assertFalse(applied);
        assertTrue(gapEvents.isEmpty());
        assertEquals(ProcessorMode.IN_TRIP, state.getCurrentMode());
        verifyNoInteractions(finalizationService);
    }

    private UserState createUserState(GPSPoint... points) {
        UserState state = new UserState();
        state.setCurrentMode(ProcessorMode.IN_TRIP);
        for (GPSPoint point : points) {
            state.addActivePoint(point);
        }
        state.setLastProcessedPoint(points[points.length - 1]);
        return state;
    }

    private GPSPoint point(String timestamp, double latitude, double longitude, double speed) {
        return GPSPoint.builder()
                .timestamp(Instant.parse(timestamp))
                .latitude(latitude)
                .longitude(longitude)
                .speed(speed)
                .accuracy(10.0)
                .build();
    }

    private Trip createTrip(Instant startTime) {
        return Trip.builder()
                .startTime(startTime)
                .duration(Duration.ofMinutes(12))
                .distanceMeters(1200)
                .build();
    }
}

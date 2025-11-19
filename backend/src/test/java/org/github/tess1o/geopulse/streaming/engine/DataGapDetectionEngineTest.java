package org.github.tess1o.geopulse.streaming.engine;

import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.ProcessorMode;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.UserState;
import org.github.tess1o.geopulse.streaming.service.StreamingDataGapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DataGapDetectionEngine, focusing on gap stay inference feature.
 */
@ExtendWith(MockitoExtension.class)
class DataGapDetectionEngineTest {

    @InjectMocks
    private DataGapDetectionEngine engine;

    @Mock
    private TimelineEventFinalizationService finalizationService;

    @Mock
    private StreamingDataGapService dataGapService;

    private TimelineConfig config;

    @BeforeEach
    void setUp() {
        config = TimelineConfig.builder()
                .staypointRadiusMeters(50)
                .staypointMinDurationMinutes(7)
                .dataGapThresholdSeconds(10800) // 3 hours
                .dataGapMinDurationSeconds(1800) // 30 minutes
                .gapStayInferenceEnabled(false) // disabled by default
                .gapStayInferenceMaxGapHours(24)
                .build();
    }

    // ============== Tests for gap stay inference disabled ==============

    @Test
    void shouldCreateDataGap_WhenInferenceDisabled() {
        // Given: Inference is disabled (default)
        UserState userState = createUserStateWithPoints(ProcessorMode.POTENTIAL_STAY,
                createGpsPoint(Instant.parse("2024-01-01T10:00:00Z"), 40.7128, -74.0060));

        GPSPoint currentPoint = createGpsPoint(Instant.parse("2024-01-01T14:00:00Z"), 40.7128, -74.0060); // same location

        when(dataGapService.shouldCreateDataGap(any(), any(), any())).thenReturn(true);
        when(finalizationService.finalizeStayWithoutLocation(any(), any()))
                .thenReturn(createStay(Instant.parse("2024-01-01T10:00:00Z")));

        // When
        List<TimelineEvent> events = engine.checkForDataGap(currentPoint, userState, config);

        // Then: Should create a gap even though locations are the same
        assertEquals(2, events.size()); // finalized stay + data gap
        assertTrue(events.stream().anyMatch(e -> e instanceof DataGap));
    }

    @Test
    void shouldNotCreateGap_WhenNoGapDetected() {
        // Given: No gap detected
        UserState userState = createUserStateWithPoints(ProcessorMode.POTENTIAL_STAY,
                createGpsPoint(Instant.parse("2024-01-01T10:00:00Z"), 40.7128, -74.0060));

        GPSPoint currentPoint = createGpsPoint(Instant.parse("2024-01-01T10:05:00Z"), 40.7128, -74.0060);

        when(dataGapService.shouldCreateDataGap(any(), any(), any())).thenReturn(false);

        // When
        List<TimelineEvent> events = engine.checkForDataGap(currentPoint, userState, config);

        // Then
        assertTrue(events.isEmpty());
    }

    // ============== Tests for gap stay inference enabled ==============

    @Test
    void shouldInferStay_WhenSameLocation_AndInferenceEnabled() {
        // Given: Inference enabled and same location
        config.setGapStayInferenceEnabled(true);

        UserState userState = createUserStateWithPoints(ProcessorMode.POTENTIAL_STAY,
                createGpsPoint(Instant.parse("2024-01-01T20:00:00Z"), 40.7128, -74.0060));

        // 12-hour gap (overnight), same location
        GPSPoint currentPoint = createGpsPoint(Instant.parse("2024-01-02T08:00:00Z"), 40.7128, -74.0060);

        when(dataGapService.shouldCreateDataGap(any(), any(), any())).thenReturn(true);

        // When
        List<TimelineEvent> events = engine.checkForDataGap(currentPoint, userState, config);

        // Then: Should NOT create gap - inference applied
        assertTrue(events.isEmpty());
        // State should NOT be reset (checked via mode still being POTENTIAL_STAY)
        assertEquals(ProcessorMode.POTENTIAL_STAY, userState.getCurrentMode());
    }

    @Test
    void shouldInferStay_WhenConfirmedStay_AndSameLocation() {
        // Given: CONFIRMED_STAY mode with inference enabled
        config.setGapStayInferenceEnabled(true);

        UserState userState = createUserStateWithPoints(ProcessorMode.CONFIRMED_STAY,
                createGpsPoint(Instant.parse("2024-01-01T18:00:00Z"), 40.7128, -74.0060),
                createGpsPoint(Instant.parse("2024-01-01T18:10:00Z"), 40.7128, -74.0060));

        // 14-hour gap (overnight), same location
        GPSPoint currentPoint = createGpsPoint(Instant.parse("2024-01-02T08:00:00Z"), 40.7129, -74.0061); // within 50m

        when(dataGapService.shouldCreateDataGap(any(), any(), any())).thenReturn(true);

        // When
        List<TimelineEvent> events = engine.checkForDataGap(currentPoint, userState, config);

        // Then: Should infer stay
        assertTrue(events.isEmpty());
        assertEquals(ProcessorMode.CONFIRMED_STAY, userState.getCurrentMode());
    }

    @Test
    void shouldCreateGap_WhenDifferentLocation() {
        // Given: Inference enabled but different location
        config.setGapStayInferenceEnabled(true);

        UserState userState = createUserStateWithPoints(ProcessorMode.POTENTIAL_STAY,
                createGpsPoint(Instant.parse("2024-01-01T20:00:00Z"), 40.7128, -74.0060)); // NYC

        // 12-hour gap, DIFFERENT location (Los Angeles - clearly outside 50m radius)
        GPSPoint currentPoint = createGpsPoint(Instant.parse("2024-01-02T08:00:00Z"), 34.0522, -118.2437);

        when(dataGapService.shouldCreateDataGap(any(), any(), any())).thenReturn(true);
        when(finalizationService.finalizeStayWithoutLocation(any(), any()))
                .thenReturn(createStay(Instant.parse("2024-01-01T20:00:00Z")));

        // When
        List<TimelineEvent> events = engine.checkForDataGap(currentPoint, userState, config);

        // Then: Should create gap because locations are different
        assertEquals(2, events.size());
        assertTrue(events.stream().anyMatch(e -> e instanceof DataGap));
    }

    @Test
    void shouldCreateGap_WhenLocationJustOutsideRadius() {
        // Given: Location just outside 50m radius
        config.setGapStayInferenceEnabled(true);

        UserState userState = createUserStateWithPoints(ProcessorMode.POTENTIAL_STAY,
                createGpsPoint(Instant.parse("2024-01-01T20:00:00Z"), 40.7128, -74.0060));

        // Point approximately 60m away (outside 50m radius)
        // ~0.0005 degrees ≈ 55m at this latitude
        GPSPoint currentPoint = createGpsPoint(Instant.parse("2024-01-02T08:00:00Z"), 40.7133, -74.0060);

        when(dataGapService.shouldCreateDataGap(any(), any(), any())).thenReturn(true);
        when(finalizationService.finalizeStayWithoutLocation(any(), any()))
                .thenReturn(createStay(Instant.parse("2024-01-01T20:00:00Z")));

        // When
        List<TimelineEvent> events = engine.checkForDataGap(currentPoint, userState, config);

        // Then: Should create gap because distance > radius
        assertEquals(2, events.size());
        assertTrue(events.stream().anyMatch(e -> e instanceof DataGap));
    }

    // ============== Tests for IN_TRIP mode (should always create gap) ==============

    @Test
    void shouldCreateGap_WhenInTripMode_EvenIfSameLocation() {
        // Given: IN_TRIP mode (user was traveling)
        config.setGapStayInferenceEnabled(true);

        UserState userState = createUserStateWithPoints(ProcessorMode.IN_TRIP,
                createGpsPoint(Instant.parse("2024-01-01T20:00:00Z"), 40.7128, -74.0060));

        // Same location after gap
        GPSPoint currentPoint = createGpsPoint(Instant.parse("2024-01-02T08:00:00Z"), 40.7128, -74.0060);

        when(dataGapService.shouldCreateDataGap(any(), any(), any())).thenReturn(true);
        when(finalizationService.finalizeTripForGap(any(), any(), any()))
                .thenReturn(null);

        // When
        List<TimelineEvent> events = engine.checkForDataGap(currentPoint, userState, config);

        // Then: Should create gap - inference doesn't apply to IN_TRIP
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof DataGap);
    }

    // ============== Tests for max gap hours ==============

    @Test
    void shouldCreateGap_WhenGapExceedsMaxHours() {
        // Given: Gap exceeds max hours
        config.setGapStayInferenceEnabled(true);
        config.setGapStayInferenceMaxGapHours(12); // Only allow up to 12 hours

        UserState userState = createUserStateWithPoints(ProcessorMode.POTENTIAL_STAY,
                createGpsPoint(Instant.parse("2024-01-01T10:00:00Z"), 40.7128, -74.0060));

        // 48-hour gap (exceeds 12-hour max), same location
        GPSPoint currentPoint = createGpsPoint(Instant.parse("2024-01-03T10:00:00Z"), 40.7128, -74.0060);

        when(dataGapService.shouldCreateDataGap(any(), any(), any())).thenReturn(true);
        when(finalizationService.finalizeStayWithoutLocation(any(), any()))
                .thenReturn(createStay(Instant.parse("2024-01-01T10:00:00Z")));

        // When
        List<TimelineEvent> events = engine.checkForDataGap(currentPoint, userState, config);

        // Then: Should create gap because duration exceeds max
        assertEquals(2, events.size());
        assertTrue(events.stream().anyMatch(e -> e instanceof DataGap));
    }

    @Test
    void shouldInferStay_WhenGapWithinMaxHours() {
        // Given: Gap is within max hours
        config.setGapStayInferenceEnabled(true);
        config.setGapStayInferenceMaxGapHours(24);

        UserState userState = createUserStateWithPoints(ProcessorMode.POTENTIAL_STAY,
                createGpsPoint(Instant.parse("2024-01-01T20:00:00Z"), 40.7128, -74.0060));

        // 12-hour gap (within 24-hour max), same location
        GPSPoint currentPoint = createGpsPoint(Instant.parse("2024-01-02T08:00:00Z"), 40.7128, -74.0060);

        when(dataGapService.shouldCreateDataGap(any(), any(), any())).thenReturn(true);

        // When
        List<TimelineEvent> events = engine.checkForDataGap(currentPoint, userState, config);

        // Then: Should infer stay
        assertTrue(events.isEmpty());
    }

    // ============== Edge cases ==============

    @Test
    void shouldCreateGap_WhenNoActivePoints() {
        // Given: No active points to compare
        config.setGapStayInferenceEnabled(true);

        UserState userState = new UserState();
        userState.setCurrentMode(ProcessorMode.UNKNOWN);
        userState.setLastProcessedPoint(createGpsPoint(Instant.parse("2024-01-01T10:00:00Z"), 40.7128, -74.0060));

        GPSPoint currentPoint = createGpsPoint(Instant.parse("2024-01-01T14:00:00Z"), 40.7128, -74.0060);

        when(dataGapService.shouldCreateDataGap(any(), any(), any())).thenReturn(true);

        // When
        List<TimelineEvent> events = engine.checkForDataGap(currentPoint, userState, config);

        // Then: Should create gap because no active points for comparison
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof DataGap);
    }

    @Test
    void shouldNotCheckGap_WhenNoLastProcessedPoint() {
        // Given: First point (no last processed)
        config.setGapStayInferenceEnabled(true);

        UserState userState = new UserState();
        GPSPoint currentPoint = createGpsPoint(Instant.parse("2024-01-01T10:00:00Z"), 40.7128, -74.0060);

        // When
        List<TimelineEvent> events = engine.checkForDataGap(currentPoint, userState, config);

        // Then: No gap check when no previous point
        assertTrue(events.isEmpty());
    }

    @Test
    void shouldCreateGap_WhenUnknownMode() {
        // Given: UNKNOWN mode
        config.setGapStayInferenceEnabled(true);

        UserState userState = createUserStateWithPoints(ProcessorMode.UNKNOWN,
                createGpsPoint(Instant.parse("2024-01-01T10:00:00Z"), 40.7128, -74.0060));

        GPSPoint currentPoint = createGpsPoint(Instant.parse("2024-01-01T14:00:00Z"), 40.7128, -74.0060);

        when(dataGapService.shouldCreateDataGap(any(), any(), any())).thenReturn(true);

        // When
        List<TimelineEvent> events = engine.checkForDataGap(currentPoint, userState, config);

        // Then: Should create gap - UNKNOWN mode doesn't qualify for inference
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof DataGap);
    }

    // ============== Helper methods ==============

    private GPSPoint createGpsPoint(Instant timestamp, double lat, double lon) {
        return GPSPoint.builder()
                .timestamp(timestamp)
                .latitude(lat)
                .longitude(lon)
                .speed(0.0)
                .accuracy(10.0)
                .build();
    }

    private UserState createUserStateWithPoints(ProcessorMode mode, GPSPoint... points) {
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

    private Stay createStay(Instant startTime) {
        return Stay.builder()
                .startTime(startTime)
                .duration(Duration.ofMinutes(10))
                .latitude(40.7128)
                .longitude(-74.0060)
                .build();
    }
}

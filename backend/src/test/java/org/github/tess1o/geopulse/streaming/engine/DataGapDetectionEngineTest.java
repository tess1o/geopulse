package org.github.tess1o.geopulse.streaming.engine;

import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.ProcessorMode;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.domain.UserState;
import org.github.tess1o.geopulse.streaming.service.StreamingDataGapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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

    @Spy
    private GapStayInferenceService gapStayInferenceService = new GapStayInferenceService();

    private TimelineConfig config;

    @BeforeEach
    void setUp() {
        gapStayInferenceService.tripStopHeuristicsService = new TripStopHeuristicsService();
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

    // ============== Tests for IN_TRIP mode (with local-excursion fallback) ==============

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

    @Test
    void shouldInferStay_WhenInTripModeIsShortLocalExcursion_AndReturnsWithinRadius() {
        // Given: Short unfinished trip that returns near the same place before GPS goes silent
        config.setGapStayInferenceEnabled(true);
        config.setStaypointRadiusMeters(80);

        GPSPoint tripFar = createGpsPoint(Instant.parse("2024-01-01T20:00:00Z"), 40.71375, -74.0060);
        GPSPoint tripReturn1 = createGpsPoint(Instant.parse("2024-01-01T20:00:30Z"), 40.71335, -74.0060);
        GPSPoint tripReturn2 = createGpsPoint(Instant.parse("2024-01-01T20:01:00Z"), 40.71300, -74.0060);
        GPSPoint tripEnd = createGpsPoint(Instant.parse("2024-01-01T20:01:30Z"), 40.71292, -74.0060);

        UserState userState = createUserStateWithPoints(ProcessorMode.IN_TRIP, tripFar, tripReturn1, tripReturn2, tripEnd);

        // Resume point after overnight gap is back within 80m of the unfinished trip endpoint
        GPSPoint currentPoint = createGpsPoint(Instant.parse("2024-01-02T08:00:00Z"), 40.71284, -74.0060);

        when(dataGapService.shouldCreateDataGap(any(), any(), any())).thenReturn(true);

        // When
        List<TimelineEvent> events = engine.checkForDataGap(currentPoint, userState, config);

        // Then: Treat as continuous stay context (skip gap, convert pending local trip back to stay)
        assertTrue(events.isEmpty());
        assertEquals(ProcessorMode.CONFIRMED_STAY, userState.getCurrentMode());
        assertTrue(userState.getActivePoints().size() < 4, "Far excursion point should be trimmed from stay seed");
    }

    @Test
    void shouldCreateGap_WhenInTripModeLocalButPendingTripIsTooLong() {
        // Given: A local-looking IN_TRIP that lasted too long to safely collapse into a stay
        config.setGapStayInferenceEnabled(true);
        config.setStaypointRadiusMeters(80);

        UserState userState = createUserStateWithPoints(ProcessorMode.IN_TRIP,
                createGpsPoint(Instant.parse("2024-01-01T20:00:00Z"), 40.71310, -74.0060),
                createGpsPoint(Instant.parse("2024-01-01T20:45:00Z"), 40.71292, -74.0060));

        GPSPoint currentPoint = createGpsPoint(Instant.parse("2024-01-02T08:00:00Z"), 40.71284, -74.0060);

        when(dataGapService.shouldCreateDataGap(any(), any(), any())).thenReturn(true);
        when(finalizationService.finalizeTripForGap(any(), any(), any())).thenReturn(null);

        // When
        List<TimelineEvent> events = engine.checkForDataGap(currentPoint, userState, config);

        // Then
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof DataGap);
        assertEquals(ProcessorMode.UNKNOWN, userState.getCurrentMode()); // reset after gap creation
    }

    @Test
    void shouldFinalizeTripAndInferStay_WhenInTripTailLooksLikeArrival_AndResumesSameLocationAfterGap() {
        // Given: Real trip followed by slow clustered tail before GPS gap (arrival-like)
        config.setGapStayInferenceEnabled(true);
        config.setStaypointRadiusMeters(80);
        config.setStaypointVelocityThreshold(2.0);
        config.setTripArrivalMinPoints(3);
        config.setTripArrivalDetectionMinDurationSeconds(90); // default-like, gap heuristic uses relaxed threshold

        UserState userState = createUserStateWithPoints(ProcessorMode.IN_TRIP,
                createGpsPoint(Instant.parse("2024-01-01T17:35:00Z"), 40.7120, -74.0100, 10.0),
                createGpsPoint(Instant.parse("2024-01-01T17:40:00Z"), 40.7130, -74.0080, 12.0),
                createGpsPoint(Instant.parse("2024-01-01T17:50:00Z"), 40.7145, -74.0060, 11.0),
                createGpsPoint(Instant.parse("2024-01-01T17:57:06Z"), 40.71510, -74.00580, 0.7),
                createGpsPoint(Instant.parse("2024-01-01T17:57:26Z"), 40.71500, -74.00576, 0.5),
                createGpsPoint(Instant.parse("2024-01-01T17:57:46Z"), 40.71492, -74.00574, 0.6),
                createGpsPoint(Instant.parse("2024-01-01T17:57:57Z"), 40.71486, -74.00572, 0.4));

        // First point after overnight gap is stationary and resumes within stay radius of trip tail
        GPSPoint currentPoint = createGpsPoint(Instant.parse("2024-01-02T10:03:03Z"), 40.71482, -74.00570, 0.0);

        when(dataGapService.shouldCreateDataGap(any(), any(), any())).thenReturn(true);
        when(finalizationService.finalizeTrip(any(), any())).thenReturn(createTrip(Instant.parse("2024-01-01T17:35:00Z")));

        // When
        List<TimelineEvent> events = engine.checkForDataGap(currentPoint, userState, config);

        // Then: Trip part should be finalized, no DataGap, state continues as stay
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof Trip);
        assertFalse(events.stream().anyMatch(e -> e instanceof DataGap));
        assertEquals(ProcessorMode.CONFIRMED_STAY, userState.getCurrentMode());
        assertTrue(userState.getActivePoints().size() >= 3, "Tail stop cluster should seed stay state");
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

    private GPSPoint createGpsPoint(Instant timestamp, double lat, double lon, double speed) {
        return GPSPoint.builder()
                .timestamp(timestamp)
                .latitude(lat)
                .longitude(lon)
                .speed(speed)
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

    private Trip createTrip(Instant startTime) {
        return Trip.builder()
                .startTime(startTime)
                .duration(Duration.ofMinutes(22))
                .distanceMeters(1500)
                .build();
    }
}

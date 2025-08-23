package org.github.tess1o.geopulse.timeline.service.redesign;

import org.github.tess1o.geopulse.timeline.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.timeline.mapper.TimelinePersistenceMapper;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.timeline.model.TimelineDataGapDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;
import org.github.tess1o.geopulse.timeline.model.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineTripDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for TimelineAssembler.
 * Tests timeline combining, previous context prepending, and cross-day gap detection.
 */
@ExtendWith(MockitoExtension.class)
class TimelineAssemblerTest {

    @InjectMocks
    TimelineAssembler timelineAssembler;

    @Mock
    TimelineEventRetriever timelineEventRetriever;

    @Mock
    TimelineConfigurationProvider configurationProvider;

    @Mock
    TimelinePersistenceMapper persistenceMapper;

    private UUID testUserId;
    private TimelineConfig mockConfig;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        
        // Set up default configuration
        mockConfig = new TimelineConfig();
        mockConfig.setDataGapThresholdSeconds(10800); // 3 hours
        mockConfig.setDataGapMinDurationSeconds(1800); // 30 minutes
        // Don't set up global stubbing - do it per test as needed
    }

    @Test
    @DisplayName("Timeline combining: Merge two timelines chronologically")
    void testCombineTimelines_MergeChronologically() {
        // Configure gap detection
        when(configurationProvider.getConfigurationForUser(testUserId)).thenReturn(mockConfig);
        
        // Create past timeline
        MovementTimelineDTO pastTimeline = new MovementTimelineDTO(testUserId);
        TimelineStayLocationDTO pastStay = createStayAt(parseInstant("2024-08-21T10:00:00Z"), "Past Stay", 120);
        pastTimeline.getStays().add(pastStay);

        // Create today timeline
        MovementTimelineDTO todayTimeline = new MovementTimelineDTO(testUserId);
        TimelineStayLocationDTO todayStay = createStayAt(parseInstant("2024-08-22T09:00:00Z"), "Today Stay", 180);
        todayTimeline.getStays().add(todayStay);

        MovementTimelineDTO result = timelineAssembler.combineTimelines(pastTimeline, todayTimeline, testUserId);

        // Verify combining
        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
        assertEquals(2, result.getStaysCount());
        
        // Verify chronological order (past stay should come first)
        assertEquals("Past Stay", result.getStays().get(0).getLocationName());
        assertEquals("Today Stay", result.getStays().get(1).getLocationName());
        
        assertNotNull(result.getLastUpdated());
    }

    @Test
    @DisplayName("Cross-day gap detection: Detect gap between past and today timelines")
    void testCombineTimelines_DetectCrossDayGap() {
        // Configure gap detection
        when(configurationProvider.getConfigurationForUser(testUserId)).thenReturn(mockConfig);
        
        // Past timeline ending at 6 PM
        MovementTimelineDTO pastTimeline = new MovementTimelineDTO(testUserId);
        TimelineStayLocationDTO pastStay = createStayAt(parseInstant("2024-08-21T14:00:00Z"), "Past Stay", 240); // 2 PM - 6 PM
        pastTimeline.getStays().add(pastStay);

        // Today timeline starting at 10 AM (16-hour gap)
        MovementTimelineDTO todayTimeline = new MovementTimelineDTO(testUserId);
        TimelineStayLocationDTO todayStay = createStayAt(parseInstant("2024-08-22T10:00:00Z"), "Today Stay", 120);
        todayTimeline.getStays().add(todayStay);

        MovementTimelineDTO result = timelineAssembler.combineTimelines(pastTimeline, todayTimeline, testUserId);

        // Verify cross-day gap was detected (16 hours > 3 hour threshold)
        assertEquals(1, result.getDataGapsCount());
        
        TimelineDataGapDTO gap = result.getDataGaps().get(0);
        assertEquals(parseInstant("2024-08-21T18:00:00Z"), gap.getStartTime()); // 6 PM (end of past stay)
        assertEquals(parseInstant("2024-08-22T10:00:00Z"), gap.getEndTime());   // 10 AM (start of today)
        assertEquals(16 * 60, gap.getDurationMinutes()); // 16 hours in minutes
    }

    @Test
    @DisplayName("Cross-day gap detection: No gap when activities are continuous")
    void testCombineTimelines_NoCrossDayGapWhenContinuous() {
        // Configure gap detection
        when(configurationProvider.getConfigurationForUser(testUserId)).thenReturn(mockConfig);
        
        // Past timeline ending at 11 PM  
        MovementTimelineDTO pastTimeline = new MovementTimelineDTO(testUserId);
        TimelineStayLocationDTO pastStay = createStayAt(parseInstant("2024-08-21T20:00:00Z"), "Past Stay", 180); // 8 PM - 11 PM
        pastTimeline.getStays().add(pastStay);

        // Today timeline starting at midnight (1-hour gap, below threshold)
        MovementTimelineDTO todayTimeline = new MovementTimelineDTO(testUserId);
        TimelineStayLocationDTO todayStay = createStayAt(parseInstant("2024-08-22T00:00:00Z"), "Today Stay", 120);
        todayTimeline.getStays().add(todayStay);

        MovementTimelineDTO result = timelineAssembler.combineTimelines(pastTimeline, todayTimeline, testUserId);

        // Verify no cross-day gap was detected (1 hour < 3 hour threshold)
        assertEquals(0, result.getDataGapsCount());
        assertEquals(2, result.getStaysCount());
    }

    @Test
    @DisplayName("Cross-day gap detection: Handle missing activity timestamps")
    void testCombineTimelines_HandleMissingActivityTimestamps() {
        // Configure gap detection
        when(configurationProvider.getConfigurationForUser(testUserId)).thenReturn(mockConfig);
        
        // Empty past timeline
        MovementTimelineDTO pastTimeline = new MovementTimelineDTO(testUserId);

        // Today timeline with activities
        MovementTimelineDTO todayTimeline = new MovementTimelineDTO(testUserId);
        TimelineStayLocationDTO todayStay = createStayAt(parseInstant("2024-08-22T10:00:00Z"), "Today Stay", 120);
        todayTimeline.getStays().add(todayStay);

        MovementTimelineDTO result = timelineAssembler.combineTimelines(pastTimeline, todayTimeline, testUserId);

        // Should handle gracefully without throwing exceptions
        assertNotNull(result);
        assertEquals(1, result.getStaysCount());
        assertEquals(0, result.getDataGapsCount()); // No gap detected due to missing past activity
    }

    @Test
    @DisplayName("Timeline enhancement: Prepend previous context stay")
    void testEnhanceTimeline_PrependPreviousStay() {
        // Set up previous stay event
        TimelineEventRetriever.TimelineEvent previousStay = new TimelineEventRetriever.TimelineEvent(
            TimelineEventRetriever.TimelineEventType.STAY,
            123L,
            parseInstant("2024-08-21T20:00:00Z"), // 8 PM
            4 * 3600L // 4 hours duration
        );
        when(timelineEventRetriever.findLatestEventBefore(eq(testUserId), any())).thenReturn(Optional.of(previousStay));

        // Create timeline with stay starting at 10 AM
        MovementTimelineDTO timeline = new MovementTimelineDTO(testUserId);
        TimelineStayLocationDTO currentStay = createStayAt(parseInstant("2024-08-22T10:00:00Z"), "Current Stay", 120);
        timeline.getStays().add(currentStay);

        Instant requestStart = parseInstant("2024-08-22T08:00:00Z"); // 8 AM

        MovementTimelineDTO result = timelineAssembler.enhanceTimeline(timeline, testUserId, requestStart, parseInstant("2024-08-22T18:00:00Z"));

        // Verify previous context was prepended
        assertEquals(2, result.getStaysCount());
        
        TimelineStayLocationDTO prependedStay = result.getStays().get(0);
        assertEquals(parseInstant("2024-08-21T20:00:00Z"), prependedStay.getTimestamp());
        assertEquals("Previous Context", prependedStay.getLocationName());
        
        // Duration should be adjusted to reach the current stay start time
        long expectedDurationMinutes = (10 - (-14)) * 60; // From 8 PM yesterday to 10 AM today = 14 hours
        assertEquals(14 * 60, prependedStay.getStayDuration());
    }

    @Test
    @DisplayName("Timeline enhancement: Prepend previous context trip")
    void testEnhanceTimeline_PrependPreviousTrip() {
        // Set up previous trip event
        TimelineEventRetriever.TimelineEvent previousTrip = new TimelineEventRetriever.TimelineEvent(
            TimelineEventRetriever.TimelineEventType.TRIP,
            456L,
            parseInstant("2024-08-21T15:00:00Z"), // 3 PM
            2 * 3600L // 2 hours duration
        );
        when(timelineEventRetriever.findLatestEventBefore(eq(testUserId), any())).thenReturn(Optional.of(previousTrip));

        // Create timeline with stay starting at 10 AM
        MovementTimelineDTO timeline = new MovementTimelineDTO(testUserId);
        TimelineStayLocationDTO currentStay = createStayAt(parseInstant("2024-08-22T10:00:00Z"), "Current Stay", 120);
        timeline.getStays().add(currentStay);

        Instant requestStart = parseInstant("2024-08-22T08:00:00Z");

        MovementTimelineDTO result = timelineAssembler.enhanceTimeline(timeline, testUserId, requestStart, parseInstant("2024-08-22T18:00:00Z"));

        // Verify previous trip was prepended
        assertEquals(1, result.getStaysCount());
        assertEquals(1, result.getTripsCount());
        
        TimelineTripDTO prependedTrip = result.getTrips().get(0);
        assertEquals(parseInstant("2024-08-21T15:00:00Z"), prependedTrip.getTimestamp());
        assertEquals("Previous Context", prependedTrip.getMovementType());
        
        // Duration should be adjusted to reach the current stay
        long expectedDurationMinutes = (10 + 24 - 15) * 60; // From 3 PM yesterday to 10 AM today = 19 hours
        assertEquals(19 * 60, prependedTrip.getTripDuration());
    }

    @Test
    @DisplayName("Timeline enhancement: Prepend previous context data gap")
    void testEnhanceTimeline_PrependPreviousDataGap() {
        // Set up previous data gap event
        TimelineEventRetriever.TimelineEvent previousGap = new TimelineEventRetriever.TimelineEvent(
            TimelineEventRetriever.TimelineEventType.DATA_GAP,
            789L,
            parseInstant("2024-08-21T22:00:00Z"), // 10 PM
            6 * 3600L // 6 hours duration
        );
        when(timelineEventRetriever.findLatestEventBefore(eq(testUserId), any())).thenReturn(Optional.of(previousGap));

        // Create timeline with stay starting at 10 AM
        MovementTimelineDTO timeline = new MovementTimelineDTO(testUserId);
        TimelineStayLocationDTO currentStay = createStayAt(parseInstant("2024-08-22T10:00:00Z"), "Current Stay", 120);
        timeline.getStays().add(currentStay);

        Instant requestStart = parseInstant("2024-08-22T08:00:00Z");

        MovementTimelineDTO result = timelineAssembler.enhanceTimeline(timeline, testUserId, requestStart, parseInstant("2024-08-22T18:00:00Z"));

        // Verify previous data gap was prepended
        assertEquals(1, result.getStaysCount());
        assertEquals(1, result.getDataGapsCount());
        
        TimelineDataGapDTO prependedGap = result.getDataGaps().get(0);
        assertEquals(parseInstant("2024-08-21T22:00:00Z"), prependedGap.getStartTime());
        assertEquals(parseInstant("2024-08-22T10:00:00Z"), prependedGap.getEndTime());
        
        // Duration should be adjusted to reach the current stay  
        long expectedDurationMinutes = (10 + 24 - 22) * 60; // From 10 PM yesterday to 10 AM today = 12 hours
        assertEquals(12 * 60, prependedGap.getDurationMinutes());
    }

    @Test
    @DisplayName("Timeline enhancement: Skip prepending when no previous context")
    void testEnhanceTimeline_NoPreviousContext() {
        // No previous event found
        when(timelineEventRetriever.findLatestEventBefore(eq(testUserId), any())).thenReturn(Optional.empty());

        MovementTimelineDTO timeline = new MovementTimelineDTO(testUserId);
        TimelineStayLocationDTO currentStay = createStayAt(parseInstant("2024-08-22T10:00:00Z"), "Current Stay", 120);
        timeline.getStays().add(currentStay);

        Instant requestStart = parseInstant("2024-08-22T08:00:00Z");

        MovementTimelineDTO result = timelineAssembler.enhanceTimeline(timeline, testUserId, requestStart, parseInstant("2024-08-22T18:00:00Z"));

        // Should return original timeline unchanged
        assertEquals(1, result.getStaysCount());
        assertEquals(0, result.getTripsCount());
        assertEquals(0, result.getDataGapsCount());
        assertEquals(currentStay, result.getStays().get(0));
    }

    @Test
    @DisplayName("Timeline enhancement: Skip prepending for data-gap-only timelines")
    void testEnhanceTimeline_SkipPrependingForDataGapOnlyTimelines() {
        // Create timeline with only data gaps (no actual GPS-based activities)
        // Previous context prepending should be skipped entirely
        MovementTimelineDTO timeline = new MovementTimelineDTO(testUserId);
        TimelineDataGapDTO dataGap = new TimelineDataGapDTO(
            parseInstant("2024-08-22T10:00:00Z"),
            parseInstant("2024-08-22T14:00:00Z")
        );
        timeline.getDataGaps().add(dataGap);

        Instant requestStart = parseInstant("2024-08-22T08:00:00Z");

        MovementTimelineDTO result = timelineAssembler.enhanceTimeline(timeline, testUserId, requestStart, parseInstant("2024-08-22T18:00:00Z"));

        // Should not prepend previous context for data-gap-only timelines
        assertEquals(0, result.getStaysCount());
        assertEquals(0, result.getTripsCount());
        assertEquals(1, result.getDataGapsCount());
        assertEquals(dataGap, result.getDataGaps().get(0));
    }

    @Test
    @DisplayName("Cross-day gap configuration: Respect user-specific thresholds")
    void testCombineTimelines_RespectUserConfiguration() {
        // Configure custom thresholds (1 hour threshold, 30 min minimum)
        mockConfig.setDataGapThresholdSeconds(3600);     // 1 hour threshold
        mockConfig.setDataGapMinDurationSeconds(1800);   // 30 minutes minimum
        when(configurationProvider.getConfigurationForUser(testUserId)).thenReturn(mockConfig);

        // Past timeline ending at 10 PM
        MovementTimelineDTO pastTimeline = new MovementTimelineDTO(testUserId);
        TimelineStayLocationDTO pastStay = createStayAt(parseInstant("2024-08-21T20:00:00Z"), "Past Stay", 120); // 8 PM - 10 PM
        pastTimeline.getStays().add(pastStay);

        // Today timeline starting at 11:30 PM (1.5 hour gap)
        MovementTimelineDTO todayTimeline = new MovementTimelineDTO(testUserId);
        TimelineStayLocationDTO todayStay = createStayAt(parseInstant("2024-08-21T23:30:00Z"), "Today Stay", 120);
        todayTimeline.getStays().add(todayStay);

        MovementTimelineDTO result = timelineAssembler.combineTimelines(pastTimeline, todayTimeline, testUserId);

        // Gap should be detected (1.5 hours > 1 hour threshold and >= 30 min minimum)
        assertEquals(1, result.getDataGapsCount());
        
        TimelineDataGapDTO gap = result.getDataGaps().get(0);
        assertEquals(90, gap.getDurationMinutes()); // 1.5 hours in minutes
    }

    @Test
    @DisplayName("Cross-day gap configuration: Handle disabled configuration")
    void testCombineTimelines_HandleDisabledConfiguration() {
        // Configure null thresholds (disabled)
        mockConfig.setDataGapThresholdSeconds(null);
        mockConfig.setDataGapMinDurationSeconds(null);
        when(configurationProvider.getConfigurationForUser(testUserId)).thenReturn(mockConfig);

        // Past and today timelines with large gap
        MovementTimelineDTO pastTimeline = new MovementTimelineDTO(testUserId);
        TimelineStayLocationDTO pastStay = createStayAt(parseInstant("2024-08-21T10:00:00Z"), "Past Stay", 120);
        pastTimeline.getStays().add(pastStay);

        MovementTimelineDTO todayTimeline = new MovementTimelineDTO(testUserId);
        TimelineStayLocationDTO todayStay = createStayAt(parseInstant("2024-08-22T14:00:00Z"), "Today Stay", 120);
        todayTimeline.getStays().add(todayStay);

        MovementTimelineDTO result = timelineAssembler.combineTimelines(pastTimeline, todayTimeline, testUserId);

        // No gap should be detected when configuration is disabled
        assertEquals(0, result.getDataGapsCount());
        assertEquals(2, result.getStaysCount());
    }

    @Test
    @DisplayName("Timeline combining: Handle multiple event types and sort chronologically")
    void testCombineTimelines_MultipleEventTypesChronological() {
        // Configure gap detection
        when(configurationProvider.getConfigurationForUser(testUserId)).thenReturn(mockConfig);
        
        // Past timeline with mixed events
        MovementTimelineDTO pastTimeline = new MovementTimelineDTO(testUserId);
        pastTimeline.getStays().add(createStayAt(parseInstant("2024-08-21T10:00:00Z"), "Past Stay", 120));
        pastTimeline.getTrips().add(createTripAt(parseInstant("2024-08-21T12:00:00Z"), "Past Trip", 60));
        pastTimeline.getDataGaps().add(new TimelineDataGapDTO(parseInstant("2024-08-21T14:00:00Z"), parseInstant("2024-08-21T16:00:00Z")));

        // Today timeline with mixed events  
        MovementTimelineDTO todayTimeline = new MovementTimelineDTO(testUserId);
        todayTimeline.getStays().add(createStayAt(parseInstant("2024-08-22T09:00:00Z"), "Today Stay", 180));
        todayTimeline.getTrips().add(createTripAt(parseInstant("2024-08-22T12:00:00Z"), "Today Trip", 90));
        
        MovementTimelineDTO result = timelineAssembler.combineTimelines(pastTimeline, todayTimeline, testUserId);

        // Verify all events are included and sorted chronologically
        assertEquals(2, result.getStaysCount());
        assertEquals(2, result.getTripsCount());
        // May have original gap plus cross-day gap if detected
        assertTrue(result.getDataGapsCount() >= 1, "Should have at least original gap from past timeline");

        // Verify chronological order within each event type
        assertTrue(result.getStays().get(0).getTimestamp().isBefore(result.getStays().get(1).getTimestamp()));
        assertTrue(result.getTrips().get(0).getTimestamp().isBefore(result.getTrips().get(1).getTimestamp()));
    }

    // Helper methods

    private Instant parseInstant(String instantString) {
        return Instant.parse(instantString);
    }

    private TimelineStayLocationDTO createStayAt(Instant timestamp, String locationName, long durationMinutes) {
        TimelineStayLocationDTO stay = new TimelineStayLocationDTO();
        stay.setTimestamp(timestamp);
        stay.setLocationName(locationName);
        stay.setStayDuration(durationMinutes);
        return stay;
    }

    private TimelineTripDTO createTripAt(Instant timestamp, String movementType, long durationMinutes) {
        TimelineTripDTO trip = new TimelineTripDTO();
        trip.setTimestamp(timestamp);
        trip.setMovementType(movementType);
        trip.setTripDuration(durationMinutes);
        return trip;
    }
}
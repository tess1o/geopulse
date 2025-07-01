package org.github.tess1o.geopulse.statistics.service;

import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineTripDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TimelineAggregationService.
 * Tests basic mathematical calculations, aggregations, and division by zero handling.
 */
class TimelineAggregationServiceTest {

    private TimelineAggregationService timelineAggregationService;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        timelineAggregationService = new TimelineAggregationService();
        testUserId = UUID.randomUUID();
    }

    @Test
    void getTotalDistance_WithMultipleTrips_SumsCorrectly() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithMultipleTrips();

        // When
        double result = timelineAggregationService.getTotalDistance(timeline);

        // Then
        assertEquals(25.0, result, 0.01); // 10 + 8 + 7 = 25
    }

    @Test
    void getTotalDistance_WithEmptyTimeline_ReturnsZero() {
        // Given
        MovementTimelineDTO emptyTimeline = new MovementTimelineDTO(testUserId, List.of(), List.of());

        // When
        double result = timelineAggregationService.getTotalDistance(emptyTimeline);

        // Then
        assertEquals(0.0, result);
    }

    @Test
    void getTotalDistance_WithSingleTrip_ReturnsCorrectDistance() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithSingleTrip();

        // When
        double result = timelineAggregationService.getTotalDistance(timeline);

        // Then
        assertEquals(10.0, result);
    }

    @Test
    void getTimeMoving_WithMultipleTrips_SumsCorrectly() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithMultipleTrips();

        // When
        long result = timelineAggregationService.getTimeMoving(timeline);

        // Then
        assertEquals(150, result); // 60 + 45 + 45 = 150 minutes
    }

    @Test
    void getTimeMoving_WithEmptyTimeline_ReturnsZero() {
        // Given
        MovementTimelineDTO emptyTimeline = new MovementTimelineDTO(testUserId, List.of(), List.of());

        // When
        long result = timelineAggregationService.getTimeMoving(emptyTimeline);

        // Then
        assertEquals(0, result);
    }

    @Test
    void getTimeMoving_WithSingleTrip_ReturnsCorrectTime() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithSingleTrip();

        // When
        long result = timelineAggregationService.getTimeMoving(timeline);

        // Then
        assertEquals(60, result);
    }

    @Test
    void getDailyAverage_WithMultipleDays_CalculatesCorrectly() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithMultipleDays();

        // When
        double result = timelineAggregationService.getDailyAverage(timeline);

        // Then
        assertEquals(12.5, result, 0.01); // (10 + 15) / 2 days = 12.5
    }

    @Test
    void getDailyAverage_WithSingleDay_ReturnsFullDistance() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithSingleDay();

        // When
        double result = timelineAggregationService.getDailyAverage(timeline);

        // Then
        assertEquals(15.0, result, 0.01); // 10 + 5 = 15 on single day
    }

    @Test
    void getDailyAverage_WithEmptyTimeline_ReturnsZero() {
        // Given
        MovementTimelineDTO emptyTimeline = new MovementTimelineDTO(testUserId, List.of(), List.of());

        // When
        double result = timelineAggregationService.getDailyAverage(emptyTimeline);

        // Then
        assertEquals(0.0, result);
    }

    @Test
    void getAverageSpeed_WithValidData_CalculatesCorrectly() {
        // Given
        double totalDistance = 120.0; // km
        long timeMoving = 240; // minutes (4 hours)

        // When
        double result = timelineAggregationService.getAverageSpeed(totalDistance, timeMoving);

        // Then
        assertEquals(30.0, result, 0.01); // 120 km / 4 hours = 30 km/h
    }

    @Test
    void getAverageSpeed_WithZeroTime_ReturnsZero() {
        // Given
        double totalDistance = 100.0;
        long timeMoving = 0;

        // When
        double result = timelineAggregationService.getAverageSpeed(totalDistance, timeMoving);

        // Then
        assertEquals(0.0, result); // Should handle division by zero
    }

    @Test
    void getAverageSpeed_WithNegativeTime_ReturnsZero() {
        // Given
        double totalDistance = 100.0;
        long timeMoving = -60;

        // When
        double result = timelineAggregationService.getAverageSpeed(totalDistance, timeMoving);

        // Then
        assertEquals(0.0, result); // Should handle negative time gracefully
    }

    @Test
    void getAverageSpeed_WithZeroDistance_ReturnsZero() {
        // Given
        double totalDistance = 0.0;
        long timeMoving = 120;

        // When
        double result = timelineAggregationService.getAverageSpeed(totalDistance, timeMoving);

        // Then
        assertEquals(0.0, result); // 0 distance / any time = 0
    }

    @Test
    void getAverageSpeed_WithFractionalTime_CalculatesCorrectly() {
        // Given
        double totalDistance = 10.0; // km
        long timeMoving = 30; // minutes (0.5 hours)

        // When
        double result = timelineAggregationService.getAverageSpeed(totalDistance, timeMoving);

        // Then
        assertEquals(20.0, result, 0.01); // 10 km / 0.5 hours = 20 km/h
    }

    @Test
    void getDailyAverage_WithSameDayMultipleTrips_GroupsCorrectly() {
        // Given - multiple trips on same day
        MovementTimelineDTO timeline = createTimelineWithSameDayTrips();

        // When
        double result = timelineAggregationService.getDailyAverage(timeline);

        // Then
        assertEquals(25.0, result, 0.01); // All trips on same day: 10 + 15 = 25
    }

    @Test
    void getDailyAverage_WithTimeZoneSpanning_HandlesCorrectly() {
        // Given - trips spanning midnight UTC
        MovementTimelineDTO timeline = createTimelineWithTimeZoneSpanning();

        // When
        double result = timelineAggregationService.getDailyAverage(timeline);

        // Then
        assertTrue(result > 0); // Should handle timezone transitions properly
    }

    // Helper methods for creating test data

    private MovementTimelineDTO createTimelineWithMultipleTrips() {
        return new MovementTimelineDTO(testUserId,
                List.of(),
                List.of(
                        createTrip("2024-01-01T10:00:00Z", 10.0, 60),
                        createTrip("2024-01-01T12:00:00Z", 8.0, 45),
                        createTrip("2024-01-01T14:00:00Z", 7.0, 45)
                ));
    }

    private MovementTimelineDTO createTimelineWithSingleTrip() {
        return new MovementTimelineDTO(testUserId,
                List.of(),
                List.of(createTrip("2024-01-01T10:00:00Z", 10.0, 60)));
    }

    private MovementTimelineDTO createTimelineWithMultipleDays() {
        return new MovementTimelineDTO(testUserId,
                List.of(),
                List.of(
                        createTrip("2024-01-01T10:00:00Z", 10.0, 60), // Day 1
                        createTrip("2024-01-02T10:00:00Z", 15.0, 90)  // Day 2
                ));
    }

    private MovementTimelineDTO createTimelineWithSingleDay() {
        return new MovementTimelineDTO(testUserId,
                List.of(),
                List.of(
                        createTrip("2024-01-01T10:00:00Z", 10.0, 60),
                        createTrip("2024-01-01T12:00:00Z", 5.0, 30) // Same day
                ));
    }

    private MovementTimelineDTO createTimelineWithSameDayTrips() {
        return new MovementTimelineDTO(testUserId,
                List.of(),
                List.of(
                        createTrip("2024-01-01T09:00:00Z", 10.0, 60),
                        createTrip("2024-01-01T14:00:00Z", 15.0, 90) // Same day, different times
                ));
    }

    private MovementTimelineDTO createTimelineWithTimeZoneSpanning() {
        return new MovementTimelineDTO(testUserId,
                List.of(),
                List.of(
                        createTrip("2024-01-01T23:30:00Z", 10.0, 60), // Late night UTC
                        createTrip("2024-01-02T00:30:00Z", 5.0, 30)   // Early morning UTC next day
                ));
    }

    private TimelineTripDTO createTrip(String timestamp, double distanceKm, long durationMinutes) {
        return TimelineTripDTO.builder()
                .timestamp(Instant.parse(timestamp))
                .distanceKm(distanceKm)
                .tripDuration(durationMinutes)
                .build();
    }

    private TimelineStayLocationDTO createStay(String timestamp, String location, double lat, double lon, long durationMinutes) {
        return TimelineStayLocationDTO.builder()
                .timestamp(Instant.parse(timestamp))
                .locationName(location)
                .latitude(lat)
                .longitude(lon)
                .stayDuration(durationMinutes)
                .build();
    }
}
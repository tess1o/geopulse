package org.github.tess1o.geopulse.statistics.service;

import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TimelineAggregationService.
 * Tests basic mathematical calculations, aggregations, and division by zero handling.
 */
@ExtendWith(MockitoExtension.class)
class TimelineAggregationServiceTest {

    private TimelineAggregationService timelineAggregationService;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        timelineAggregationService = new TimelineAggregationService();
        testUserId = UUID.randomUUID();
    }

    @Test
    void getTotalDistance_Meters_WithMultipleTrips_SumsCorrectly() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithMultipleTrips();

        // When
        double result = timelineAggregationService.getTotalDistanceMeters(timeline);

        // Then
        assertEquals(25000, result, 0.01); // 10 + 8 + 7 = 25
    }

    @Test
    void getTotalDistance_Meters_WithEmptyTimeline_ReturnsZero() {
        // Given
        MovementTimelineDTO emptyTimeline = new MovementTimelineDTO(testUserId, List.of(), List.of());

        // When
        double result = timelineAggregationService.getTotalDistanceMeters(emptyTimeline);

        // Then
        assertEquals(0.0, result);
    }

    @Test
    void getTotalDistance_WithSingleTrip_ReturnsCorrectDistanceMeters() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithSingleTrip();

        // When
        double result = timelineAggregationService.getTotalDistanceMeters(timeline);

        // Then
        assertEquals(10000, result);
    }

    @Test
    void getTimeMoving_Seconds_WithMultipleTrips_SumsCorrectly() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithMultipleTrips();

        // When
        long result = timelineAggregationService.getTimeMovingSeconds(timeline);

        // Then
        assertEquals(150, result); // 60 + 45 + 45 = 150 minutes
    }

    @Test
    void getTimeMoving_Seconds_WithEmptyTimeline_ReturnsZero() {
        // Given
        MovementTimelineDTO emptyTimeline = new MovementTimelineDTO(testUserId, List.of(), List.of());

        // When
        long result = timelineAggregationService.getTimeMovingSeconds(emptyTimeline);

        // Then
        assertEquals(0, result);
    }

    @Test
    void getTimeMoving_WithSingleTrip_ReturnsCorrectTimeSeconds() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithSingleTrip();

        // When
        long result = timelineAggregationService.getTimeMovingSeconds(timeline);

        // Then
        assertEquals(60, result);
    }

    @Test
    void getDailyDistanceAverage_Meters_WithMultipleDays_CalculatesCorrectly() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithMultipleDays();

        // When
        double result = timelineAggregationService.getDailyDistanceAverageMeters(timeline);

        // Then
        assertEquals(12500, result, 0.01); // (10 + 15) / 2 days = 12.5
    }

    @Test
    void getDailyDistanceAverage_Meters_WithSingleDay_ReturnsFullDistance() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithSingleDay();

        // When
        double result = timelineAggregationService.getDailyDistanceAverageMeters(timeline);

        // Then
        assertEquals(15000, result, 0.01); // 10 + 5 = 15 on single day
    }

    @Test
    void getDailyDistanceAverage_Meters_WithEmptyTimeline_ReturnsZero() {
        // Given
        MovementTimelineDTO emptyTimeline = new MovementTimelineDTO(testUserId, List.of(), List.of());

        // When
        double result = timelineAggregationService.getDailyDistanceAverageMeters(emptyTimeline);

        // Then
        assertEquals(0.0, result);
    }

    @Test
    void getAverageSpeed_WithValidData_CalculatesCorrectly() {
        // Given
        double totalDistance = 120000; // m
        long timeMoving = 4 * 60 * 60; // seconds (4 hours)

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
        double totalDistance = 10000; // meters
        long timeMoving = 1800; // seconds (0.5 hours)

        // When
        double result = timelineAggregationService.getAverageSpeed(totalDistance, timeMoving);

        // Then
        assertEquals(20, result, 0.01); // 10 km / 0.5 hours = 20 km/h
    }

    @Test
    void getDailyDistanceAverage_Meters_WithSameDayMultipleTrips_GroupsCorrectly() {
        // Given - multiple trips on same day
        MovementTimelineDTO timeline = createTimelineWithSameDayTrips();

        // When
        double result = timelineAggregationService.getDailyDistanceAverageMeters(timeline);

        // Then
        assertEquals(25000, result, 0.01); // All trips on same day: 10 + 15 = 25
    }

    @Test
    void getDailyDistanceAverage_Meters_WithTimeZoneSpanning_HandlesCorrectly() {
        // Given - trips spanning midnight UTC
        MovementTimelineDTO timeline = createTimelineWithTimeZoneSpanning();

        // When
        double result = timelineAggregationService.getDailyDistanceAverageMeters(timeline);

        // Then
        assertTrue(result > 0); // Should handle timezone transitions properly
    }

    // Helper methods for creating test data

    private MovementTimelineDTO createTimelineWithMultipleTrips() {
        return new MovementTimelineDTO(testUserId,
                List.of(),
                List.of(
                        createTrip("2024-01-01T10:00:00Z", 10000, 60),
                        createTrip("2024-01-01T12:00:00Z", 8000, 45),
                        createTrip("2024-01-01T14:00:00Z", 7000, 45)
                ));
    }

    private MovementTimelineDTO createTimelineWithSingleTrip() {
        return new MovementTimelineDTO(testUserId,
                List.of(),
                List.of(createTrip("2024-01-01T10:00:00Z", 10000, 60)));
    }

    private MovementTimelineDTO createTimelineWithMultipleDays() {
        return new MovementTimelineDTO(testUserId,
                List.of(),
                List.of(
                        createTrip("2024-01-01T10:00:00Z", 10000, 60), // Day 1
                        createTrip("2024-01-02T10:00:00Z", 15000, 90)  // Day 2
                ));
    }

    private MovementTimelineDTO createTimelineWithSingleDay() {
        return new MovementTimelineDTO(testUserId,
                List.of(),
                List.of(
                        createTrip("2024-01-01T10:00:00Z", 10000, 60),
                        createTrip("2024-01-01T12:00:00Z", 5000, 30) // Same day
                ));
    }

    private MovementTimelineDTO createTimelineWithSameDayTrips() {
        return new MovementTimelineDTO(testUserId,
                List.of(),
                List.of(
                        createTrip("2024-01-01T09:00:00Z", 10000, 60),
                        createTrip("2024-01-01T14:00:00Z", 15000, 90) // Same day, different times
                ));
    }

    private MovementTimelineDTO createTimelineWithTimeZoneSpanning() {
        return new MovementTimelineDTO(testUserId,
                List.of(),
                List.of(
                        createTrip("2024-01-01T23:30:00Z", 10000, 60), // Late night UTC
                        createTrip("2024-01-02T00:30:00Z", 5000, 30)   // Early morning UTC next day
                ));
    }

    private TimelineTripDTO createTrip(String timestamp, long distance, long durationMinutes) {
        return TimelineTripDTO.builder()
                .timestamp(Instant.parse(timestamp))
                .distanceMeters(distance)
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
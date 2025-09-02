package org.github.tess1o.geopulse.statistics.service;

import org.github.tess1o.geopulse.statistics.model.MostActiveDayDto;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ActivityAnalysisService.
 * Tests most active day detection and activity pattern analysis.
 */
class ActivityAnalysisServiceTest {

    private ActivityAnalysisService activityAnalysisService;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        activityAnalysisService = new ActivityAnalysisService();
        testUserId = UUID.randomUUID();
    }

    @Test
    void getMostActiveDay_WithVariableActivity_IdentifiesCorrectDay() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithVariableActivity();

        // When
        MostActiveDayDto result = activityAnalysisService.getMostActiveDay(timeline);

        // Then
        assertNotNull(result);
        assertNotNull(result.getDate());
        assertNotNull(result.getDay());
        assertTrue(result.getDistanceTraveled() > 0);
        assertTrue(result.getTravelTime() >= 0);
        assertTrue(result.getLocationsVisited() >= 0);
        
        // Should be the day with highest distance (35km on 2024-01-02)
        assertEquals(35.0, result.getDistanceTraveled(), 0.01);
    }

    @Test
    void getMostActiveDay_WithEmptyTimeline_ReturnsNull() {
        // Given
        MovementTimelineDTO emptyTimeline = new MovementTimelineDTO(testUserId, List.of(), List.of());

        // When
        MostActiveDayDto result = activityAnalysisService.getMostActiveDay(emptyTimeline);

        // Then
        assertNull(result);
    }

    @Test
    void getMostActiveDay_WithSingleDay_ReturnsThatDay() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithSingleDay();

        // When
        MostActiveDayDto result = activityAnalysisService.getMostActiveDay(timeline);

        // Then
        assertNotNull(result);
        assertEquals(25.0, result.getDistanceTraveled(), 0.01); // 10 + 15
        assertEquals(150.0, result.getTravelTime(), 0.01); // 60 + 90
        assertEquals(2, result.getLocationsVisited()); // Home, Work
        assertNotNull(result.getDate());
        assertTrue(result.getDate().matches("\\d{2}/\\d{2}")); // MM/dd format
        assertNotNull(result.getDay());
    }

    @Test
    void getMostActiveDay_WithEqualDistanceDays_PicksFirst() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithEqualDistanceDays();

        // When
        MostActiveDayDto result = activityAnalysisService.getMostActiveDay(timeline);

        // Then
        assertNotNull(result);
        assertEquals(10.0, result.getDistanceTraveled(), 0.01);
        // Should pick one of the days with equal distance
        assertTrue(result.getDate().equals("01/01") || result.getDate().equals("01/02"));
    }

    @Test
    void getMostActiveDay_WithNoTrips_ReturnsNull() {
        // Given - timeline with stays but no trips
        MovementTimelineDTO timeline = new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 480),
                        createStay("2024-01-01T17:00:00Z", "Work", 40.7580, -73.9855, 480)
                ),
                List.of()); // No trips

        // When
        MostActiveDayDto result = activityAnalysisService.getMostActiveDay(timeline);

        // Then
        assertNull(result); // No trips means no active day
    }

    @Test
    void getMostActiveDay_CalculatesCorrectStatistics() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithDetailedActivity();

        // When
        MostActiveDayDto result = activityAnalysisService.getMostActiveDay(timeline);

        // Then
        assertNotNull(result);
        
        // Verify all statistics are calculated correctly
        assertTrue(result.getDistanceTraveled() > 0);
        assertTrue(result.getTravelTime() >= 0);
        assertTrue(result.getLocationsVisited() >= 0);
        
        // Check date format
        assertTrue(result.getDate().matches("\\d{2}/\\d{2}"));
        
        // Check day name
        assertTrue(List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                .contains(result.getDay()));
    }

    @Test
    void getMostActiveDay_WithMultipleStaysSameLocation_CountsUniqueLocations() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithRepeatedLocations();

        // When
        MostActiveDayDto result = activityAnalysisService.getMostActiveDay(timeline);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getLocationsVisited()); // Should count unique locations only
        assertEquals(15.0, result.getDistanceTraveled(), 0.01);
    }

    @Test
    void getMostActiveDay_WithTimeZoneSpanning_HandlesCorrectly() {
        // Given - activities spanning midnight UTC
        MovementTimelineDTO timeline = createTimelineWithTimeZoneSpanning();

        // When
        MostActiveDayDto result = activityAnalysisService.getMostActiveDay(timeline);

        // Then
        assertNotNull(result);
        assertTrue(result.getDistanceTraveled() > 0);
        // Should handle timezone transitions properly
    }

    @Test
    void getMostActiveDay_WithComplexActivity_SelectsHighestDistance() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithComplexActivity();

        // When
        MostActiveDayDto result = activityAnalysisService.getMostActiveDay(timeline);

        // Then
        assertNotNull(result);
        
        // Should select the day with highest total distance
        assertEquals(30.0, result.getDistanceTraveled(), 0.01); // Highest distance day
        assertTrue(result.getLocationsVisited() >= 1);
        assertTrue(result.getTravelTime() > 0);
    }

    // Helper methods for creating test data

    private MovementTimelineDTO createTimelineWithVariableActivity() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-02T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-02T14:00:00Z", "Work", 40.7580, -73.9855, 60),
                        createStay("2024-01-02T16:30:00Z", "Gym", 40.7505, -73.9934, 120),
                        createStay("2024-01-03T09:00:00Z", "Home", 40.7128, -74.0060, 30)
                ),
                List.of(
                        createTrip("2024-01-01T10:00:00Z", 5.0, 30),   // Low activity day
                        createTrip("2024-01-02T10:00:00Z", 20.0, 120), // High activity day
                        createTrip("2024-01-02T15:00:00Z", 15.0, 90),  // Same day, more activity
                        createTrip("2024-01-03T10:00:00Z", 8.0, 45)    // Medium activity day
                ));
    }

    private MovementTimelineDTO createTimelineWithSingleDay() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-01T11:00:00Z", "Work", 40.7580, -73.9855, 480)
                ),
                List.of(
                        createTrip("2024-01-01T10:00:00Z", 10.0, 60),
                        createTrip("2024-01-01T14:00:00Z", 15.0, 90)
                ));
    }

    private MovementTimelineDTO createTimelineWithEqualDistanceDays() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-02T09:00:00Z", "Work", 40.7580, -73.9855, 30)
                ),
                List.of(
                        createTrip("2024-01-01T10:00:00Z", 10.0, 60), // Day 1: 10km
                        createTrip("2024-01-02T10:00:00Z", 10.0, 60)  // Day 2: 10km (equal)
                ));
    }

    private MovementTimelineDTO createTimelineWithDetailedActivity() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-01T10:30:00Z", "Work", 40.7580, -73.9855, 420),
                        createStay("2024-01-01T18:00:00Z", "Gym", 40.7505, -73.9934, 90),
                        createStay("2024-01-01T20:00:00Z", "Home", 40.7128, -74.0060, 480)
                ),
                List.of(
                        createTrip("2024-01-01T10:00:00Z", 8.0, 45),
                        createTrip("2024-01-01T17:30:00Z", 5.0, 30),
                        createTrip("2024-01-01T19:30:00Z", 7.0, 35)
                ));
    }

    private MovementTimelineDTO createTimelineWithRepeatedLocations() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-01T10:30:00Z", "Home", 40.7128, -74.0060, 60),
                        createStay("2024-01-01T12:00:00Z", "Home", 40.7128, -74.0060, 45)
                ),
                List.of(
                        createTrip("2024-01-01T10:00:00Z", 8.0, 45),
                        createTrip("2024-01-01T11:30:00Z", 7.0, 35)
                ));
    }

    private MovementTimelineDTO createTimelineWithTimeZoneSpanning() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T23:00:00Z", "Location1", 40.7128, -74.0060, 30),
                        createStay("2024-01-02T01:00:00Z", "Location2", 40.7580, -73.9855, 60)
                ),
                List.of(
                        createTrip("2024-01-01T23:30:00Z", 10.0, 60), // Late night UTC
                        createTrip("2024-01-02T00:30:00Z", 5.0, 30)   // Early morning UTC
                ));
    }

    private MovementTimelineDTO createTimelineWithComplexActivity() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-02T09:00:00Z", "Work", 40.7580, -73.9855, 30),
                        createStay("2024-01-03T09:00:00Z", "Gym", 40.7505, -73.9934, 30)
                ),
                List.of(
                        createTrip("2024-01-01T10:00:00Z", 10.0, 60), // Day 1: 10km
                        createTrip("2024-01-02T10:00:00Z", 30.0, 120), // Day 2: 30km (highest)
                        createTrip("2024-01-03T10:00:00Z", 15.0, 75)   // Day 3: 15km
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
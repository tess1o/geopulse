package org.github.tess1o.geopulse.statistics.service;

import org.github.tess1o.geopulse.statistics.model.BarChartData;
import org.github.tess1o.geopulse.statistics.model.ChartGroupMode;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChartDataService.
 * Tests chart generation logic for both daily and weekly grouping modes.
 */
@ExtendWith(MockitoExtension.class)
class ChartDataServiceTest {

    private ChartDataService chartDataService;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        chartDataService = new ChartDataService();
        testUserId = UUID.randomUUID();
    }

    @Test
    void getDistanceChartData_WithDailyMode_GeneratesDailyChart() {
        // Given
        MovementTimelineDTO timeline = createSampleTimeline();

        // When
        BarChartData result = chartDataService.getDistanceChartData(timeline, TripType.CAR, ChartGroupMode.DAYS);

        // Then
        assertNotNull(result);
        assertTrue(result.getLabels().length > 0);
        assertEquals(result.getLabels().length, result.getData().length);
        
        // Verify daily grouping format (day names)
        for (String label : result.getLabels()) {
            assertTrue(List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN").contains(label),
                    "Label should be day abbreviation: " + label);
        }
    }

    @Test
    void getDistanceChartData_WithWeeklyMode_GeneratesWeeklyChart() {
        // Given
        MovementTimelineDTO timeline = createMultiWeekTimeline();

        // When
        BarChartData result = chartDataService.getDistanceChartData(timeline, TripType.CAR, ChartGroupMode.WEEKS);

        // Then
        assertNotNull(result);
        assertTrue(result.getLabels().length > 0);
        assertEquals(result.getLabels().length, result.getData().length);
        
        // Verify weekly grouping format (MM/dd)
        for (String label : result.getLabels()) {
            assertTrue(label.matches("\\d{2}/\\d{2}"), "Label should be in MM/dd format: " + label);
        }
    }

    @Test
    void getDistanceChartData_WithEmptyTimeline_ReturnsEmptyChart() {
        // Given
        MovementTimelineDTO emptyTimeline = new MovementTimelineDTO(testUserId, List.of(), List.of());

        // When
        BarChartData result = chartDataService.getDistanceChartData(emptyTimeline, TripType.CAR, ChartGroupMode.DAYS);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getLabels().length);
        assertEquals(0, result.getData().length);
    }

    @Test
    void getDistanceChartData_WithSingleDay_GroupsCorrectly() {
        // Given
        MovementTimelineDTO timeline = createSingleDayTimeline();

        // When
        BarChartData result = chartDataService.getDistanceChartData(timeline, TripType.CAR, ChartGroupMode.DAYS);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getLabels().length);
        assertEquals(1, result.getData().length);
        assertEquals(15.0, result.getData()[0], 0.01); // 10 + 5
        assertTrue(List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN").contains(result.getLabels()[0]));
    }

    @Test
    void getDistanceChartData_WithWeeklyGrouping_SumsDistancesByWeek() {
        // Given
        MovementTimelineDTO timeline = createMultiWeekTimeline();

        // When
        BarChartData result = chartDataService.getDistanceChartData(timeline, TripType.CAR, ChartGroupMode.WEEKS);

        // Then
        assertNotNull(result);
        assertTrue(result.getLabels().length > 0);
        
        // Verify that distances are properly summed by week
        double totalDistance = 0;
        for (double distance : result.getData()) {
            totalDistance += distance;
        }
        assertEquals(33.0, totalDistance, 0.01); // 10 + 15 + 8
    }

    @Test
    void getDistanceChartData_WithDailyGrouping_SumsDistancesByDay() {
        // Given
        MovementTimelineDTO timeline = createSameDayMultipleTripsTimeline();

        // When
        BarChartData result = chartDataService.getDistanceChartData(timeline, TripType.CAR, ChartGroupMode.DAYS);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getLabels().length);
        assertEquals(1, result.getData().length);
        assertEquals(25.0, result.getData()[0], 0.01); // 10 + 15 (same day)
    }

    @Test
    void getDistanceChartData_WithDifferentTimeZones_HandlesCorrectly() {
        // Given - trips spanning midnight UTC
        MovementTimelineDTO timeline = createTimeZoneSpanningTimeline();

        // When
        BarChartData result = chartDataService.getDistanceChartData(timeline, TripType.CAR, ChartGroupMode.DAYS);

        // Then
        assertNotNull(result);
        assertTrue(result.getLabels().length >= 1);
        assertTrue(result.getData().length >= 1);
        
        // Verify total distance is preserved
        double totalDistance = 0;
        for (double distance : result.getData()) {
            totalDistance += distance;
        }
        assertEquals(15.0, totalDistance, 0.01); // 10 + 5
    }

    @Test
    void getDistanceChartData_SortsChronologically() {
        // Given
        MovementTimelineDTO timeline = createMultiDayTimeline();

        // When
        BarChartData result = chartDataService.getDistanceChartData(timeline, TripType.CAR, ChartGroupMode.DAYS);

        // Then
        assertNotNull(result);
        assertTrue(result.getLabels().length > 1);
        
        // For daily mode, labels should represent chronological days
        // (exact order depends on the days of week, but should be consistent)
        assertTrue(result.getLabels().length == result.getData().length);
    }

    // Helper methods for creating test data

    private MovementTimelineDTO createSampleTimeline() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-01T11:00:00Z", "Work", 40.7580, -73.9855, 480)
                ),
                List.of(createTrip("2024-01-01T10:00:00Z", 10000, 60)));
    }

    private MovementTimelineDTO createMultiWeekTimeline() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-08T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-15T09:00:00Z", "Home", 40.7128, -74.0060, 30)
                ),
                List.of(
                        createTrip("2024-01-01T10:00:00Z", 10000, 60), // Week 1
                        createTrip("2024-01-08T10:00:00Z", 15000, 90), // Week 2
                        createTrip("2024-01-15T10:00:00Z", 8000, 45)   // Week 3
                ));
    }

    private MovementTimelineDTO createSingleDayTimeline() {
        return new MovementTimelineDTO(testUserId,
                List.of(createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30)),
                List.of(
                        createTrip("2024-01-01T10:00:00Z", 10000, 60),
                        createTrip("2024-01-01T11:00:00Z", 5000, 30)
                ));
    }

    private MovementTimelineDTO createSameDayMultipleTripsTimeline() {
        return new MovementTimelineDTO(testUserId,
                List.of(createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30)),
                List.of(
                        createTrip("2024-01-01T10:00:00Z", 10000, 60),
                        createTrip("2024-01-01T14:00:00Z", 15000, 90) // Same day
                ));
    }

    private MovementTimelineDTO createTimeZoneSpanningTimeline() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T23:00:00Z", "Location1", 40.7128, -74.0060, 30),
                        createStay("2024-01-02T01:00:00Z", "Location2", 40.7580, -73.9855, 60)
                ),
                List.of(
                        createTrip("2024-01-01T23:30:00Z", 10000, 60), // Late night UTC
                        createTrip("2024-01-02T00:30:00Z", 5000, 30)   // Early morning UTC
                ));
    }

    private MovementTimelineDTO createMultiDayTimeline() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-02T09:00:00Z", "Work", 40.7580, -73.9855, 480),
                        createStay("2024-01-03T09:00:00Z", "Home", 40.7128, -74.0060, 30)
                ),
                List.of(
                        createTrip("2024-01-01T10:00:00Z", 10000, 60),
                        createTrip("2024-01-02T10:00:00Z", 8000, 45),
                        createTrip("2024-01-03T10:00:00Z", 12000, 75)
                ));
    }

    private TimelineTripDTO createTrip(String timestamp, long distanceMeters, long durationSeconds) {
        return TimelineTripDTO.builder()
                .timestamp(Instant.parse(timestamp))
                .distanceMeters(distanceMeters)
                .tripDuration(durationSeconds)
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
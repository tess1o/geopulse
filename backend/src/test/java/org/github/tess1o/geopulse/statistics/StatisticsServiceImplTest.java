package org.github.tess1o.geopulse.statistics;

import org.github.tess1o.geopulse.statistics.model.*;
import org.github.tess1o.geopulse.statistics.service.*;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineTripDTO;
import org.github.tess1o.geopulse.timeline.service.TimelineQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for StatisticsServiceImpl.
 * Tests cover normal operations, edge cases, and error conditions.
 */
@ExtendWith(MockitoExtension.class)
class StatisticsServiceImplTest {

    @Mock
    private TimelineQueryService timelineQueryService;

    private ChartDataService chartDataService;
    private PlacesAnalysisService placesAnalysisService;
    private RoutesAnalysisService routesAnalysisService;
    private TimelineAggregationService timelineAggregationService;
    private ActivityAnalysisService activityAnalysisService;
    private StatisticsServiceImpl statisticsService;

    private UUID testUserId;
    private Instant testStart;
    private Instant testEnd;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testStart = Instant.parse("2024-01-01T00:00:00Z");
        testEnd = Instant.parse("2024-01-07T23:59:59Z");
        
        // Create real instances of the service classes (no external dependencies)
        chartDataService = new ChartDataService();
        placesAnalysisService = new PlacesAnalysisService();
        routesAnalysisService = new RoutesAnalysisService();
        timelineAggregationService = new TimelineAggregationService();
        activityAnalysisService = new ActivityAnalysisService();
        
        // Create the main service with real dependencies
        statisticsService = new StatisticsServiceImpl(
                timelineQueryService,
                chartDataService,
                placesAnalysisService,
                routesAnalysisService,
                timelineAggregationService,
                activityAnalysisService
        );
    }

    @Test
    void getStatistics_WithValidData_ReturnsCompleteStatistics() {
        // Given
        MovementTimelineDTO timeline = createSampleTimeline();
        when(timelineQueryService.getTimeline(eq(testUserId), eq(testStart), eq(testEnd)))
                .thenReturn(timeline);

        // When
        UserStatistics result = statisticsService.getStatistics(testUserId, testStart, testEnd, ChartGroupMode.DAYS);

        // Then
        assertNotNull(result);
        assertEquals(15.0, result.getTotalDistance(), 0.01); // 10 + 5
        assertEquals(120, result.getTimeMoving()); // 60 + 60
        assertTrue(result.getDailyAverage() > 0);
        assertEquals(2, result.getUniqueLocationsCount()); // Home, Work
        assertEquals(7.5, result.getAverageSpeed(), 0.01); // 15km / 2h
        assertNotNull(result.getMostActiveDay());
        assertNotNull(result.getPlaces());
        assertNotNull(result.getRoutes());
        assertNotNull(result.getDistanceCarChart());
    }

    @Test
    void getStatistics_WithEmptyTimeline_ReturnsZeroStatistics() {
        // Given
        MovementTimelineDTO emptyTimeline = new MovementTimelineDTO(testUserId, List.of(), List.of());
        when(timelineQueryService.getTimeline(any(), any(), any()))
                .thenReturn(emptyTimeline);

        // When
        UserStatistics result = statisticsService.getStatistics(testUserId, testStart, testEnd, ChartGroupMode.DAYS);

        // Then
        assertNotNull(result);
        assertEquals(0.0, result.getTotalDistance());
        assertEquals(0, result.getTimeMoving());
        assertEquals(0.0, result.getDailyAverage());
        assertEquals(0, result.getUniqueLocationsCount());
        assertEquals(0.0, result.getAverageSpeed()); // Should handle division by zero
        assertNull(result.getMostActiveDay()); // No active day when no data
        assertTrue(result.getPlaces().isEmpty());
        assertNotNull(result.getRoutes());
        assertNotNull(result.getDistanceCarChart());
    }

    @Test
    void getStatistics_WithSingleTrip_HandlesCorrectly() {
        // Given
        MovementTimelineDTO timeline = new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-01T11:00:00Z", "Work", 40.7580, -73.9855, 480)
                ),
                List.of(createTrip("2024-01-01T10:00:00Z", 10.0, 60)));
        when(timelineQueryService.getTimeline(any(), any(), any()))
                .thenReturn(timeline);

        // When
        UserStatistics result = statisticsService.getStatistics(testUserId, testStart, testEnd, ChartGroupMode.DAYS);

        // Then
        assertEquals(10.0, result.getTotalDistance());
        assertEquals(60, result.getTimeMoving());
        assertEquals(10.0, result.getDailyAverage()); // Only one day
        assertEquals(2, result.getUniqueLocationsCount());
        assertEquals(10.0, result.getAverageSpeed()); // 10km / 1h
    }

    @Test
    void getStatistics_WithWeeklyGrouping_GeneratesCorrectChartData() {
        // Given
        MovementTimelineDTO timeline = createMultiWeekTimeline();
        when(timelineQueryService.getTimeline(any(), any(), any()))
                .thenReturn(timeline);

        // When
        UserStatistics result = statisticsService.getStatistics(testUserId, testStart, testEnd, ChartGroupMode.WEEKS);

        // Then
        BarChartData chartData = result.getDistanceCarChart();
        assertNotNull(chartData);
        assertTrue(chartData.getLabels().length > 0);
        assertEquals(chartData.getLabels().length, chartData.getData().length);
        
        // Verify weekly grouping format (MM/dd)
        for (String label : chartData.getLabels()) {
            assertTrue(label.matches("\\d{2}/\\d{2}"), "Label should be in MM/dd format: " + label);
        }
    }

    @Test
    void getStatistics_WithDailyGrouping_GeneratesCorrectChartData() {
        // Given
        MovementTimelineDTO timeline = createSampleTimeline();
        when(timelineQueryService.getTimeline(any(), any(), any()))
                .thenReturn(timeline);

        // When
        UserStatistics result = statisticsService.getStatistics(testUserId, testStart, testEnd, ChartGroupMode.DAYS);

        // Then
        BarChartData chartData = result.getDistanceCarChart();
        assertNotNull(chartData);
        assertTrue(chartData.getLabels().length > 0);
        
        // Verify daily grouping format (day names)
        for (String label : chartData.getLabels()) {
            assertTrue(List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN").contains(label),
                    "Label should be day abbreviation: " + label);
        }
    }

    @Test
    void getStatistics_CalculatesTopPlacesCorrectly() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithMultiplePlaces();
        when(timelineQueryService.getTimeline(any(), any(), any()))
                .thenReturn(timeline);

        // When
        UserStatistics result = statisticsService.getStatistics(testUserId, testStart, testEnd, ChartGroupMode.DAYS);

        // Then
        List<TopPlace> places = result.getPlaces();
        assertNotNull(places);
        assertTrue(places.size() <= 5); // Should limit to top 5
        
        // Verify places are sorted by visit count (descending)
        for (int i = 0; i < places.size() - 1; i++) {
            assertTrue(places.get(i).getVisits() >= places.get(i + 1).getVisits(),
                    "Places should be sorted by visits descending");
        }
        
        // Verify each place has coordinates
        for (TopPlace place : places) {
            assertNotNull(place.getCoordinates());
            assertEquals(2, place.getCoordinates().length);
        }
    }

    @Test
    void getStatistics_CalculatesRoutesStatisticsCorrectly() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithRoutes();
        when(timelineQueryService.getTimeline(any(), any(), any()))
                .thenReturn(timeline);

        // When
        UserStatistics result = statisticsService.getStatistics(testUserId, testStart, testEnd, ChartGroupMode.DAYS);

        // Then
        RoutesStatistics routes = result.getRoutes();
        assertNotNull(routes);
        assertTrue(routes.getAvgTripDuration() >= 0);
        assertTrue(routes.getUniqueRoutesCount() >= 0);
        assertTrue(routes.getLongestTripDuration() >= 0);
        assertTrue(routes.getLongestTripDistance() >= 0);
        assertNotNull(routes.getMostCommonRoute());
    }

    @Test
    void getStatistics_HandlesMostActiveDayCalculation() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithVariableActivity();
        when(timelineQueryService.getTimeline(any(), any(), any()))
                .thenReturn(timeline);

        // When
        UserStatistics result = statisticsService.getStatistics(testUserId, testStart, testEnd, ChartGroupMode.DAYS);

        // Then
        MostActiveDayDto mostActiveDay = result.getMostActiveDay();
        assertNotNull(mostActiveDay);
        assertNotNull(mostActiveDay.getDate());
        assertNotNull(mostActiveDay.getDay());
        assertTrue(mostActiveDay.getDistanceTraveled() >= 0);
        assertTrue(mostActiveDay.getTravelTime() >= 0);
        assertTrue(mostActiveDay.getLocationsVisited() >= 0);
    }

    @Test
    void getStatistics_HandlesZeroMovingTime_PreventsDivisionByZero() {
        // Given - timeline with stays but no trips
        MovementTimelineDTO timeline = new MovementTimelineDTO(testUserId,
                List.of(createStay("2024-01-01T10:00:00Z", "Home", 40.7128, -74.0060, 480)),
                List.of()); // No trips = no moving time
        when(timelineQueryService.getTimeline(any(), any(), any()))
                .thenReturn(timeline);

        // When
        UserStatistics result = statisticsService.getStatistics(testUserId, testStart, testEnd, ChartGroupMode.DAYS);

        // Then
        assertEquals(0.0, result.getAverageSpeed()); // Should handle division by zero gracefully
        assertDoesNotThrow(() -> result.getAverageSpeed()); // Should not throw exception
    }

    @Test
    void getStatistics_HandlesIdenticalLocations() {
        // Given - multiple stays at same location
        MovementTimelineDTO timeline = new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-01T10:30:00Z", "Home", 40.7128, -74.0060, 60),
                        createStay("2024-01-01T11:30:00Z", "Home", 40.7128, -74.0060, 45)
                ),
                List.of(createTrip("2024-01-01T10:00:00Z", 5.0, 30)));
        when(timelineQueryService.getTimeline(any(), any(), any()))
                .thenReturn(timeline);

        // When
        UserStatistics result = statisticsService.getStatistics(testUserId, testStart, testEnd, ChartGroupMode.DAYS);

        // Then
        assertEquals(1, result.getUniqueLocationsCount()); // Should count unique locations correctly
        
        List<TopPlace> places = result.getPlaces();
        assertEquals(1, places.size());
        assertEquals("Home", places.get(0).getName());
        assertEquals(3, places.get(0).getVisits()); // Should aggregate visits
        assertEquals(135, places.get(0).getDuration()); // Should sum durations (30+60+45)
    }

    @Test
    void getStatistics_HandlesTimeZoneConsistently() {
        // Given - trips spanning multiple days in different timezones
        MovementTimelineDTO timeline = new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T23:00:00Z", "Location1", 40.7128, -74.0060, 30),
                        createStay("2024-01-02T00:30:00Z", "Location2", 40.7580, -73.9855, 60),
                        createStay("2024-01-02T02:00:00Z", "Location3", 40.7830, -73.9712, 30)
                ),
                List.of(
                        createTrip("2024-01-01T23:30:00Z", 10.0, 60), // Late night UTC
                        createTrip("2024-01-02T01:30:00Z", 5.0, 30)   // Early morning UTC
                ));
        when(timelineQueryService.getTimeline(any(), any(), any()))
                .thenReturn(timeline);

        // When
        UserStatistics result = statisticsService.getStatistics(testUserId, testStart, testEnd, ChartGroupMode.DAYS);

        // Then
        assertNotNull(result);
        // Should handle timezone grouping consistently
        BarChartData chartData = result.getDistanceCarChart();
        assertNotNull(chartData);
        assertTrue(chartData.getLabels().length >= 1);
    }

    // Helper methods to create test data

    private MovementTimelineDTO createSampleTimeline() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-01T11:00:00Z", "Work", 40.7580, -73.9855, 480),
                        createStay("2024-01-02T13:00:00Z", "Home", 40.7128, -74.0060, 60),
                        createStay("2024-01-02T15:00:00Z", "Work", 40.7580, -73.9855, 420)
                ),
                List.of(
                        createTrip("2024-01-01T10:00:00Z", 10.0, 60),
                        createTrip("2024-01-02T14:00:00Z", 5.0, 60)
                ));
    }

    private MovementTimelineDTO createMultiWeekTimeline() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-08T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-15T09:00:00Z", "Home", 40.7128, -74.0060, 30)
                ),
                List.of(
                        createTrip("2024-01-01T10:00:00Z", 10.0, 60), // Week 1
                        createTrip("2024-01-08T10:00:00Z", 15.0, 90), // Week 2
                        createTrip("2024-01-15T10:00:00Z", 8.0, 45)   // Week 3
                ));
    }

    private MovementTimelineDTO createTimelineWithMultiplePlaces() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 480),
                        createStay("2024-01-01T10:30:00Z", "Work", 40.7580, -73.9855, 420),
                        createStay("2024-01-01T18:00:00Z", "Home", 40.7128, -74.0060, 600), // 2nd visit to Home
                        createStay("2024-01-02T09:00:00Z", "Gym", 40.7505, -73.9934, 90),
                        createStay("2024-01-02T10:30:00Z", "Work", 40.7580, -73.9855, 420), // 2nd visit to Work
                        createStay("2024-01-02T18:00:00Z", "Store", 40.7614, -73.9776, 30)
                ),
                List.of(createTrip("2024-01-01T10:00:00Z", 5.0, 30)));
    }

    private MovementTimelineDTO createTimelineWithRoutes() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-01T11:00:00Z", "Work", 40.7580, -73.9855, 420),
                        createStay("2024-01-01T19:00:00Z", "Home", 40.7128, -74.0060, 780),
                        createStay("2024-01-02T11:30:00Z", "Store", 40.7614, -73.9776, 60)
                ),
                List.of(
                        createTrip("2024-01-01T10:00:00Z", 10.0, 60),
                        createTrip("2024-01-01T18:00:00Z", 10.0, 50),
                        createTrip("2024-01-02T10:00:00Z", 15.0, 90)
                ));
    }

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
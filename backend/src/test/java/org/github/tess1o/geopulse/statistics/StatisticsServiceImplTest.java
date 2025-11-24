package org.github.tess1o.geopulse.statistics;

import org.github.tess1o.geopulse.statistics.model.*;
import org.github.tess1o.geopulse.statistics.repository.StatisticsRepository;
import org.github.tess1o.geopulse.statistics.service.StatisticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

    /**
 * Unit tests for StatisticsServiceImpl with SQL-based calculations.
 * Tests cover normal operations, edge cases, and error conditions.
 */
@ExtendWith(MockitoExtension.class)
class StatisticsServiceImplTest {

    @Mock
    private StatisticsRepository statisticsRepository;

    private StatisticsServiceImpl statisticsService;

    private UUID testUserId;
    private Instant testStart;
    private Instant testEnd;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testStart = Instant.parse("2024-01-01T00:00:00Z");
        testEnd = Instant.parse("2024-01-07T23:59:59Z");

        statisticsService = new StatisticsServiceImpl(statisticsRepository);
    }

    @Test
    void getStatistics_WithValidData_ReturnsCompleteStatistics() {
        // Given
        TripAggregationResult tripAgg = new TripAggregationResult(15000.0, 7200L, 7500.0, 2L);
        when(statisticsRepository.getTripAggregations(testUserId, testStart, testEnd)).thenReturn(tripAgg);
        when(statisticsRepository.getUniqueLocationsCount(testUserId, testStart, testEnd)).thenReturn(2L);
        when(statisticsRepository.getTopPlaces(testUserId, testStart, testEnd, 5))
                .thenReturn(List.of(createTopPlace("Home", 3, 1800, 40.7128, -74.0060)));
        when(statisticsRepository.getMostActiveDay(testUserId, testStart, testEnd))
                .thenReturn(createMostActiveDay("01/02", "Monday", 20.0, 3600.0, 3L));
        when(statisticsRepository.getRoutesStatistics(testUserId, testStart, testEnd))
                .thenReturn(createRoutesStats());
        when(statisticsRepository.getChartDataByDays(any(UUID.class), any(), any(), any()))
                .thenReturn(List.of(new ChartDataPoint("MON", 10.0)));

        // When
        UserStatistics result = statisticsService.getStatistics(testUserId, testStart, testEnd, ChartGroupMode.DAYS);

        // Then
        assertNotNull(result);
        assertEquals(15000.0, result.getTotalDistanceMeters());
        assertEquals(7200L, result.getTimeMoving());
        assertEquals(7500.0, result.getDailyAverageDistanceMeters());
        assertEquals(2L, result.getUniqueLocationsCount());
        assertEquals(7.5, result.getAverageSpeed(), 0.01); // 15km / 2h
        assertNotNull(result.getMostActiveDay());
        assertNotNull(result.getPlaces());
        assertFalse(result.getPlaces().isEmpty());
        assertNotNull(result.getRoutes());
        assertNotNull(result.getDistanceChartsByTripType());
        assertFalse(result.getDistanceChartsByTripType().isEmpty());

        // Verify repository was called
        verify(statisticsRepository).getTripAggregations(testUserId, testStart, testEnd);
        verify(statisticsRepository).getUniqueLocationsCount(testUserId, testStart, testEnd);
        verify(statisticsRepository).getTopPlaces(testUserId, testStart, testEnd, 5);
        verify(statisticsRepository).getMostActiveDay(testUserId, testStart, testEnd);
        verify(statisticsRepository).getRoutesStatistics(testUserId, testStart, testEnd);
        verify(statisticsRepository, times(6)).getChartDataByDays(any(), any(), any(), any());
    }

    @Test
    void getStatistics_WithEmptyData_ReturnsZeroStatistics() {
        // Given - empty results from database
        TripAggregationResult emptyTrips = new TripAggregationResult(0.0, 0L, 0.0, 0L);
        when(statisticsRepository.getTripAggregations(testUserId, testStart, testEnd)).thenReturn(emptyTrips);
        when(statisticsRepository.getUniqueLocationsCount(testUserId, testStart, testEnd)).thenReturn(0L);
        when(statisticsRepository.getTopPlaces(testUserId, testStart, testEnd, 5)).thenReturn(List.of());
        when(statisticsRepository.getMostActiveDay(testUserId, testStart, testEnd)).thenReturn(null);
        when(statisticsRepository.getRoutesStatistics(testUserId, testStart, testEnd))
                .thenReturn(RoutesStatistics.builder()
                        .avgTripDurationSeconds(0.0)
                        .uniqueRoutesCount(0)
                        .longestTripDurationSeconds(0.0)
                        .longestTripDistanceMeters(0.0)
                        .mostCommonRoute(new MostCommonRoute("", 0))
                        .build());
        when(statisticsRepository.getChartDataByDays(any(UUID.class), any(), any(), any())).thenReturn(List.of());

        // When
        UserStatistics result = statisticsService.getStatistics(testUserId, testStart, testEnd, ChartGroupMode.DAYS);

        // Then
        assertNotNull(result);
        assertEquals(0.0, result.getTotalDistanceMeters());
        assertEquals(0L, result.getTimeMoving());
        assertEquals(0.0, result.getDailyAverageDistanceMeters());
        assertEquals(0L, result.getUniqueLocationsCount());
        assertEquals(0.0, result.getAverageSpeed()); // Should handle division by zero
        assertNull(result.getMostActiveDay());
        assertTrue(result.getPlaces().isEmpty());
        assertNotNull(result.getRoutes());
        assertEquals(0, result.getRoutes().getUniqueRoutesCount());
        assertNotNull(result.getDistanceChartsByTripType());
        // Charts should have data for all trip types, but with empty arrays
        result.getDistanceChartsByTripType().values().forEach(chart -> {
            assertEquals(0, chart.getLabels().length);
        });
    }

    @Test
    void getStatistics_WithZeroMovingTime_PreventsDivisionByZero() {
        // Given - distance but no time (edge case)
        TripAggregationResult tripAgg = new TripAggregationResult(10000.0, 0L, 10000.0, 1L);
        when(statisticsRepository.getTripAggregations(testUserId, testStart, testEnd)).thenReturn(tripAgg);
        when(statisticsRepository.getUniqueLocationsCount(testUserId, testStart, testEnd)).thenReturn(1L);
        when(statisticsRepository.getTopPlaces(testUserId, testStart, testEnd, 5))
                .thenReturn(List.of(createTopPlace("Home", 1, 600, 40.7128, -74.0060)));
        when(statisticsRepository.getMostActiveDay(testUserId, testStart, testEnd)).thenReturn(null);
        when(statisticsRepository.getRoutesStatistics(testUserId, testStart, testEnd))
                .thenReturn(createRoutesStats());
        when(statisticsRepository.getChartDataByDays(any(UUID.class), any(), any(), any())).thenReturn(List.of());

        // When
        UserStatistics result = statisticsService.getStatistics(testUserId, testStart, testEnd, ChartGroupMode.DAYS);

        // Then
        assertEquals(0.0, result.getAverageSpeed()); // Should not throw exception
        assertDoesNotThrow(() -> result.getAverageSpeed());
    }

    @Test
    void getStatistics_UsesWeeklyGrouping_ForLongDateRanges() {
        // Given - date range > 10 days
        Instant longRangeEnd = testStart.plusSeconds(15 * 24 * 3600); // 15 days
        TripAggregationResult tripAgg = new TripAggregationResult(15000.0, 7200L, 1000.0, 15L);
        when(statisticsRepository.getTripAggregations(testUserId, testStart, longRangeEnd)).thenReturn(tripAgg);
        when(statisticsRepository.getUniqueLocationsCount(testUserId, testStart, longRangeEnd)).thenReturn(3L);
        when(statisticsRepository.getTopPlaces(testUserId, testStart, longRangeEnd, 5))
                .thenReturn(List.of(createTopPlace("Home", 10, 5000, 40.7128, -74.0060)));
        when(statisticsRepository.getMostActiveDay(testUserId, testStart, longRangeEnd))
                .thenReturn(createMostActiveDay("01/05", "Friday", 25.0, 4500.0, 4L));
        when(statisticsRepository.getRoutesStatistics(testUserId, testStart, longRangeEnd))
                .thenReturn(createRoutesStats());
        when(statisticsRepository.getChartDataByWeeks(any(UUID.class), any(), any(), any()))
                .thenReturn(List.of(
                        new ChartDataPoint("01/01", 10.0),
                        new ChartDataPoint("01/08", 5.0)
                ));

        // When
        UserStatistics result = statisticsService.getStatistics(testUserId, testStart, longRangeEnd, ChartGroupMode.DAYS);

        // Then
        verify(statisticsRepository, atLeast(5)).getChartDataByWeeks(any(), any(), any(), any());
        assertNotNull(result.getDistanceChartsByTripType());
        // Should have charts for multiple trip types
        assertTrue(result.getDistanceChartsByTripType().size() > 0);
    }

    @Test
    void getStatistics_UsesDailyGrouping_ForShortDateRanges() {
        // Given - date range < 10 days
        TripAggregationResult tripAgg = new TripAggregationResult(15000.0, 7200L, 2500.0, 6L);
        when(statisticsRepository.getTripAggregations(testUserId, testStart, testEnd)).thenReturn(tripAgg);
        when(statisticsRepository.getUniqueLocationsCount(testUserId, testStart, testEnd)).thenReturn(2L);
        when(statisticsRepository.getTopPlaces(testUserId, testStart, testEnd, 5))
                .thenReturn(List.of(createTopPlace("Work", 5, 2500, 40.7580, -73.9855)));
        when(statisticsRepository.getMostActiveDay(testUserId, testStart, testEnd))
                .thenReturn(createMostActiveDay("01/03", "Wednesday", 30.0, 5400.0, 5L));
        when(statisticsRepository.getRoutesStatistics(testUserId, testStart, testEnd))
                .thenReturn(createRoutesStats());
        when(statisticsRepository.getChartDataByDays(any(UUID.class), any(), any(), any()))
                .thenReturn(List.of(
                        new ChartDataPoint("MON", 5.0),
                        new ChartDataPoint("TUE", 10.0)
                ));

        // When
        UserStatistics result = statisticsService.getStatistics(testUserId, testStart, testEnd, ChartGroupMode.DAYS);

        // Then
        verify(statisticsRepository, atLeast(5)).getChartDataByDays(any(), any(), any(), any());
        assertNotNull(result.getDistanceChartsByTripType());
        // Should have charts for multiple trip types
        assertTrue(result.getDistanceChartsByTripType().size() > 0);
    }

    @Test
    void getStatistics_RespectsChartGroupModeOverride() {
        // Given - short range but WEEKS mode explicitly requested
        TripAggregationResult tripAgg = new TripAggregationResult(15000.0, 7200L, 2500.0, 6L);
        when(statisticsRepository.getTripAggregations(testUserId, testStart, testEnd)).thenReturn(tripAgg);
        when(statisticsRepository.getUniqueLocationsCount(testUserId, testStart, testEnd)).thenReturn(2L);
        when(statisticsRepository.getTopPlaces(testUserId, testStart, testEnd, 5))
                .thenReturn(List.of(createTopPlace("Gym", 4, 300, 40.7505, -73.9934)));
        when(statisticsRepository.getMostActiveDay(testUserId, testStart, testEnd))
                .thenReturn(createMostActiveDay("01/04", "Thursday", 18.0, 3200.0, 3L));
        when(statisticsRepository.getRoutesStatistics(testUserId, testStart, testEnd))
                .thenReturn(createRoutesStats());
        when(statisticsRepository.getChartDataByWeeks(any(UUID.class), any(), any(), any()))
                .thenReturn(List.of(new ChartDataPoint("01/01", 15.0)));

        // When
        UserStatistics result = statisticsService.getStatistics(testUserId, testStart, testEnd, ChartGroupMode.WEEKS);

        // Then
        verify(statisticsRepository, times(6)).getChartDataByWeeks(any(), any(), any(), any());
        verify(statisticsRepository, times(0)).getChartDataByDays(any(), any(), any(), any());
    }

    @Test
    void getStatistics_HandlesTopPlacesWithCoordinates() {
        // Given
        TripAggregationResult tripAgg = new TripAggregationResult(15000.0, 7200L, 7500.0, 2L);
        when(statisticsRepository.getTripAggregations(testUserId, testStart, testEnd)).thenReturn(tripAgg);
        when(statisticsRepository.getUniqueLocationsCount(testUserId, testStart, testEnd)).thenReturn(5L);
        when(statisticsRepository.getTopPlaces(testUserId, testStart, testEnd, 5))
                .thenReturn(List.of(
                        createTopPlace("Home", 10, 5000, 40.7128, -74.0060),
                        createTopPlace("Work", 8, 4000, 40.7580, -73.9855),
                        createTopPlace("Gym", 3, 300, 40.7505, -73.9934)
                ));
        when(statisticsRepository.getMostActiveDay(testUserId, testStart, testEnd)).thenReturn(null);
        when(statisticsRepository.getRoutesStatistics(testUserId, testStart, testEnd))
                .thenReturn(createRoutesStats());
        when(statisticsRepository.getChartDataByDays(any(UUID.class), any(), any(), any())).thenReturn(List.of());

        // When
        UserStatistics result = statisticsService.getStatistics(testUserId, testStart, testEnd, ChartGroupMode.DAYS);

        // Then
        List<TopPlace> places = result.getPlaces();
        assertEquals(3, places.size());

        // Verify each place has proper coordinates
        for (TopPlace place : places) {
            assertNotNull(place.getCoordinates());
            assertEquals(2, place.getCoordinates().length);
            assertTrue(place.getCoordinates()[0] != 0.0); // Latitude
            assertTrue(place.getCoordinates()[1] != 0.0); // Longitude
        }
    }

    @Test
    void getStatistics_CalculatesAverageSpeedCorrectly() {
        // Given - 36 km in 2 hours = 18 km/h
        TripAggregationResult tripAgg = new TripAggregationResult(36000.0, 7200L, 18000.0, 2L);
        when(statisticsRepository.getTripAggregations(testUserId, testStart, testEnd)).thenReturn(tripAgg);
        when(statisticsRepository.getUniqueLocationsCount(testUserId, testStart, testEnd)).thenReturn(2L);
        when(statisticsRepository.getTopPlaces(testUserId, testStart, testEnd, 5)).thenReturn(List.of());
        when(statisticsRepository.getMostActiveDay(testUserId, testStart, testEnd)).thenReturn(null);
        when(statisticsRepository.getRoutesStatistics(testUserId, testStart, testEnd))
                .thenReturn(createRoutesStats());
        when(statisticsRepository.getChartDataByDays(any(UUID.class), any(), any(), any())).thenReturn(List.of());

        // When
        UserStatistics result = statisticsService.getStatistics(testUserId, testStart, testEnd, ChartGroupMode.DAYS);

        // Then
        assertEquals(18.0, result.getAverageSpeed(), 0.01); // 36km / 2h = 18 km/h
    }

    // Helper methods to create test data

    private TopPlace createTopPlace(String name, int visits, long duration, double lat, double lon) {
        return TopPlace.builder()
                .name(name)
                .visits(visits)
                .duration(duration)
                .coordinates(new double[]{lat, lon})
                .build();
    }

    private MostActiveDayDto createMostActiveDay(String date, String day, double distance, double travelTime, long locations) {
        return MostActiveDayDto.builder()
                .date(date)
                .day(day)
                .distanceTraveled(distance)
                .travelTime(travelTime)
                .locationsVisited(locations)
                .build();
    }

    private RoutesStatistics createRoutesStats() {
        return RoutesStatistics.builder()
                .avgTripDurationSeconds(3600.0)
                .uniqueRoutesCount(5)
                .longestTripDurationSeconds(5400.0)
                .longestTripDistanceMeters(25000.0)
                .mostCommonRoute(new MostCommonRoute("Home -> Work", 10))
                .build();
    }
}

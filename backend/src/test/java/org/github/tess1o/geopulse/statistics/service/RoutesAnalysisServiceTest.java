package org.github.tess1o.geopulse.statistics.service;

import org.github.tess1o.geopulse.statistics.model.MostCommonRoute;
import org.github.tess1o.geopulse.statistics.model.RoutesStatistics;
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
 * Unit tests for RoutesAnalysisService.
 * Tests route statistics calculation, trip duration analysis, and route frequency detection.
 */
class RoutesAnalysisServiceTest {

    private RoutesAnalysisService routesAnalysisService;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        routesAnalysisService = new RoutesAnalysisService();
        testUserId = UUID.randomUUID();
    }

    @Test
    void getRoutesStatistics_WithValidData_CalculatesCorrectStatistics() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithRoutes();

        // When
        RoutesStatistics result = routesAnalysisService.getRoutesStatistics(timeline);

        // Then
        assertNotNull(result);
        assertTrue(result.getAvgTripDuration() > 0);
        assertTrue(result.getUniqueRoutesCount() > 0);
        assertTrue(result.getLongestTripDuration() > 0);
        assertTrue(result.getLongestTripDistance() > 0);
        assertNotNull(result.getMostCommonRoute());
        
        // Verify specific calculations
        assertEquals(66.67, result.getAvgTripDuration(), 0.1); // (60 + 50 + 90) / 3
        assertEquals(90.0, result.getLongestTripDuration());
        assertEquals(15.0, result.getLongestTripDistance());
        assertEquals(3, result.getUniqueRoutesCount()); // Home->Work, Work->Home, Home->Store
    }

    @Test
    void getRoutesStatistics_WithEmptyTimeline_ReturnsZeroStatistics() {
        // Given
        MovementTimelineDTO emptyTimeline = new MovementTimelineDTO(testUserId, List.of(), List.of());

        // When
        RoutesStatistics result = routesAnalysisService.getRoutesStatistics(emptyTimeline);

        // Then
        assertNotNull(result);
        assertEquals(0.0, result.getAvgTripDuration());
        assertEquals(0, result.getUniqueRoutesCount());
        assertEquals(0.0, result.getLongestTripDuration());
        assertEquals(0.0, result.getLongestTripDistance());
        assertNotNull(result.getMostCommonRoute());
        assertEquals("", result.getMostCommonRoute().getName());
        assertEquals(0, result.getMostCommonRoute().getCount());
    }

    @Test
    void getRoutesStatistics_WithSingleTrip_HandlesSingleTripCorrectly() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithSingleTrip();

        // When
        RoutesStatistics result = routesAnalysisService.getRoutesStatistics(timeline);

        // Then
        assertNotNull(result);
        assertEquals(60.0, result.getAvgTripDuration());
        assertEquals(1, result.getUniqueRoutesCount());
        assertEquals(60.0, result.getLongestTripDuration());
        assertEquals(10.0, result.getLongestTripDistance());
        
        MostCommonRoute mostCommon = result.getMostCommonRoute();
        assertEquals("Home -> Work", mostCommon.getName());
        assertEquals(1, mostCommon.getCount());
    }

    @Test
    void getRoutesStatistics_WithRepeatedRoutes_IdentifiesMostCommonRoute() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithRepeatedRoutes();

        // When
        RoutesStatistics result = routesAnalysisService.getRoutesStatistics(timeline);

        // Then
        assertNotNull(result);
        
        MostCommonRoute mostCommon = result.getMostCommonRoute();
        assertEquals("Home -> Work", mostCommon.getName());
        assertEquals(2, mostCommon.getCount()); // Should appear twice
        
        assertTrue(result.getUniqueRoutesCount() >= 1);
    }

    @Test
    void getRoutesStatistics_WithNoStays_HandlesGracefully() {
        // Given - timeline with trips but no stays (edge case)
        MovementTimelineDTO timeline = new MovementTimelineDTO(testUserId,
                List.of(), // No stays
                List.of(createTrip("2024-01-01T10:00:00Z", 10.0, 60)));

        // When
        RoutesStatistics result = routesAnalysisService.getRoutesStatistics(timeline);

        // Then
        assertNotNull(result);
        assertEquals(60.0, result.getAvgTripDuration());
        assertEquals(0, result.getUniqueRoutesCount()); // No routes without stays
        assertEquals(60.0, result.getLongestTripDuration());
        assertEquals(10.0, result.getLongestTripDistance());
        assertEquals("", result.getMostCommonRoute().getName());
        assertEquals(0, result.getMostCommonRoute().getCount());
    }

    @Test
    void getRoutesStatistics_WithNoTrips_HandlesGracefully() {
        // Given - timeline with stays but no trips
        MovementTimelineDTO timeline = new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-01T11:00:00Z", "Work", 40.7580, -73.9855, 480)
                ),
                List.of()); // No trips

        // When
        RoutesStatistics result = routesAnalysisService.getRoutesStatistics(timeline);

        // Then
        assertNotNull(result);
        assertEquals(0.0, result.getAvgTripDuration());
        assertEquals(1, result.getUniqueRoutesCount()); // One route from Home -> Work
        assertEquals(0.0, result.getLongestTripDuration());
        assertEquals(0.0, result.getLongestTripDistance());
        
        MostCommonRoute mostCommon = result.getMostCommonRoute();
        assertEquals("Home -> Work", mostCommon.getName());
        assertEquals(1, mostCommon.getCount());
    }

    @Test
    void getRoutesStatistics_WithVariedTripDurations_CalculatesCorrectAverages() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithVariedTripDurations();

        // When
        RoutesStatistics result = routesAnalysisService.getRoutesStatistics(timeline);

        // Then
        assertNotNull(result);
        assertEquals(60.0, result.getAvgTripDuration(), 0.1); // (30 + 60 + 90) / 3
        assertEquals(90.0, result.getLongestTripDuration());
        assertEquals(15.0, result.getLongestTripDistance());
    }

    @Test
    void getRoutesStatistics_WithComplexRoutes_CountsUniqueRoutesCorrectly() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithComplexRoutes();

        // When
        RoutesStatistics result = routesAnalysisService.getRoutesStatistics(timeline);

        // Then
        assertNotNull(result);
        assertTrue(result.getUniqueRoutesCount() >= 4); // Multiple unique routes
        assertNotNull(result.getMostCommonRoute());
    }

    // Helper methods for creating test data

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

    private MovementTimelineDTO createTimelineWithSingleTrip() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-01T11:00:00Z", "Work", 40.7580, -73.9855, 420)
                ),
                List.of(createTrip("2024-01-01T10:00:00Z", 10.0, 60)));
    }

    private MovementTimelineDTO createTimelineWithRepeatedRoutes() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-01T11:00:00Z", "Work", 40.7580, -73.9855, 420),
                        createStay("2024-01-01T19:00:00Z", "Home", 40.7128, -74.0060, 780),
                        createStay("2024-01-02T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-02T11:00:00Z", "Work", 40.7580, -73.9855, 420)
                ),
                List.of(
                        createTrip("2024-01-01T10:00:00Z", 10.0, 60), // Home -> Work
                        createTrip("2024-01-01T18:00:00Z", 10.0, 50), // Work -> Home
                        createTrip("2024-01-02T10:00:00Z", 10.0, 60)  // Home -> Work (repeat)
                ));
    }

    private MovementTimelineDTO createTimelineWithVariedTripDurations() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-01T10:00:00Z", "Work", 40.7580, -73.9855, 30),
                        createStay("2024-01-01T11:30:00Z", "Gym", 40.7505, -73.9934, 30),
                        createStay("2024-01-01T13:00:00Z", "Store", 40.7614, -73.9776, 30)
                ),
                List.of(
                        createTrip("2024-01-01T09:30:00Z", 5.0, 30),   // Short trip
                        createTrip("2024-01-01T11:00:00Z", 10.0, 60),  // Medium trip
                        createTrip("2024-01-01T12:30:00Z", 15.0, 90)   // Long trip
                ));
    }

    private MovementTimelineDTO createTimelineWithComplexRoutes() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-01T10:00:00Z", "Work", 40.7580, -73.9855, 30),
                        createStay("2024-01-01T11:00:00Z", "Gym", 40.7505, -73.9934, 30),
                        createStay("2024-01-01T12:00:00Z", "Store", 40.7614, -73.9776, 30),
                        createStay("2024-01-01T13:00:00Z", "Restaurant", 40.7589, -73.9851, 30)
                ),
                List.of(
                        createTrip("2024-01-01T09:30:00Z", 5.0, 30),
                        createTrip("2024-01-01T10:30:00Z", 3.0, 20),
                        createTrip("2024-01-01T11:30:00Z", 8.0, 40),
                        createTrip("2024-01-01T12:30:00Z", 12.0, 60)
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
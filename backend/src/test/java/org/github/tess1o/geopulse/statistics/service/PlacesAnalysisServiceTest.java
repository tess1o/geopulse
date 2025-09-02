package org.github.tess1o.geopulse.statistics.service;

import org.github.tess1o.geopulse.statistics.model.TopPlace;
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
 * Unit tests for PlacesAnalysisService.
 * Tests location analysis, top places calculation, and unique location counting.
 */
class PlacesAnalysisServiceTest {

    private PlacesAnalysisService placesAnalysisService;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        placesAnalysisService = new PlacesAnalysisService();
        testUserId = UUID.randomUUID();
    }

    @Test
    void getPlacesStatistics_WithMultiplePlaces_ReturnsTopPlacesSorted() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithMultiplePlaces();

        // When
        List<TopPlace> result = placesAnalysisService.getPlacesStatistics(timeline);

        // Then
        assertNotNull(result);
        assertTrue(result.size() <= 5); // Should limit to top 5
        
        // Verify places are sorted by visit count (descending)
        for (int i = 0; i < result.size() - 1; i++) {
            assertTrue(result.get(i).getVisits() >= result.get(i + 1).getVisits(),
                    "Places should be sorted by visits descending");
        }
        
        // Verify each place has coordinates
        for (TopPlace place : result) {
            assertNotNull(place.getCoordinates());
            assertEquals(2, place.getCoordinates().length);
            assertNotNull(place.getName());
            assertTrue(place.getVisits() > 0);
            assertTrue(place.getDuration() > 0);
        }
    }

    @Test
    void getPlacesStatistics_WithIdenticalLocations_AggregatesCorrectly() {
        // Given - multiple stays at same location
        MovementTimelineDTO timeline = createTimelineWithIdenticalLocations();

        // When
        List<TopPlace> result = placesAnalysisService.getPlacesStatistics(timeline);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        
        TopPlace home = result.get(0);
        assertEquals("Home", home.getName());
        assertEquals(3, home.getVisits()); // Should aggregate visits
        assertEquals(135, home.getDuration()); // Should sum durations (30+60+45)
        assertNotNull(home.getCoordinates());
        assertEquals(40.7128, home.getCoordinates()[0], 0.0001);
        assertEquals(-74.0060, home.getCoordinates()[1], 0.0001);
    }

    @Test
    void getPlacesStatistics_WithEmptyTimeline_ReturnsEmptyList() {
        // Given
        MovementTimelineDTO emptyTimeline = new MovementTimelineDTO(testUserId, List.of(), List.of());

        // When
        List<TopPlace> result = placesAnalysisService.getPlacesStatistics(emptyTimeline);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getPlacesStatistics_WithSinglePlace_ReturnsSinglePlace() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithSinglePlace();

        // When
        List<TopPlace> result = placesAnalysisService.getPlacesStatistics(timeline);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        
        TopPlace place = result.get(0);
        assertEquals("Home", place.getName());
        assertEquals(1, place.getVisits());
        assertEquals(480, place.getDuration()); // 8 hours
        assertNotNull(place.getCoordinates());
    }

    @Test
    void getPlacesStatistics_SortsByVisitsAndDuration() {
        // Given - places with different visit counts and durations
        MovementTimelineDTO timeline = createTimelineWithVariedPlaces();

        // When
        List<TopPlace> result = placesAnalysisService.getPlacesStatistics(timeline);

        // Then
        assertNotNull(result);
        assertTrue(result.size() >= 2);
        
        // First place should have highest visit count
        TopPlace topPlace = result.get(0);
        assertEquals("Home", topPlace.getName());
        assertEquals(2, topPlace.getVisits());
        
        // Places with same visit count should be sorted by duration
        if (result.size() > 1) {
            for (int i = 0; i < result.size() - 1; i++) {
                TopPlace current = result.get(i);
                TopPlace next = result.get(i + 1);
                
                if (current.getVisits() == next.getVisits()) {
                    assertTrue(current.getDuration() >= next.getDuration(),
                            "Places with same visit count should be sorted by duration descending");
                }
            }
        }
    }

    @Test
    void getPlacesStatistics_LimitsToTop5Places() {
        // Given - timeline with more than 5 places
        MovementTimelineDTO timeline = createTimelineWithManyPlaces();

        // When
        List<TopPlace> result = placesAnalysisService.getPlacesStatistics(timeline);

        // Then
        assertNotNull(result);
        assertTrue(result.size() <= 5, "Should limit to top 5 places");
    }

    @Test
    void getUniqueLocationsCount_WithMultiplePlaces_CountsCorrectly() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithMultiplePlaces();

        // When
        long result = placesAnalysisService.getUniqueLocationsCount(timeline);

        // Then
        assertEquals(4, result); // Home, Work, Gym, Store
    }

    @Test
    void getUniqueLocationsCount_WithIdenticalLocations_CountsUnique() {
        // Given - multiple stays at same location
        MovementTimelineDTO timeline = createTimelineWithIdenticalLocations();

        // When
        long result = placesAnalysisService.getUniqueLocationsCount(timeline);

        // Then
        assertEquals(1, result); // Should count unique locations only
    }

    @Test
    void getUniqueLocationsCount_WithEmptyTimeline_ReturnsZero() {
        // Given
        MovementTimelineDTO emptyTimeline = new MovementTimelineDTO(testUserId, List.of(), List.of());

        // When
        long result = placesAnalysisService.getUniqueLocationsCount(emptyTimeline);

        // Then
        assertEquals(0, result);
    }

    @Test
    void getUniqueLocationsCount_WithSinglePlace_ReturnsOne() {
        // Given
        MovementTimelineDTO timeline = createTimelineWithSinglePlace();

        // When
        long result = placesAnalysisService.getUniqueLocationsCount(timeline);

        // Then
        assertEquals(1, result);
    }

    // Helper methods for creating test data

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

    private MovementTimelineDTO createTimelineWithIdenticalLocations() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 30),
                        createStay("2024-01-01T10:30:00Z", "Home", 40.7128, -74.0060, 60),
                        createStay("2024-01-01T11:30:00Z", "Home", 40.7128, -74.0060, 45)
                ),
                List.of(createTrip("2024-01-01T10:00:00Z", 5.0, 30)));
    }

    private MovementTimelineDTO createTimelineWithSinglePlace() {
        return new MovementTimelineDTO(testUserId,
                List.of(createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 480)),
                List.of());
    }

    private MovementTimelineDTO createTimelineWithVariedPlaces() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 480), // 1 visit, long duration
                        createStay("2024-01-01T18:00:00Z", "Home", 40.7128, -74.0060, 600), // 2nd visit to Home
                        createStay("2024-01-01T10:30:00Z", "Work", 40.7580, -73.9855, 420), // 1 visit, medium duration
                        createStay("2024-01-02T09:00:00Z", "Gym", 40.7505, -73.9934, 90) // 1 visit, short duration
                ),
                List.of());
    }

    private MovementTimelineDTO createTimelineWithManyPlaces() {
        return new MovementTimelineDTO(testUserId,
                List.of(
                        createStay("2024-01-01T09:00:00Z", "Home", 40.7128, -74.0060, 480),
                        createStay("2024-01-01T10:30:00Z", "Work", 40.7580, -73.9855, 420),
                        createStay("2024-01-01T18:00:00Z", "Gym", 40.7505, -73.9934, 90),
                        createStay("2024-01-02T09:00:00Z", "Store", 40.7614, -73.9776, 30),
                        createStay("2024-01-02T10:30:00Z", "Restaurant", 40.7589, -73.9851, 60),
                        createStay("2024-01-02T12:00:00Z", "Park", 40.7829, -73.9654, 120),
                        createStay("2024-01-02T14:00:00Z", "Library", 40.7831, -73.9712, 90)
                ),
                List.of());
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
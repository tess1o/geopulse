package org.github.tess1o.geopulse.service;

import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.timeline.model.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineTripDTO;
import org.github.tess1o.geopulse.timeline.merge.MovementTimelineMergerImpl;
import org.github.tess1o.geopulse.timeline.merge.TimelineMergeAnalysisService;
import org.github.tess1o.geopulse.timeline.merge.TimelineMergeExecutionService;
import org.github.tess1o.geopulse.timeline.merge.TripFilteringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MovementTimelineMergerImplTest {

    private UUID userId;
    private Instant baseTime;

    private final int mergeMaxDistanceMeters = 400;
    private final int mergeMaxTimeGapMinutes = 3;

    private final TimelineConfig timelineConfig = TimelineConfig.builder()
            .mergeMaxDistanceMeters(mergeMaxDistanceMeters)
            .mergeMaxTimeGapMinutes(mergeMaxTimeGapMinutes)
            .build();

    private MovementTimelineMergerImpl service;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        baseTime = Instant.parse("2024-01-01T10:00:00Z");
        
        // Create real service instances for integration testing
        TimelineMergeAnalysisService analysisService = new TimelineMergeAnalysisService();
        TimelineMergeExecutionService executionService = new TimelineMergeExecutionService();
        TripFilteringService tripFilteringService = new TripFilteringService();
        
        service = new MovementTimelineMergerImpl(analysisService, executionService, tripFilteringService);
    }

    @Test
    void testNoMergeRequired_DifferentLocations() {
        // Arrange: Home -> Park -> Work (all different locations)
        List<TimelineStayLocationDTO> stays = Arrays.asList(
                createStay(baseTime, "Home", 60, 40.0, -74.0),
                createStay(baseTime.plusSeconds(900), "Park", 60, 40.1, -74.1),
                createStay(baseTime.plusSeconds(1800), "Work", 120, 40.2, -74.2)
        );

        List<TimelineTripDTO> trips = Arrays.asList(
                createTrip(baseTime.plusSeconds(780), 10, 2.0, 40.05, -74.05),
                createTrip(baseTime.plusSeconds(1680), 15, 1.5, 40.15, -74.15)
        );

        MovementTimelineDTO timeline = new MovementTimelineDTO(userId, stays, trips);

        // Act
        MovementTimelineDTO result = service.mergeSameNamedLocations(timelineConfig, timeline);

        // Assert
        assertEquals(3, result.getStaysCount());
        assertEquals(2, result.getTripsCount());
        assertEquals(3, result.getStays().size());
        assertEquals(2, result.getTrips().size());
    }

    @Test
    void testMergeRequired_ShortDistance() {
        // Arrange: Home -> Park (1h) -> Park (2min) -> Home
        // Trip between Parks: 200m, 2min (should merge due to distance < 300m)
        List<TimelineStayLocationDTO> stays = Arrays.asList(
                createStay(baseTime, "Home", 60, 40.0, -74.0),
                createStay(baseTime.plusSeconds(900), "Park", 60, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2040), "Park", 2, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2640), "Home", 30, 40.0, -74.0)
        );

        List<TimelineTripDTO> trips = Arrays.asList(
                createTrip(baseTime.plusSeconds(780), 10, 2.0, 40.05, -74.05),
                createTrip(baseTime.plusSeconds(1980), 2, 0.2, 40.1, -74.1), // 200m trip
                createTrip(baseTime.plusSeconds(2520), 10, 2.0, 40.05, -74.05)
        );

        MovementTimelineDTO timeline = new MovementTimelineDTO(userId, stays, trips);

        // Act
        MovementTimelineDTO result = service.mergeSameNamedLocations(timelineConfig, timeline);

        // Assert
        assertEquals(3, result.getStaysCount());
        assertEquals(2, result.getTripsCount()); // Trip between Parks is removed

        // Check that Park stays were merged
        TimelineStayLocationDTO mergedPark = result.getStays().stream()
                .filter(s -> "Park".equals(s.getLocationName()))
                .findFirst()
                .orElse(null);

        assertNotNull(mergedPark);
        assertEquals(64, mergedPark.getStayDuration()); // 60 + 2 + 2 (trip duration)
    }

    @Test
    void testMergeRequired_ShortTime() {
        // Arrange: Home -> Park (1h) -> Park (2min) -> Home
        // Trip between Parks: 400m, 2min (should merge due to time < 3min)
        List<TimelineStayLocationDTO> stays = Arrays.asList(
                createStay(baseTime, "Home", 60, 40.0, -74.0),
                createStay(baseTime.plusSeconds(900), "Park", 60, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2040), "Park", 2, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2640), "Home", 30, 40.0, -74.0)
        );

        List<TimelineTripDTO> trips = Arrays.asList(
                createTrip(baseTime.plusSeconds(780), 10, 2.0, 40.05, -74.05),
                createTrip(baseTime.plusSeconds(1980), 2, 0.4, 40.1, -74.1), // 400m, 2min trip
                createTrip(baseTime.plusSeconds(2520), 10, 2.0, 40.05, -74.05)
        );

        MovementTimelineDTO timeline = new MovementTimelineDTO(userId, stays, trips);

        // Act
        MovementTimelineDTO result = service.mergeSameNamedLocations(timelineConfig, timeline);

        // Assert
        assertEquals(3, result.getStaysCount());
        assertEquals(2, result.getTripsCount()); // Trip between Parks is removed

        // Check that Park stays were merged
        TimelineStayLocationDTO mergedPark = result.getStays().stream()
                .filter(s -> "Park".equals(s.getLocationName()))
                .findFirst()
                .orElse(null);

        assertNotNull(mergedPark);
        assertEquals(64, mergedPark.getStayDuration()); // 60 + 2 + 2 (trip duration)
    }

    @Test
    void testNoMergeRequired_LongDistanceAndTime() {
        // Arrange: Home -> Park (1h) -> Park (2min) -> Home
        // Trip between Parks: 500m, 5min (should NOT merge - both thresholds exceeded)
        List<TimelineStayLocationDTO> stays = Arrays.asList(
                createStay(baseTime, "Home", 60, 40.0, -74.0),
                createStay(baseTime.plusSeconds(900), "Park", 60, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2340), "Park", 2, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2640), "Home", 30, 40.0, -74.0)
        );

        List<TimelineTripDTO> trips = Arrays.asList(
                createTrip(baseTime.plusSeconds(780), 10, 2.0, 40.05, -74.05),
                createTrip(baseTime.plusSeconds(1980), 5, 0.5, 40.1, -74.1), // 500m, 5min trip
                createTrip(baseTime.plusSeconds(2520), 10, 2.0, 40.05, -74.05)
        );

        MovementTimelineDTO timeline = new MovementTimelineDTO(userId, stays, trips);

        // Act
        MovementTimelineDTO result = service.mergeSameNamedLocations(timelineConfig, timeline);

        // Assert - no merge should occur
        assertEquals(4, result.getStaysCount());
        assertEquals(3, result.getTripsCount());
    }

    @Test
    void testMergeMultipleLocations() {
        // Arrange: Home -> Park -> Park -> Park -> Home (merge 3 Park locations)
        List<TimelineStayLocationDTO> stays = Arrays.asList(
                createStay(baseTime, "Home", 60, 40.0, -74.0),
                createStay(baseTime.plusSeconds(900), "Park", 30, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2040), "Park", 15, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2340), "Park", 45, 40.1, -74.1),
                createStay(baseTime.plusSeconds(3240), "Home", 30, 40.0, -74.0)
        );

        List<TimelineTripDTO> trips = Arrays.asList(
                createTrip(baseTime.plusSeconds(780), 10, 2.0, 40.05, -74.05),
                createTrip(baseTime.plusSeconds(1980), 2, 0.15, 40.1, -74.1), // 150m trip
                createTrip(baseTime.plusSeconds(2280), 1, 0.1, 40.1, -74.1),   // 100m trip
                createTrip(baseTime.plusSeconds(3120), 10, 2.0, 40.05, -74.05)
        );

        MovementTimelineDTO timeline = new MovementTimelineDTO(userId, stays, trips);

        // Act
        MovementTimelineDTO result = service.mergeSameNamedLocations(timelineConfig, timeline);

        // Assert
        assertEquals(3, result.getStaysCount());
        assertEquals(2, result.getTripsCount()); // All trips between Parks are removed

        // Check merged Park duration
        TimelineStayLocationDTO mergedPark = result.getStays().stream()
                .filter(s -> "Park".equals(s.getLocationName()))
                .findFirst()
                .orElse(null);

        assertNotNull(mergedPark);
        assertEquals(93, mergedPark.getStayDuration()); // 30 + 15 + 45 + 2 + 1 (trip durations)
    }

    @Test
    void testPartialMerge() {
        // Arrange: Home -> Park -> Park -> Mall -> Park -> Home
        // First two Parks should merge, but third Park (after Mall) should not
        List<TimelineStayLocationDTO> stays = Arrays.asList(
                createStay(baseTime, "Home", 60, 40.0, -74.0),
                createStay(baseTime.plusSeconds(900), "Park", 30, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2040), "Park", 15, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2640), "Mall", 60, 40.2, -74.2),
                createStay(baseTime.plusSeconds(4240), "Park", 20, 40.1, -74.1),
                createStay(baseTime.plusSeconds(5440), "Home", 30, 40.0, -74.0)
        );

        List<TimelineTripDTO> trips = Arrays.asList(
                createTrip(baseTime.plusSeconds(780), 10, 2.0, 40.05, -74.05),
                createTrip(baseTime.plusSeconds(1980), 2, 0.2, 40.1, -74.1),    // Short trip between Parks
                createTrip(baseTime.plusSeconds(2520), 10, 1.5, 40.15, -74.15), // To Mall
                createTrip(baseTime.plusSeconds(4120), 10, 1.5, 40.15, -74.15), // To Park again
                createTrip(baseTime.plusSeconds(5320), 10, 2.0, 40.05, -74.05)
        );

        MovementTimelineDTO timeline = new MovementTimelineDTO(userId, stays, trips);

        // Act
        MovementTimelineDTO result = service.mergeSameNamedLocations(timelineConfig, timeline);

        // Assert
        assertEquals(5, result.getStaysCount()); // Home, Merged Park, Mall, Park, Home
        assertEquals(4, result.getTripsCount()); // Only trip between first two Parks is removed

        // Count Park occurrences
        long parkCount = result.getStays().stream()
                .filter(s -> "Park".equals(s.getLocationName()))
                .count();
        assertEquals(2, parkCount); // One merged Park and one separate Park
    }

    @Test
    void testNullInput() {
        // Act & Assert
        assertNull(service.mergeSameNamedLocations(timelineConfig, null));
    }

    @Test
    void testEmptyLists() {
        // Arrange
        MovementTimelineDTO timeline = new MovementTimelineDTO(userId, new ArrayList<>(), new ArrayList<>());

        // Act
        MovementTimelineDTO result = service.mergeSameNamedLocations(timelineConfig, timeline);

        // Assert
        assertEquals(0, result.getStaysCount());
        assertEquals(0, result.getTripsCount());
    }

    @Test
    void testSingleStay() {
        // Arrange
        List<TimelineStayLocationDTO> stays = Arrays.asList(
                createStay(baseTime, "Home", 60, 40.0, -74.0)
        );

        MovementTimelineDTO timeline = new MovementTimelineDTO(userId, stays, new ArrayList<>());

        // Act
        MovementTimelineDTO result = service.mergeSameNamedLocations(timelineConfig, timeline);

        // Assert
        assertEquals(1, result.getStaysCount());
        assertEquals(0, result.getTripsCount());
    }

    @Test
    void testMergeWithMultipleTripsInBetween() {
        // Arrange: Home -> Park -> (multiple short trips) -> Park -> Home
        List<TimelineStayLocationDTO> stays = Arrays.asList(
                createStay(baseTime, "Home", 60, 40.0, -74.0),
                createStay(baseTime.plusSeconds(900), "Park", 30, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2640), "Park", 20, 40.1, -74.1),
                createStay(baseTime.plusSeconds(3840), "Home", 30, 40.0, -74.0)
        );

        List<TimelineTripDTO> trips = Arrays.asList(
                createTrip(baseTime.plusSeconds(780), 10, 2.0, 40.05, -74.05),
                createTrip(baseTime.plusSeconds(1980), 1, 0.1, 40.1, -74.1),   // 100m, 1min
                createTrip(baseTime.plusSeconds(2100), 1, 0.05, 40.1, -74.1),  // 50m, 1min
                createTrip(baseTime.plusSeconds(2220), 1, 0.08, 40.1, -74.1),  // 80m, 1min
                createTrip(baseTime.plusSeconds(3720), 10, 2.0, 40.05, -74.05)
        );

        MovementTimelineDTO timeline = new MovementTimelineDTO(userId, stays, trips);

        // Act
        MovementTimelineDTO result = service.mergeSameNamedLocations(timelineConfig, timeline);

        // Assert
        assertEquals(3, result.getStaysCount());
        assertEquals(2, result.getTripsCount()); // Only Home->Park and Park->Home trips remain

        // Check merged Park duration includes all intermediate trips
        TimelineStayLocationDTO mergedPark = result.getStays().stream()
                .filter(s -> "Park".equals(s.getLocationName()))
                .findFirst()
                .orElse(null);

        assertNotNull(mergedPark);
        assertEquals(53, mergedPark.getStayDuration()); // 30 + 20 + 1 + 1 + 1 (all trip durations)
    }

    @Test
    void testNoMergeWhenStaysAreNotConsecutive() {
        // Arrange: Home -> Park -> Work -> Park -> Home (Parks are not consecutive)
        List<TimelineStayLocationDTO> stays = Arrays.asList(
                createStay(baseTime, "Home", 60, 40.0, -74.0),
                createStay(baseTime.plusSeconds(900), "Park", 30, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2100), "Work", 60, 40.2, -74.2),
                createStay(baseTime.plusSeconds(3900), "Park", 30, 40.1, -74.1),
                createStay(baseTime.plusSeconds(5700), "Home", 30, 40.0, -74.0)
        );

        List<TimelineTripDTO> trips = Arrays.asList(
                createTrip(baseTime.plusSeconds(780), 10, 2.0, 40.05, -74.05),
                createTrip(baseTime.plusSeconds(1980), 10, 1.5, 40.15, -74.15),
                createTrip(baseTime.plusSeconds(3780), 10, 1.5, 40.15, -74.15),
                createTrip(baseTime.plusSeconds(5580), 10, 2.0, 40.05, -74.05)
        );

        MovementTimelineDTO timeline = new MovementTimelineDTO(userId, stays, trips);

        // Act
        MovementTimelineDTO result = service.mergeSameNamedLocations(timelineConfig, timeline);

        // Assert - No merge should occur because Parks are not consecutive
        assertEquals(5, result.getStaysCount());
        assertEquals(4, result.getTripsCount());
    }

    // Helper methods
    private TimelineStayLocationDTO createStay(Instant timestamp, String location, long duration,
                                               double lat, double lon) {
        return TimelineStayLocationDTO.builder()
                .timestamp(timestamp)
                .locationName(location)
                .stayDuration(duration)
                .latitude(lat)
                .longitude(lon)
                .build();
    }

    private TimelineTripDTO createTrip(Instant timestamp, long duration, double distanceKm,
                                       double lat, double lon) {
        return new TimelineTripDTO(timestamp, lat, lon, duration, distanceKm, "WALKING", List.of());
    }
}
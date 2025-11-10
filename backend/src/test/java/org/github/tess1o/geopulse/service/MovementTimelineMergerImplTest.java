package org.github.tess1o.geopulse.service;

import org.github.tess1o.geopulse.streaming.model.domain.RawTimeline;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.merge.MovementTimelineMergerImpl;
import org.github.tess1o.geopulse.streaming.merge.TimelineMergeAnalysisService;
import org.github.tess1o.geopulse.streaming.merge.TimelineMergeExecutionService;
import org.github.tess1o.geopulse.streaming.merge.TripFilteringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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
        List<Stay> stays = Arrays.asList(
                createStay(baseTime, "Home", 60, 40.0, -74.0),
                createStay(baseTime.plusSeconds(900), "Park", 60, 40.1, -74.1),
                createStay(baseTime.plusSeconds(1800), "Work", 120, 40.2, -74.2)
        );

        List<Trip> trips = Arrays.asList(
                createTrip(baseTime.plusSeconds(780), 10, 2000, 40.05, -74.05),
                createTrip(baseTime.plusSeconds(1680), 15, 1500, 40.15, -74.15)
        );

        RawTimeline timeline = new RawTimeline(userId, stays, trips, new ArrayList<>());

        // Act
        RawTimeline result = service.mergeSameNamedLocations(timelineConfig, timeline);

        // Assert
        assertEquals(3, result.getStays().size());
        assertEquals(2, result.getTrips().size());
    }

    @Test
    void testMergeRequired_ShortDistance() {
        // Arrange: Home -> Park (1h) -> Park (2min) -> Home
        // Trip between Parks: 200m, 2min (should merge due to distance < 300m)
        List<Stay> stays = Arrays.asList(
                createStay(baseTime, "Home", 60, 40.0, -74.0),
                createStay(baseTime.plusSeconds(900), "Park", 60, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2040), "Park", 2, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2640), "Home", 30, 40.0, -74.0)
        );

        List<Trip> trips = Arrays.asList(
                createTrip(baseTime.plusSeconds(780), 10, 2000, 40.05, -74.05),
                createTrip(baseTime.plusSeconds(1980), 2, 200, 40.1, -74.1), // 200m trip
                createTrip(baseTime.plusSeconds(2520), 10, 2000, 40.05, -74.05)
        );

        RawTimeline timeline = new RawTimeline(userId, stays, trips, new ArrayList<>());

        // Act
        RawTimeline result = service.mergeSameNamedLocations(timelineConfig, timeline);

        // Assert
        assertEquals(3, result.getStays().size());
        assertEquals(2, result.getTrips().size()); // Trip between Parks is removed

        // Check that Park stays were merged
        Stay mergedPark = result.getStays().stream()
                .filter(s -> "Park".equals(s.getLocationName()))
                .findFirst()
                .orElse(null);

        assertNotNull(mergedPark);
        assertEquals(64, mergedPark.getDuration().toSeconds()); // 60 + 2 + 2 (trip duration)
    }

    @Test
    void testMergeRequired_ShortTime() {
        // Arrange: Home -> Park (1h) -> Park (2min) -> Home
        // Trip between Parks: 400m, 2min (should merge due to time < 3min)
        List<Stay> stays = Arrays.asList(
                createStay(baseTime, "Home", 60, 40.0, -74.0),
                createStay(baseTime.plusSeconds(900), "Park", 60, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2040), "Park", 2, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2640), "Home", 30, 40.0, -74.0)
        );

        List<Trip> trips = Arrays.asList(
                createTrip(baseTime.plusSeconds(780), 10, 2000, 40.05, -74.05),
                createTrip(baseTime.plusSeconds(1980), 2, 400, 40.1, -74.1), // 400m, 2min trip
                createTrip(baseTime.plusSeconds(2520), 10, 2000, 40.05, -74.05)
        );

        RawTimeline timeline = new RawTimeline(userId, stays, trips, List.of());

        // Act
        RawTimeline result = service.mergeSameNamedLocations(timelineConfig, timeline);

        // Assert
        assertEquals(3, result.getStays().size());
        assertEquals(2, result.getTrips().size()); // Trip between Parks is removed

        // Check that Park stays were merged
        Stay mergedPark = result.getStays().stream()
                .filter(s -> "Park".equals(s.getLocationName()))
                .findFirst()
                .orElse(null);

        assertNotNull(mergedPark);
        assertEquals(64, mergedPark.getDuration().toSeconds()); // 60 + 2 + 2 (trip duration)
    }

    @Test
    void testNoMergeRequired_LongDistanceAndTime() {
        // Arrange: Home -> Park (1h) -> Park (2min) -> Home
        // Trip between Parks: 500m, 5min (should NOT merge - both thresholds exceeded)
        List<Stay> stays = Arrays.asList(
                createStay(baseTime, "Home", 60, 40.0, -74.0),
                createStay(baseTime.plusSeconds(900), "Park", 60, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2340), "Park", 2, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2640), "Home", 30, 40.0, -74.0)
        );

        List<Trip> trips = Arrays.asList(
                createTrip(baseTime.plusSeconds(780), 10, 2000, 40.05, -74.05),
                createTrip(baseTime.plusSeconds(1980), 5, 500, 40.1, -74.1), // 500m, 5min trip
                createTrip(baseTime.plusSeconds(2520), 10, 2000, 40.05, -74.05)
        );

        RawTimeline timeline = new RawTimeline(userId, stays, trips, List.of());

        // Act
        RawTimeline result = service.mergeSameNamedLocations(timelineConfig, timeline);

        // Assert - no merge should occur
        assertEquals(4, result.getStays().size());
        assertEquals(3, result.getTrips().size());
    }

    @Test
    void testMergeMultipleLocations() {
        // Arrange: Home -> Park -> Park -> Park -> Home (merge 3 Park locations)
        List<Stay> stays = Arrays.asList(
                createStay(baseTime, "Home", 60, 40.0, -74.0),
                createStay(baseTime.plusSeconds(900), "Park", 30, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2040), "Park", 15, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2340), "Park", 45, 40.1, -74.1),
                createStay(baseTime.plusSeconds(3240), "Home", 30, 40.0, -74.0)
        );

        List<Trip> trips = Arrays.asList(
                createTrip(baseTime.plusSeconds(780), 10, 2000, 40.05, -74.05),
                createTrip(baseTime.plusSeconds(1980), 2, 150, 40.1, -74.1), // 150m trip
                createTrip(baseTime.plusSeconds(2280), 1, 100, 40.1, -74.1),   // 100m trip
                createTrip(baseTime.plusSeconds(3120), 10, 2000, 40.05, -74.05)
        );

        RawTimeline timeline = new RawTimeline(userId, stays, trips, List.of());

        // Act
        RawTimeline result = service.mergeSameNamedLocations(timelineConfig, timeline);

        // Assert
        assertEquals(3, result.getStays().size());
        assertEquals(2, result.getTrips().size()); // All trips between Parks are removed

        // Check merged Park duration
        Stay mergedPark = result.getStays().stream()
                .filter(s -> "Park".equals(s.getLocationName()))
                .findFirst()
                .orElse(null);

        assertNotNull(mergedPark);
        assertEquals(93, mergedPark.getDuration().toSeconds()); // 30 + 15 + 45 + 2 + 1 (trip durations)
    }

    @Test
    void testPartialMerge() {
        // Arrange: Home -> Park -> Park -> Mall -> Park -> Home
        // First two Parks should merge, but third Park (after Mall) should not
        List<Stay> stays = Arrays.asList(
                createStay(baseTime, "Home", 60, 40.0, -74.0),
                createStay(baseTime.plusSeconds(900), "Park", 30, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2040), "Park", 15, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2640), "Mall", 60, 40.2, -74.2),
                createStay(baseTime.plusSeconds(4240), "Park", 20, 40.1, -74.1),
                createStay(baseTime.plusSeconds(5440), "Home", 30, 40.0, -74.0)
        );

        List<Trip> trips = Arrays.asList(
                createTrip(baseTime.plusSeconds(780), 10, 2000, 40.05, -74.05),
                createTrip(baseTime.plusSeconds(1980), 2, 200, 40.1, -74.1),    // Short trip between Parks
                createTrip(baseTime.plusSeconds(2520), 10, 1500, 40.15, -74.15), // To Mall
                createTrip(baseTime.plusSeconds(4120), 10, 1500, 40.15, -74.15), // To Park again
                createTrip(baseTime.plusSeconds(5320), 10, 2000, 40.05, -74.05)
        );

        RawTimeline timeline = new RawTimeline(userId, stays, trips, List.of());

        // Act
        RawTimeline result = service.mergeSameNamedLocations(timelineConfig, timeline);

        // Assert
        assertEquals(5, result.getStays().size()); // Home, Merged Park, Mall, Park, Home
        assertEquals(4, result.getTrips().size()); // Only trip between first two Parks is removed

        // Count Park occurrences
        long parkCount = result.getStays().stream()
                .filter(s -> "Park".equals(s.getLocationName()))
                .count();
        assertEquals(2, parkCount); // One merged Park and one separate Park
    }

    @Test
    void testEmptyLists() {
        // Arrange
        RawTimeline timeline = new RawTimeline(userId, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        // Act
        RawTimeline result = service.mergeSameNamedLocations(timelineConfig, timeline);

        // Assert
        assertEquals(0, result.getStays().size());
        assertEquals(0, result.getTrips().size());
    }

    @Test
    void testSingleStay() {
        // Arrange
        List<Stay> stays = Arrays.asList(
                createStay(baseTime, "Home", 60, 40.0, -74.0)
        );

        RawTimeline timeline = new RawTimeline(userId, stays, new ArrayList<>(), new ArrayList<>());

        // Act
        RawTimeline result = service.mergeSameNamedLocations(timelineConfig, timeline);

        // Assert
        assertEquals(1, result.getStays().size());
        assertEquals(0, result.getTrips().size());
    }

    @Test
    void testMergeWithMultipleTripsInBetween() {
        // Arrange: Home -> Park -> (multiple short trips) -> Park -> Home
        List<Stay> stays = Arrays.asList(
                createStay(baseTime, "Home", 60, 40.0, -74.0),
                createStay(baseTime.plusSeconds(900), "Park", 30, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2640), "Park", 20, 40.1, -74.1),
                createStay(baseTime.plusSeconds(3840), "Home", 30, 40.0, -74.0)
        );

        List<Trip> trips = Arrays.asList(
                createTrip(baseTime.plusSeconds(780), 10, 2000, 40.05, -74.05),
                createTrip(baseTime.plusSeconds(1980), 1, 100, 40.1, -74.1),   // 100m, 1min
                createTrip(baseTime.plusSeconds(2100), 1, 50, 40.1, -74.1),  // 50m, 1min
                createTrip(baseTime.plusSeconds(2220), 1, 80, 40.1, -74.1),  // 80m, 1min
                createTrip(baseTime.plusSeconds(3720), 10, 2000, 40.05, -74.05)
        );

        RawTimeline timeline = new RawTimeline(userId, stays, trips, new ArrayList<>());

        // Act
        RawTimeline result = service.mergeSameNamedLocations(timelineConfig, timeline);

        // Assert
        assertEquals(3, result.getStays().size());
        assertEquals(2, result.getTrips().size()); // Only Home->Park and Park->Home trips remain

        // Check merged Park duration includes all intermediate trips
        Stay mergedPark = result.getStays().stream()
                .filter(s -> "Park".equals(s.getLocationName()))
                .findFirst()
                .orElse(null);

        assertNotNull(mergedPark);
        assertEquals(53, mergedPark.getDuration().toSeconds()); // 30 + 20 + 1 + 1 + 1 (all trip durations)
    }

    @Test
    void testNoMergeWhenStaysAreNotConsecutive() {
        // Arrange: Home -> Park -> Work -> Park -> Home (Parks are not consecutive)
        List<Stay> stays = Arrays.asList(
                createStay(baseTime, "Home", 60, 40.0, -74.0),
                createStay(baseTime.plusSeconds(900), "Park", 30, 40.1, -74.1),
                createStay(baseTime.plusSeconds(2100), "Work", 60, 40.2, -74.2),
                createStay(baseTime.plusSeconds(3900), "Park", 30, 40.1, -74.1),
                createStay(baseTime.plusSeconds(5700), "Home", 30, 40.0, -74.0)
        );

        List<Trip> trips = Arrays.asList(
                createTrip(baseTime.plusSeconds(780), 10, 2000, 40.05, -74.05),
                createTrip(baseTime.plusSeconds(1980), 10, 1500, 40.15, -74.15),
                createTrip(baseTime.plusSeconds(3780), 10, 1500, 40.15, -74.15),
                createTrip(baseTime.plusSeconds(5580), 10, 2000, 40.05, -74.05)
        );

        RawTimeline timeline = new RawTimeline(userId, stays, trips, new ArrayList<>());

        // Act
        RawTimeline result = service.mergeSameNamedLocations(timelineConfig, timeline);

        // Assert - No merge should occur because Parks are not consecutive
        assertEquals(5, result.getStays().size());
        assertEquals(4, result.getTrips().size());
    }

    // Helper methods
    private Stay createStay(Instant timestamp, String location, long duration,
                                               double lat, double lon) {
        return Stay.builder()
                .startTime(timestamp)
                .locationName(location)
                .duration(Duration.ofSeconds(duration))
                .latitude(lat)
                .longitude(lon)
                .build();
    }

    private Trip createTrip(Instant timestamp, long duration, long distanceMeters,
                           double lat, double lon) {
        return Trip.builder()
                .duration(Duration.ofSeconds(duration))
                .startTime(timestamp)
                .distanceMeters(distanceMeters)
                .build();
    }
}
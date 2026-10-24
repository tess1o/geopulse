package org.github.tess1o.geopulse.service;

import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.merge.MovementTimelineMergerImpl;
import org.github.tess1o.geopulse.streaming.merge.TimelineMergeAnalysisService;
import org.github.tess1o.geopulse.streaming.merge.TimelineMergeExecutionService;
import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("unit")
public class MovementTimelineMergerImplTest {
    private final int mergeMaxDistanceMeters = 400;
    private final int mergeMaxTimeGapMinutes = 3;
    private final TimelineConfig timelineConfig = TimelineConfig.builder()
            .mergeMaxDistanceMeters(mergeMaxDistanceMeters)
            .mergeMaxTimeGapMinutes(mergeMaxTimeGapMinutes)
            .isMergeEnabled(true)
            .build();
    private MovementTimelineMergerImpl service;
    @BeforeEach
    void setUp() {
        // Create real service instances for integration testing
        TimelineMergeAnalysisService analysisService = new TimelineMergeAnalysisService();
        TimelineMergeExecutionService executionService = new TimelineMergeExecutionService();
        service = new MovementTimelineMergerImpl(analysisService, executionService);
    }
    @Test
    void testNoMergeRequired_DifferentLocations() {
        // Arrange: Home -> Park -> Work (all different locations)
        List<TimelineEvent> events = new Timeline()
                .stay("Home", 60)
                .trip(10, 2000)
                .stay("Park", 60)
                .trip(15, 1500)
                .stay("Work", 120)
                .events();
        // Act
        List<TimelineEvent> result = service.mergeSameNamedLocations(timelineConfig, events);
        // Assert
        assertEquals(3, stays(result).size());
        assertEquals(2, trips(result).size());
    }
    @Test
    void testMergeRequired_ShortDistanceAndTime() {
        // Arrange: Home -> Park (1h) -> Park (2min) -> Home
        // Trip between Parks: 200m, 2min (should merge - under both 400m and 3min)
        List<TimelineEvent> events = new Timeline()
                .stay("Home", 60)
                .trip(10, 2000)
                .stay("Park", 60)
                .trip(2, 200) // 200m trip
                .stay("Park", 2)
                .trip(10, 2000)
                .stay("Home", 30)
                .events();
        // Act
        List<TimelineEvent> result = service.mergeSameNamedLocations(timelineConfig, events);
        // Assert
        assertEquals(3, stays(result).size());
        assertEquals(2, trips(result).size()); // Trip between Parks is removed
        // Check that Park stays were merged
        Stay mergedPark = stays(result).stream()
                .filter(s -> "Park".equals(s.getLocationName()))
                .findFirst()
                .orElse(null);
        assertNotNull(mergedPark);
        assertEquals(64, mergedPark.getDuration().toMinutes()); // 60 + 2 + 2 (trip duration)
    }
    @Test
    void testNoMergeRequired_ShortTimeButLongDistance() {
        // Arrange: Home -> Park (1h) -> Park (2min) -> Home
        // Trip between Parks: 2000m, 2min (should NOT merge - a quick trip that went far is a real drive)
        List<TimelineEvent> events = new Timeline()
                .stay("Home", 60)
                .trip(10, 2000)
                .stay("Park", 60)
                .trip(2, 2000) // 2000m, 2min trip
                .stay("Park", 2)
                .trip(10, 2000)
                .stay("Home", 30)
                .events();
        // Act
        List<TimelineEvent> result = service.mergeSameNamedLocations(timelineConfig, events);
        // Assert - no merge should occur
        assertEquals(4, stays(result).size());
        assertEquals(3, trips(result).size());
    }
    @Test
    void testNoMergeRequired_ShortDistanceButLongTime() {
        // Arrange: Home -> Park (1h) -> Park (2min) -> Home
        // Trip between Parks: 200m, 20min (should NOT merge - a short trip that took long is a walk around)
        List<TimelineEvent> events = new Timeline()
                .stay("Home", 60)
                .trip(10, 2000)
                .stay("Park", 60)
                .trip(20, 200) // 200m, 20min trip
                .stay("Park", 2)
                .trip(10, 2000)
                .stay("Home", 30)
                .events();
        // Act
        List<TimelineEvent> result = service.mergeSameNamedLocations(timelineConfig, events);
        // Assert - no merge should occur
        assertEquals(4, stays(result).size());
        assertEquals(3, trips(result).size());
    }
    @Test
    void testNoMergeRequired_LongDistanceAndTime() {
        // Arrange: Home -> Park (1h) -> Park (2min) -> Home
        // Trip between Parks: 500m, 5min (should NOT merge - both thresholds exceeded)
        List<TimelineEvent> events = new Timeline()
                .stay("Home", 60)
                .trip(10, 2000)
                .stay("Park", 60)
                .trip(5, 500) // 500m, 5min trip
                .stay("Park", 2)
                .trip(10, 2000)
                .stay("Home", 30)
                .events();
        // Act
        List<TimelineEvent> result = service.mergeSameNamedLocations(timelineConfig, events);
        // Assert - no merge should occur
        assertEquals(4, stays(result).size());
        assertEquals(3, trips(result).size());
    }
    @Test
    void testTimeGapIsInMinutes() {
        // Arrange: Park -> 300m trip -> Park. Under the 3-minute threshold a trip of 90 seconds merges and one of
        // 4 minutes does not; a threshold read as 3 seconds would merge neither.
        List<TimelineEvent> shortTrip = new Timeline()
                .stay("Park", 60)
                .trip(Duration.ofSeconds(90), 300)
                .stay("Park", 60)
                .events();
        List<TimelineEvent> longTrip = new Timeline()
                .stay("Park", 60)
                .trip(4, 300)
                .stay("Park", 60)
                .events();
        // Act + Assert
        assertEquals(1, service.mergeSameNamedLocations(timelineConfig, shortTrip).size());
        assertEquals(3, service.mergeSameNamedLocations(timelineConfig, longTrip).size());
    }
    @Test
    void testMergeMultipleLocations() {
        // Arrange: Home -> Park -> Park -> Park -> Home (merge 3 Park locations)
        List<TimelineEvent> events = new Timeline()
                .stay("Home", 60)
                .trip(10, 2000)
                .stay("Park", 30)
                .trip(2, 150) // 150m trip
                .stay("Park", 15)
                .trip(1, 100)   // 100m trip
                .stay("Park", 45)
                .trip(10, 2000)
                .stay("Home", 30)
                .events();
        // Act
        List<TimelineEvent> result = service.mergeSameNamedLocations(timelineConfig, events);
        // Assert
        assertEquals(3, stays(result).size());
        assertEquals(2, trips(result).size()); // All trips between Parks are removed
        // Check merged Park duration
        Stay mergedPark = stays(result).stream()
                .filter(s -> "Park".equals(s.getLocationName()))
                .findFirst()
                .orElse(null);
        assertNotNull(mergedPark);
        assertEquals(93, mergedPark.getDuration().toMinutes()); // 30 + 15 + 45 + 2 + 1 (trip durations)
    }
    @Test
    void testPartialMerge() {
        // Arrange: Home -> Park -> Park -> Mall -> Park -> Home
        // First two Parks should merge, but third Park (after Mall) should not
        List<TimelineEvent> events = new Timeline()
                .stay("Home", 60)
                .trip(10, 2000)
                .stay("Park", 30)
                .trip(2, 200)    // Short trip between Parks
                .stay("Park", 15)
                .trip(10, 1500) // To Mall
                .stay("Mall", 60)
                .trip(10, 1500) // To Park again
                .stay("Park", 20)
                .trip(10, 2000)
                .stay("Home", 30)
                .events();
        // Act
        List<TimelineEvent> result = service.mergeSameNamedLocations(timelineConfig, events);
        // Assert
        assertEquals(5, stays(result).size()); // Home, Merged Park, Mall, Park, Home
        assertEquals(4, trips(result).size()); // Only trip between first two Parks is removed
        // Count Park occurrences
        long parkCount = stays(result).stream()
                .filter(s -> "Park".equals(s.getLocationName()))
                .count();
        assertEquals(2, parkCount); // One merged Park and one separate Park
    }
    @Test
    void testEmptyLists() {
        // Act
        List<TimelineEvent> result = service.mergeSameNamedLocations(timelineConfig, List.of());
        // Assert
        assertEquals(0, result.size());
    }
    @Test
    void testSingleStay() {
        // Arrange
        List<TimelineEvent> events = new Timeline()
                .stay("Home", 60)
                .events();
        // Act
        List<TimelineEvent> result = service.mergeSameNamedLocations(timelineConfig, events);
        // Assert
        assertEquals(1, stays(result).size());
        assertEquals(0, trips(result).size());
    }
    @Test
    void testMergeWithMultipleTripsInBetween() {
        // Arrange: Home -> Park -> (multiple short trips) -> Park -> Home
        List<TimelineEvent> events = new Timeline()
                .stay("Home", 60)
                .trip(10, 2000)
                .stay("Park", 30)
                .trip(Duration.ofSeconds(40), 100)   // 100m, 40s
                .trip(Duration.ofSeconds(40), 50)  // 50m, 40s
                .trip(Duration.ofSeconds(40), 80)  // 80m, 40s - 230m in 2min together
                .stay("Park", 20)
                .trip(10, 2000)
                .stay("Home", 30)
                .events();
        // Act
        List<TimelineEvent> result = service.mergeSameNamedLocations(timelineConfig, events);
        // Assert
        assertEquals(3, stays(result).size());
        assertEquals(2, trips(result).size()); // Only Home->Park and Park->Home trips remain
        // Check merged Park duration includes all intermediate trips
        Stay mergedPark = stays(result).stream()
                .filter(s -> "Park".equals(s.getLocationName()))
                .findFirst()
                .orElse(null);
        assertNotNull(mergedPark);
        assertEquals(Duration.ofMinutes(52), mergedPark.getDuration()); // 30 + 20 + 2 (all trip durations)
    }
    @Test
    void testNoMergeWhenStaysAreNotConsecutive() {
        // Arrange: Home -> Park -> Work -> Park -> Home (Parks are not consecutive)
        List<TimelineEvent> events = new Timeline()
                .stay("Home", 60)
                .trip(10, 2000)
                .stay("Park", 30)
                .trip(10, 1500)
                .stay("Work", 60)
                .trip(10, 1500)
                .stay("Park", 30)
                .trip(10, 2000)
                .stay("Home", 30)
                .events();
        // Act
        List<TimelineEvent> result = service.mergeSameNamedLocations(timelineConfig, events);
        // Assert - No merge should occur because Parks are not consecutive
        assertEquals(5, stays(result).size());
        assertEquals(4, trips(result).size());
    }
    @Test
    void testConsecutiveStaysAtSameLocationWithNoTrips_ShouldBeMerged() {
        // Arrange: Home -> Work -> Home (first) -> Home (second - NO trip between) -> Work
        List<TimelineEvent> events = new Timeline()
                .stay("Home", 60)
                .trip(10, 2000)    // Home -> Work
                .stay("Work", 120)
                .trip(10, 2000)   // Work -> Home (first)
                .stay("Home", 30)  // First Home stay
                // MISSING: No trip between Home stays!
                .stay("Home", 45)  // Second Home stay - NO TRIP!
                .trip(10, 2000)   // Home (second) -> Work
                .stay("Work", 60)
                .events();
        // Act
        List<TimelineEvent> result = service.mergeSameNamedLocations(timelineConfig, events);
        // Assert: The two consecutive Home stays MUST be merged into one
        assertEquals(4, stays(result).size(),
            "Two consecutive stays at same location with no trips should be merged into one");
        assertEquals(3, trips(result).size(),
            "All trips should remain");
        // Verify the merged Home stay has correct duration (30 + 45 = 75 minutes)
        long homeStayCount = stays(result).stream()
                .filter(s -> "Home".equals(s.getLocationName()))
                .count();
        assertEquals(2, homeStayCount,
            "Should have exactly 2 Home stays total (first one, and merged consecutive ones)");
        // Find the second Home stay (the merged one)
        Stay mergedHome = stays(result).stream()
                .filter(s -> "Home".equals(s.getLocationName()))
                .skip(1)  // Skip first Home
                .findFirst()
                .orElse(null);
        assertNotNull(mergedHome, "Merged Home stay should exist");
        assertEquals(75, mergedHome.getDuration().toMinutes(),
            "Merged Home stay should have combined duration: 30 + 45 = 75 minutes");
    }
    @Test
    void testZeroSecondTripStartingWithTheNextStayIsAbsorbed() {
        // Arrange: a GPS glitch at home - a trip of 0 seconds, starting at the same instant as the next stay
        List<TimelineEvent> events = new Timeline()
                .stay("Home", 117)
                .trip(Duration.ZERO, 73)
                .stay("Home", 62)
                .events();
        assertEquals(events.get(1).getStartTime(), events.get(2).getStartTime());
        // Act
        List<TimelineEvent> result = service.mergeSameNamedLocations(timelineConfig, events);
        // Assert: one Home stay, and the trip is not left inside it
        assertEquals(1, result.size());
        assertEquals(179, stays(result).getFirst().getDuration().toMinutes()); // 117 + 62 + 0
    }
    @Test
    void testNoMergeAcrossDataGap() {
        // Arrange: Home -> (no data) -> Home
        List<TimelineEvent> events = new Timeline()
                .stay("Home", 10)
                .gap(240)
                .stay("Home", 10)
                .events();
        // Act
        List<TimelineEvent> result = service.mergeSameNamedLocations(timelineConfig, events);
        // Assert
        assertEquals(events, result);
    }
    // Helper methods
    private static List<Stay> stays(List<TimelineEvent> events) {
        return events.stream().filter(Stay.class::isInstance).map(Stay.class::cast).toList();
    }
    private static List<Trip> trips(List<TimelineEvent> events) {
        return events.stream().filter(Trip.class::isInstance).map(Trip.class::cast).toList();
    }
    /** A timeline in order, each event starting where the previous one ended. Durations are in minutes. */
    private static class Timeline {
        private final List<TimelineEvent> events = new ArrayList<>();
        private Instant time = Instant.parse("2024-01-01T10:00:00Z");
        Timeline stay(String location, long minutes) {
            double offset = switch (location) {
                case "Home" -> 0.0;
                case "Park" -> 0.1;
                default -> 0.2;
            };
            return add(Stay.builder()
                    .startTime(time)
                    .locationName(location)
                    .duration(Duration.ofMinutes(minutes))
                    .latitude(40.0 + offset)
                    .longitude(-74.0 - offset)
                    .build());
        }
        Timeline trip(long minutes, long distanceMeters) {
            return trip(Duration.ofMinutes(minutes), distanceMeters);
        }
        Timeline trip(Duration duration, long distanceMeters) {
            return add(Trip.builder()
                    .duration(duration)
                    .startTime(time)
                    .distanceMeters(distanceMeters)
                    .build());
        }
        Timeline gap(long minutes) {
            return add(DataGap.builder().startTime(time).duration(Duration.ofMinutes(minutes)).build());
        }
        List<TimelineEvent> events() {
            return List.copyOf(events);
        }
        private Timeline add(TimelineEvent event) {
            events.add(event);
            time = event.getEndTime();
            return this;
        }
    }
}

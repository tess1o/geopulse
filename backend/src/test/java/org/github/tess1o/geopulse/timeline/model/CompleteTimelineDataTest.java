package org.github.tess1o.geopulse.timeline.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that verifies the complete timeline data structure with stays, trips, and data gaps.
 * This validates that our MovementTimelineDTO properly handles all timeline components
 * and that the data gap feature integrates correctly with existing timeline data.
 */
class CompleteTimelineDataTest {

    @Test
    void testCompleteTimelineDataStructure() {
        UUID userId = UUID.randomUUID();
        Instant baseTime = Instant.parse("2023-12-01T08:00:00Z");
        
        System.out.println("\n=== Complete Timeline Data Structure Test ===");
        
        // Create realistic timeline components
        List<TimelineStayLocationDTO> stays = createTestStays(baseTime);
        List<TimelineTripDTO> trips = createTestTrips(baseTime);
        List<TimelineDataGapDTO> dataGaps = createTestDataGaps(baseTime);
        
        // Create complete timeline
        MovementTimelineDTO completeTimeline = new MovementTimelineDTO(userId, stays, trips, dataGaps);
        completeTimeline.setDataSource(TimelineDataSource.LIVE);
        completeTimeline.setLastUpdated(Instant.now());
        
        // Verify timeline structure
        assertNotNull(completeTimeline, "Timeline should not be null");
        assertEquals(userId, completeTimeline.getUserId(), "User ID should match");
        assertEquals(TimelineDataSource.LIVE, completeTimeline.getDataSource(), "Data source should be set");
        
        // Verify all components are present
        assertNotNull(completeTimeline.getStays(), "Stays should not be null");
        assertNotNull(completeTimeline.getTrips(), "Trips should not be null"); 
        assertNotNull(completeTimeline.getDataGaps(), "Data gaps should not be null");
        
        assertEquals(3, completeTimeline.getStaysCount(), "Should have 3 stays");
        assertEquals(2, completeTimeline.getTripsCount(), "Should have 2 trips");
        assertEquals(2, completeTimeline.getDataGapsCount(), "Should have 2 data gaps");
        
        // Verify component details
        verifyStayData(completeTimeline.getStays());
        verifyTripData(completeTimeline.getTrips());
        verifyDataGapData(completeTimeline.getDataGaps());
        
        // Verify chronological ordering
        verifyChronologicalOrdering(completeTimeline);
        
        System.out.printf("✅ Complete timeline created: %d stays, %d trips, %d gaps\n",
                         completeTimeline.getStaysCount(), 
                         completeTimeline.getTripsCount(),
                         completeTimeline.getDataGapsCount());
        
        // Test constructor variations
        testConstructorVariations(userId, stays, trips, dataGaps);
        
        System.out.println("✅ Complete timeline data structure test passed!");
    }

    @Test 
    void testTimelineWithOnlyDataGaps() {
        // Test edge case: timeline with only data gaps (no stays/trips)
        UUID userId = UUID.randomUUID();
        Instant baseTime = Instant.parse("2023-12-01T08:00:00Z");
        
        List<TimelineDataGapDTO> onlyGaps = List.of(
                new TimelineDataGapDTO(baseTime, baseTime.plusSeconds(7200), 7200), // 2 hour gap
                new TimelineDataGapDTO(baseTime.plusSeconds(10800), baseTime.plusSeconds(18000), 7200) // Another 2 hour gap
        );
        
        MovementTimelineDTO gapOnlyTimeline = new MovementTimelineDTO(userId, List.of(), List.of(), onlyGaps);
        
        assertEquals(0, gapOnlyTimeline.getStaysCount(), "Should have no stays");
        assertEquals(0, gapOnlyTimeline.getTripsCount(), "Should have no trips");
        assertEquals(2, gapOnlyTimeline.getDataGapsCount(), "Should have 2 data gaps");
        
        System.out.println("✅ Gap-only timeline test passed!");
    }

    @Test
    void testTimelineDataGapFunctionality() {
        // Test TimelineDataGapDTO functionality in detail
        Instant startTime = Instant.parse("2023-12-01T10:00:00Z");
        Instant endTime = Instant.parse("2023-12-01T18:00:00Z"); // 8 hours later
        
        // Test constructor with automatic duration calculation
        TimelineDataGapDTO autoGap = new TimelineDataGapDTO(startTime, endTime);
        assertEquals(8 * 3600, autoGap.getDurationSeconds(), "Duration should be 8 hours in seconds");
        assertEquals(8 * 60, autoGap.getDurationMinutes(), "Duration should be 8 hours in minutes");
        
        // Test constructor with explicit duration
        TimelineDataGapDTO explicitGap = new TimelineDataGapDTO(startTime, endTime, 28800);
        assertEquals(28800, explicitGap.getDurationSeconds(), "Explicit duration should match");
        
        System.out.println("✅ Data gap functionality test passed!");
    }

    private List<TimelineStayLocationDTO> createTestStays(Instant baseTime) {
        return List.of(
                TimelineStayLocationDTO.builder()
                        .timestamp(baseTime)
                        .stayDuration(30 * 60) // 30 minutes at home
                        .latitude(40.7128)
                        .longitude(-74.0060)
                        .locationName("Home")
                        .build(),
                        
                TimelineStayLocationDTO.builder()
                        .timestamp(baseTime.plusSeconds(3900)) // After first trip + gap
                        .stayDuration(15 * 60) // 15 minutes at work before gap
                        .latitude(40.7580)
                        .longitude(-73.9855)
                        .locationName("Work")
                        .build(),
                        
                TimelineStayLocationDTO.builder()
                        .timestamp(baseTime.plusSeconds(32400)) // After major gap
                        .stayDuration(60 * 60) // 1 hour at home evening
                        .latitude(40.7128)
                        .longitude(-74.0060)
                        .locationName("Home")
                        .build()
        );
    }

    private List<TimelineTripDTO> createTestTrips(Instant baseTime) {
        return List.of(
                TimelineTripDTO.builder()
                        .timestamp(baseTime.plusSeconds(1800)) // After home stay
                        .latitude(40.7128)
                        .longitude(-74.0060)
                        .tripDuration(35 * 60) // 35 minutes to work
                        .distanceKm(15.2)
                        .movementType("public_transport")
                        .build(),
                        
                TimelineTripDTO.builder()
                        .timestamp(baseTime.plusSeconds(31500)) // After work gap
                        .latitude(40.7580)
                        .longitude(-73.9855)
                        .tripDuration(30 * 60) // 30 minutes home
                        .distanceKm(15.2)
                        .movementType("public_transport")
                        .build()
        );
    }

    private List<TimelineDataGapDTO> createTestDataGaps(Instant baseTime) {
        return List.of(
                // Major gap during work day (phone died)
                new TimelineDataGapDTO(
                        baseTime.plusSeconds(3900), // End of work stay 
                        baseTime.plusSeconds(31500), // Start of trip home
                        27600 // 7.5 hours gap
                ),
                
                // Smaller gap in the evening (brief phone restart)
                new TimelineDataGapDTO(
                        baseTime.plusSeconds(36000), // End of evening stay
                        baseTime.plusSeconds(38700), // 45 minutes later
                        2700 // 45 minutes gap
                )
        );
    }

    private void verifyStayData(List<TimelineStayLocationDTO> stays) {
        System.out.println("\nStay verification:");
        for (int i = 0; i < stays.size(); i++) {
            TimelineStayLocationDTO stay = stays.get(i);
            assertNotNull(stay.getTimestamp(), "Stay timestamp should not be null");
            assertTrue(stay.getStayDuration() > 0, "Stay duration should be positive");
            assertNotNull(stay.getLocationName(), "Location name should not be null");
            
            System.out.printf("  %d. %s at %.4f,%.4f (%d min)\n", 
                             i + 1, stay.getLocationName(), 
                             stay.getLatitude(), stay.getLongitude(),
                             stay.getStayDuration() / 60);
        }
    }

    private void verifyTripData(List<TimelineTripDTO> trips) {
        System.out.println("\nTrip verification:");
        for (int i = 0; i < trips.size(); i++) {
            TimelineTripDTO trip = trips.get(i);
            assertNotNull(trip.getTimestamp(), "Trip timestamp should not be null");
            assertTrue(trip.getTripDuration() > 0, "Trip duration should be positive");
            assertTrue(trip.getDistanceKm() > 0, "Trip distance should be positive");
            
            System.out.printf("  %d. %s trip: %.2f km in %d min\n",
                             i + 1, trip.getMovementType(),
                             trip.getDistanceKm(), trip.getTripDuration() / 60);
        }
    }

    private void verifyDataGapData(List<TimelineDataGapDTO> dataGaps) {
        System.out.println("\nData gap verification:");
        for (int i = 0; i < dataGaps.size(); i++) {
            TimelineDataGapDTO gap = dataGaps.get(i);
            assertNotNull(gap.getStartTime(), "Gap start time should not be null");
            assertNotNull(gap.getEndTime(), "Gap end time should not be null");
            assertTrue(gap.getDurationSeconds() > 0, "Gap duration should be positive");
            assertTrue(gap.getEndTime().isAfter(gap.getStartTime()), "End time should be after start time");
            
            long hours = gap.getDurationMinutes() / 60;
            long minutes = gap.getDurationMinutes() % 60;
            System.out.printf("  %d. Gap: %dh %dm (%d seconds)\n",
                             i + 1, hours, minutes, gap.getDurationSeconds());
        }
    }

    private void verifyChronologicalOrdering(MovementTimelineDTO timeline) {
        // Verify stays are in chronological order
        List<TimelineStayLocationDTO> stays = timeline.getStays();
        for (int i = 1; i < stays.size(); i++) {
            assertTrue(stays.get(i).getTimestamp().isAfter(stays.get(i-1).getTimestamp()),
                      "Stays should be in chronological order");
        }
        
        // Verify trips are in chronological order
        List<TimelineTripDTO> trips = timeline.getTrips();
        for (int i = 1; i < trips.size(); i++) {
            assertTrue(trips.get(i).getTimestamp().isAfter(trips.get(i-1).getTimestamp()),
                      "Trips should be in chronological order");
        }
        
        // Verify data gaps are in chronological order
        List<TimelineDataGapDTO> gaps = timeline.getDataGaps();
        for (int i = 1; i < gaps.size(); i++) {
            assertTrue(gaps.get(i).getStartTime().isAfter(gaps.get(i-1).getStartTime()),
                      "Data gaps should be in chronological order");
        }
        
        System.out.println("✅ Chronological ordering verified");
    }

    private void testConstructorVariations(UUID userId, List<TimelineStayLocationDTO> stays, 
                                         List<TimelineTripDTO> trips, List<TimelineDataGapDTO> dataGaps) {
        // Test basic constructor (no data gaps)
        MovementTimelineDTO basicTimeline = new MovementTimelineDTO(userId, stays, trips);
        assertEquals(0, basicTimeline.getDataGapsCount(), "Basic constructor should initialize empty data gaps list");
        
        // Test empty constructor
        MovementTimelineDTO emptyTimeline = new MovementTimelineDTO(userId);
        assertEquals(0, emptyTimeline.getStaysCount(), "Empty constructor should initialize empty stays list");
        assertEquals(0, emptyTimeline.getTripsCount(), "Empty constructor should initialize empty trips list");
        assertEquals(0, emptyTimeline.getDataGapsCount(), "Empty constructor should initialize empty data gaps list");
        
        System.out.println("✅ Constructor variations test passed");
    }
}
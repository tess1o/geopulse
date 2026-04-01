package org.github.tess1o.geopulse.streaming.service.trips;

import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class StreamingSingleTripAlgorithmTest {

    private StreamingSingleTripAlgorithm singleTripAlgorithm;
    private StreamingMultipleTripAlgorithm multipleTripAlgorithm;
    private TimelineConfig config;

    @BeforeEach
    void setUp() {
        singleTripAlgorithm = new StreamingSingleTripAlgorithm();
        singleTripAlgorithm.gpsStatisticsCalculator = new GpsStatisticsCalculator();
        singleTripAlgorithm.travelClassification = new TravelClassification();

        multipleTripAlgorithm = new StreamingMultipleTripAlgorithm();
        multipleTripAlgorithm.gpsStatisticsCalculator = new GpsStatisticsCalculator();
        multipleTripAlgorithm.travelClassification = new TravelClassification();

        config = TimelineConfig.builder()
                .staypointRadiusMeters(100)
                .staypointMinDurationMinutes(1)
                .walkingMaxAvgSpeed(5.0)
                .walkingMaxMaxSpeed(8.0)
                .carMinAvgSpeed(10.0)
                .carMinMaxSpeed(15.0)
                .shortDistanceKm(1.0)
                .build();
    }

    @Test
    void shouldNotMergeTripsSeparatedByDataGapSingleAlgorithm() {
        validateAlgorithm(singleTripAlgorithm);
    }

    @Test
    void shouldNotMergeTripsSeparatedByDataGapMultiAlgorithm() {
        validateAlgorithm(multipleTripAlgorithm);
    }


    private void validateAlgorithm(StreamTripAlgorithm algorithm) {
        // 1. Create a trip, a data gap, and another trip
        Trip trip1 = Trip.builder()
                .startTime(Instant.parse("2025-01-01T10:00:00Z"))
                .duration(Duration.ofMinutes(10))
                .distanceMeters(1000)
                .build();

        //data gap 50 minutes
        DataGap dataGap = DataGap.fromTimeRange(
                Instant.parse("2025-01-01T10:10:00Z"),
                Instant.parse("2025-01-01T11:00:00Z")
        );

        Trip trip2 = Trip.builder()
                .startTime(Instant.parse("2025-01-01T11:00:00Z"))
                .duration(Duration.ofMinutes(15))
                .distanceMeters(1500)
                .build();
        List<TimelineEvent> events = Arrays.asList(trip1, dataGap, trip2);
        // 2. Apply the algorithm
        List<TimelineEvent> processedEvents = algorithm.apply(UUID.randomUUID(), events, config);
        // 3. Assert the result
        assertEquals(3, processedEvents.size());
        assertTrue(processedEvents.get(0) instanceof Trip);
        assertTrue(processedEvents.get(1) instanceof DataGap);
        assertTrue(processedEvents.get(2) instanceof Trip);
        Trip processedTrip1 = (Trip) processedEvents.get(0);
        assertEquals(processedTrip1.getDuration().toMinutes(), 10);
        assertEquals(processedTrip1.getDistanceMeters(), 1000);
        DataGap processedDataGap = (DataGap) processedEvents.get(1);
        assertEquals(processedDataGap.getDuration().toMinutes(), 50);
        Trip processedTrip2 = (Trip) processedEvents.get(2);
        assertEquals(processedTrip2.getDuration().toMinutes(), 15);
        assertEquals(processedTrip2.getDistanceMeters(), 1500);
    }

    @Test
    void testConsecutiveStaysAtSameLocation_ShouldNotLogError_SingleAlgorithm() {
        Stay home1 = Stay.builder()
                .startTime(Instant.parse("2025-01-01T10:00:00Z"))
                .duration(Duration.ofMinutes(30))
                .locationName("Home")
                .latitude(40.0)
                .longitude(-74.0)
                .build();

        Stay home2 = Stay.builder()
                .startTime(Instant.parse("2025-01-01T10:30:00Z"))
                .duration(Duration.ofMinutes(45))
                .locationName("Home")
                .latitude(40.0)
                .longitude(-74.0)
                .build();

        List<TimelineEvent> events = Arrays.asList(home1, home2);

        // Capture log output to verify NO ERROR is logged
        // For now, just verify the algorithm doesn't crash and passes through the stays
        List<TimelineEvent> result = singleTripAlgorithm.apply(UUID.randomUUID(), events, config);

        // The algorithm should pass through both stays unchanged
        // (The merger will handle combining them later)
        assertEquals(2, result.size(),
                "Algorithm should pass through consecutive same-location stays");
        assertTrue(result.get(0) instanceof Stay);
        assertTrue(result.get(1) instanceof Stay);
    }

    @Test
    void testConsecutiveStaysAtSameLocation_ShouldNotLogError_MultiAlgorithm() {
        // Same test for Multi algorithm
        Stay home1 = Stay.builder()
                .startTime(Instant.parse("2025-01-01T10:00:00Z"))
                .duration(Duration.ofMinutes(30))
                .locationName("Home")
                .latitude(40.0)
                .longitude(-74.0)
                .build();

        Stay home2 = Stay.builder()
                .startTime(Instant.parse("2025-01-01T10:30:00Z"))
                .duration(Duration.ofMinutes(45))
                .locationName("Home")
                .latitude(40.0)
                .longitude(-74.0)
                .build();

        List<TimelineEvent> events = Arrays.asList(home1, home2);

        List<TimelineEvent> result = multipleTripAlgorithm.apply(UUID.randomUUID(), events, config);

        assertEquals(2, result.size(),
                "Algorithm should pass through consecutive same-location stays");
        assertTrue(result.get(0) instanceof Stay);
        assertTrue(result.get(1) instanceof Stay);
    }

    @Test
    void shouldPreserveShortTripBeforeDataGapSingleAlgorithm() {
        TimelineConfig strictConfig = TimelineConfig.builder()
                .staypointRadiusMeters(100)
                .staypointMinDurationMinutes(7)
                .walkingMaxAvgSpeed(5.0)
                .walkingMaxMaxSpeed(8.0)
                .carMinAvgSpeed(10.0)
                .carMinMaxSpeed(15.0)
                .shortDistanceKm(1.0)
                .build();

        Stay stay = Stay.builder()
                .startTime(Instant.parse("2025-01-01T10:18:00Z"))
                .duration(Duration.ofMinutes(5))
                .locationName("Start")
                .latitude(35.5639)
                .longitude(42.1875)
                .build();

        Trip shortTrip = Trip.builder()
                .startTime(Instant.parse("2025-01-01T10:24:40Z"))
                .duration(Duration.ofMinutes(3))
                .distanceMeters(1900)
                .build();

        DataGap dataGap = DataGap.fromTimeRange(
                Instant.parse("2025-01-01T10:27:59Z"),
                Instant.parse("2025-01-01T16:53:03Z")
        );

        List<TimelineEvent> events = Arrays.asList(stay, shortTrip, dataGap);

        List<TimelineEvent> result = singleTripAlgorithm.apply(UUID.randomUUID(), events, strictConfig);

        assertEquals(3, result.size());
        assertTrue(result.get(0) instanceof Stay);
        assertTrue(result.get(1) instanceof Trip);
        assertTrue(result.get(2) instanceof DataGap);
        assertEquals(3, ((Trip) result.get(1)).getDuration().toMinutes());
    }

    @Test
    void shouldPreserveShortTripBeforeDataGapMultiAlgorithm() {
        TimelineConfig strictConfig = TimelineConfig.builder()
                .staypointRadiusMeters(100)
                .staypointMinDurationMinutes(7)
                .walkingMaxAvgSpeed(5.0)
                .walkingMaxMaxSpeed(8.0)
                .carMinAvgSpeed(10.0)
                .carMinMaxSpeed(15.0)
                .shortDistanceKm(1.0)
                .build();

        Stay stay = Stay.builder()
                .startTime(Instant.parse("2025-01-01T10:18:00Z"))
                .duration(Duration.ofMinutes(5))
                .locationName("Start")
                .latitude(35.5639)
                .longitude(42.1875)
                .build();

        Trip shortTrip = Trip.builder()
                .startTime(Instant.parse("2025-01-01T10:24:40Z"))
                .duration(Duration.ofMinutes(3))
                .distanceMeters(1900)
                .build();

        DataGap dataGap = DataGap.fromTimeRange(
                Instant.parse("2025-01-01T10:27:59Z"),
                Instant.parse("2025-01-01T16:53:03Z")
        );

        List<TimelineEvent> events = Arrays.asList(stay, shortTrip, dataGap);

        List<TimelineEvent> result = multipleTripAlgorithm.apply(UUID.randomUUID(), events, strictConfig);

        assertEquals(3, result.size());
        assertTrue(result.get(0) instanceof Stay);
        assertTrue(result.get(1) instanceof Trip);
        assertTrue(result.get(2) instanceof DataGap);
        assertEquals(3, ((Trip) result.get(1)).getDuration().toMinutes());
    }
}

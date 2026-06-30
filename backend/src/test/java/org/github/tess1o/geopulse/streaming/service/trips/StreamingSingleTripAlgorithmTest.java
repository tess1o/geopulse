package org.github.tess1o.geopulse.streaming.service.trips;

import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void shouldCreateContinuityTripBetweenDifferentConsecutiveStays_SingleAlgorithm() {
        Stay home = Stay.builder()
                .startTime(Instant.parse("2025-01-01T10:00:00Z"))
                .duration(Duration.ofMinutes(30))
                .locationName("Home")
                .latitude(40.0)
                .longitude(-74.0)
                .build();

        Stay office = Stay.builder()
                .startTime(Instant.parse("2025-01-01T10:45:00Z"))
                .duration(Duration.ofMinutes(45))
                .locationName("Office")
                .latitude(40.12)
                .longitude(-74.2)
                .build();

        List<TimelineEvent> result = singleTripAlgorithm.apply(UUID.randomUUID(), Arrays.asList(home, office), config);

        assertEquals(3, result.size());
        assertTrue(result.get(0) instanceof Stay);
        assertTrue(result.get(1) instanceof Trip);
        assertTrue(result.get(2) instanceof Stay);

        Trip continuityTrip = (Trip) result.get(1);
        assertNotNull(continuityTrip.getStartPoint());
        assertNotNull(continuityTrip.getEndPoint());
        assertTrue(!continuityTrip.getDuration().isNegative());
    }

    @Test
    void shouldPreserveLeadingTripBeforeFirstStay_SingleAlgorithm() {
        Trip leadingTrip = Trip.builder()
                .startTime(Instant.parse("2026-06-20T01:27:32Z"))
                .duration(Duration.ofMinutes(60))
                .distanceMeters(12000)
                .build();

        Stay firstStay = Stay.builder()
                .startTime(Instant.parse("2026-06-20T03:17:57Z"))
                .duration(Duration.ofMinutes(12))
                .locationName("First detected stay")
                .latitude(43.7576)
                .longitude(130.8258)
                .build();

        List<TimelineEvent> result = singleTripAlgorithm.apply(UUID.randomUUID(), Arrays.asList(leadingTrip, firstStay), config);

        assertEquals(2, result.size());
        assertTrue(result.get(0) instanceof Trip);
        assertTrue(result.get(1) instanceof Stay);
        assertEquals(Instant.parse("2026-06-20T01:27:32Z"), result.get(0).getStartTime());
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
    void shouldCreateContinuityTripBetweenDifferentConsecutiveStays_MultiAlgorithm() {
        Stay home = Stay.builder()
                .startTime(Instant.parse("2025-01-01T10:00:00Z"))
                .duration(Duration.ofMinutes(30))
                .locationName("Home")
                .latitude(40.0)
                .longitude(-74.0)
                .build();

        Stay office = Stay.builder()
                .startTime(Instant.parse("2025-01-01T10:45:00Z"))
                .duration(Duration.ofMinutes(45))
                .locationName("Office")
                .latitude(40.12)
                .longitude(-74.2)
                .build();

        List<TimelineEvent> result = multipleTripAlgorithm.apply(UUID.randomUUID(), Arrays.asList(home, office), config);

        assertEquals(3, result.size());
        assertTrue(result.get(0) instanceof Stay);
        assertTrue(result.get(1) instanceof Trip);
        assertTrue(result.get(2) instanceof Stay);

        Trip continuityTrip = (Trip) result.get(1);
        assertNotNull(continuityTrip.getStartPoint());
        assertNotNull(continuityTrip.getEndPoint());
        assertTrue(!continuityTrip.getDuration().isNegative());
    }

    @Test
    void shouldPreserveLeadingTripBeforeFirstStay_MultiAlgorithm() {
        Trip leadingTrip = Trip.builder()
                .startTime(Instant.parse("2026-06-20T01:27:32Z"))
                .duration(Duration.ofMinutes(60))
                .distanceMeters(12000)
                .build();

        Stay firstStay = Stay.builder()
                .startTime(Instant.parse("2026-06-20T03:17:57Z"))
                .duration(Duration.ofMinutes(12))
                .locationName("First detected stay")
                .latitude(43.7576)
                .longitude(130.8258)
                .build();

        List<TimelineEvent> result = multipleTripAlgorithm.apply(UUID.randomUUID(), Arrays.asList(leadingTrip, firstStay), config);

        assertEquals(2, result.size());
        assertTrue(result.get(0) instanceof Trip);
        assertTrue(result.get(1) instanceof Stay);
        assertEquals(Instant.parse("2026-06-20T01:27:32Z"), result.get(0).getStartTime());
    }

    @Test
    void shouldPreserveMultipleLeadingTripsBeforeFirstStay_MultiAlgorithm() {
        Trip leadingTrip1 = Trip.builder()
                .startTime(Instant.parse("2026-06-20T01:27:32Z"))
                .duration(Duration.ofMinutes(30))
                .distanceMeters(6000)
                .tripType(TripType.WALK)
                .build();

        Trip leadingTrip2 = Trip.builder()
                .startTime(Instant.parse("2026-06-20T01:57:32Z"))
                .duration(Duration.ofMinutes(35))
                .distanceMeters(12000)
                .tripType(TripType.CAR)
                .build();

        Stay firstStay = Stay.builder()
                .startTime(Instant.parse("2026-06-20T03:17:57Z"))
                .duration(Duration.ofMinutes(12))
                .locationName("First detected stay")
                .latitude(43.7576)
                .longitude(130.8258)
                .build();

        List<TimelineEvent> result = multipleTripAlgorithm.apply(
                UUID.randomUUID(),
                Arrays.asList(leadingTrip1, leadingTrip2, firstStay),
                config
        );

        assertEquals(3, result.size());
        assertTrue(result.get(0) instanceof Trip);
        assertTrue(result.get(1) instanceof Trip);
        assertTrue(result.get(2) instanceof Stay);
        assertEquals(Instant.parse("2026-06-20T01:27:32Z"), result.get(0).getStartTime());
        assertEquals(Instant.parse("2026-06-20T01:57:32Z"), result.get(1).getStartTime());
    }

    @Test
    void shouldPreserveBoatWalkModeChange_MultiAlgorithm() {
        Stay start = createStay("2026-06-25T18:00:00Z", "Dock");
        Trip boat = createTrip("2026-06-25T18:19:11Z", Duration.ofMinutes(9), 1478.0, TripType.BOAT);
        Trip walk = createTrip("2026-06-25T18:31:11Z", Duration.ofMinutes(14), 851.0, TripType.WALK);
        Stay end = createStay("2026-06-25T18:50:00Z", "City");

        List<TimelineEvent> result = multipleTripAlgorithm.apply(
                UUID.randomUUID(),
                Arrays.asList(start, boat, walk, end),
                config
        );

        List<Trip> trips = result.stream()
                .filter(Trip.class::isInstance)
                .map(Trip.class::cast)
                .toList();

        assertEquals(2, trips.size());
        assertEquals(TripType.BOAT, trips.get(0).getTripType());
        assertEquals(TripType.WALK, trips.get(1).getTripType());
        assertEquals(1478.0, trips.get(0).getDistanceMeters());
        assertEquals(851.0, trips.get(1).getDistanceMeters());
    }

    @Test
    void shouldPreserveWalkBoatModeChange_MultiAlgorithm() {
        Stay start = createStay("2026-06-25T18:00:00Z", "City");
        Trip walk = createTrip("2026-06-25T18:05:00Z", Duration.ofMinutes(8), 700.0, TripType.WALK);
        Trip boat = createTrip("2026-06-25T18:13:00Z", Duration.ofMinutes(18), 2200.0, TripType.BOAT);
        Stay end = createStay("2026-06-25T18:35:00Z", "Dock");

        List<TimelineEvent> result = multipleTripAlgorithm.apply(
                UUID.randomUUID(),
                Arrays.asList(start, walk, boat, end),
                config
        );

        List<Trip> trips = result.stream()
                .filter(Trip.class::isInstance)
                .map(Trip.class::cast)
                .toList();

        assertEquals(2, trips.size());
        assertEquals(TripType.WALK, trips.get(0).getTripType());
        assertEquals(TripType.BOAT, trips.get(1).getTripType());
    }

    @Test
    void shouldMergeTinyBoatFragmentIntoWalk_MultiAlgorithm() {
        Stay start = createStay("2026-06-25T18:00:00Z", "Park");
        Trip walkBefore = createTrip("2026-06-25T18:05:00Z", Duration.ofMinutes(10), 800.0, TripType.WALK);
        Trip shortBoat = createTrip("2026-06-25T18:15:00Z", Duration.ofSeconds(45), 40.0, TripType.BOAT);
        Trip walkAfter = createTrip("2026-06-25T18:16:00Z", Duration.ofMinutes(10), 820.0, TripType.WALK);
        Stay end = createStay("2026-06-25T18:30:00Z", "Center");

        List<TimelineEvent> result = multipleTripAlgorithm.apply(
                UUID.randomUUID(),
                Arrays.asList(start, walkBefore, shortBoat, walkAfter, end),
                config
        );

        List<Trip> trips = result.stream()
                .filter(Trip.class::isInstance)
                .map(Trip.class::cast)
                .toList();

        assertEquals(1, trips.size());
        assertEquals(TripType.WALK, trips.getFirst().getTripType());
    }

    @Test
    void shouldStillPreserveWalkCarModeChange_MultiAlgorithm() {
        Stay start = createStay("2026-06-25T08:00:00Z", "Home");
        Trip walk = createTrip("2026-06-25T08:10:00Z", Duration.ofMinutes(12), 900.0, TripType.WALK);
        Trip car = createTrip("2026-06-25T08:22:00Z", Duration.ofMinutes(20), 8000.0, TripType.CAR);
        Stay end = createStay("2026-06-25T09:00:00Z", "Office");

        List<TimelineEvent> result = multipleTripAlgorithm.apply(
                UUID.randomUUID(),
                Arrays.asList(start, walk, car, end),
                config
        );

        List<Trip> trips = result.stream()
                .filter(Trip.class::isInstance)
                .map(Trip.class::cast)
                .toList();

        assertEquals(2, trips.size());
        assertEquals(TripType.WALK, trips.get(0).getTripType());
        assertEquals(TripType.CAR, trips.get(1).getTripType());
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

    private Stay createStay(String startTime, String locationName) {
        return Stay.builder()
                .startTime(Instant.parse(startTime))
                .duration(Duration.ofMinutes(10))
                .locationName(locationName)
                .latitude(40.0)
                .longitude(-74.0)
                .build();
    }

    private Trip createTrip(String startTime, Duration duration, double distanceMeters, TripType tripType) {
        return Trip.builder()
                .startTime(Instant.parse(startTime))
                .duration(duration)
                .distanceMeters(distanceMeters)
                .tripType(tripType)
                .build();
    }
}

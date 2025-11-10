package org.github.tess1o.geopulse.streaming.service.trips;

import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}

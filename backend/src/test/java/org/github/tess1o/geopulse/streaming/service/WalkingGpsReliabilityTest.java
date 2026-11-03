package org.github.tess1o.geopulse.streaming.service;

import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.trips.TravelClassification;
import org.github.tess1o.geopulse.streaming.service.trips.TripGpsStatistics;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.github.tess1o.geopulse.streaming.model.dto.TripClassificationDetailsDTO;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class WalkingGpsReliabilityTest {
    private final TravelClassification classification = new TravelClassification();
    private final TimelineConfig config = TimelineConfig.builder()
            .walkingMaxAvgSpeed(6.8).walkingMaxMaxSpeed(8.0)
            .runningEnabled(true).runningMinAvgSpeed(7.0).runningMaxAvgSpeed(14.0).runningMaxMaxSpeed(18.0)
            .bicycleEnabled(false).trainEnabled(false).flightEnabled(false).boatEnabled(false)
            .carEnabled(true).carMinAvgSpeed(10.0).carMinMaxSpeed(15.0).shortDistanceKm(1.0)
            .build();

    @Test
    void reportedTripUsesWalkingGpsThroughBothEntryPoints() {
        var trip = reportedTrip();
        assertEquals(TripType.WALK, classification.classifyTravelType(trip, config));
        assertEquals(TripType.WALK, classification.classifyTravelType(
                new TripGpsStatistics(trip.getAvgGpsSpeed(), trip.getMaxGpsSpeed(), trip.getSpeedVariance(), 0),
                Duration.ofSeconds(2230), 5002, config));
        assertEquals(5002, trip.getDistanceMeters());
    }

    @Test
    void detailsUseSameSpeedAndExplainWalkingException() {
        var trip = reportedTrip();
        var details = details(trip);
        assertEquals("WALK", details.algorithmClassification());
        assertTrue(details.statistics().gpsReliable());
        var walk = details.steps().stream().filter(step -> step.tripType().equals("WALK")).findFirst().orElseThrow();
        assertTrue(walk.passed());
        assertTrue(walk.reason().contains("Retaining GPS speeds"), walk.reason());
        assertTrue(walk.reason().contains("calculated"), walk.reason());
        assertEquals(4.603883408071739, walk.checks().getFirst().actual(), 0.000001);
        assertEquals(5.4, details.statistics().maxGpsSpeedKmh(), 0.000001);
        assertEquals(5002 * 3.6 / 2230, details.statistics().calculatedAvgSpeedKmh(), 0.000001);
        assertEquals(5.4, walk.checks().get(1).actual(), 0.000001);
        assertTrue(details.finalReason().contains("WALK"));
        assertTrue(details.finalReason().contains("4.6"));
        assertEquals("RUNNING", details.currentClassification()); // Reading details must not rewrite saved data.
        assertEquals(5002, details.distanceMeters());
    }

    @Test
    void otherReliabilityAndDistanceSafeguardsRemain() {
        assertEquals(TripType.UNKNOWN, classify(4.6, 5.4, 1400.0));
        assertEquals(TripType.RUNNING, classification.classifyTravelType(
                TripGpsStatistics.empty(), Duration.ofHours(1), 9000, config));
    }

    @ParameterizedTest(name = "GPS {0}/{1}, calculated {2}, walking limits {3}/{4}")
    @CsvSource({
            "4.6, 5.4, 8.08, 6.8, 8.0, true,  4.6, WALK",
            "6.8, 8.0, 9.0,  6.8, 8.0, true,  6.8, WALK",
            "6.8, 8.01,9.0,  6.8, 8.0, false, 9.0, RUNNING",
            "6.81,8.0, 9.0,  6.8, 8.0, false, 9.0, RUNNING",
            "4.6, 5.4, 8.08, 4.5, 8.0, false, 8.08,RUNNING",
            "4.6, 5.4, 8.08, 6.8, 5.3, false, 8.08,RUNNING",
            "4.6, 5.4, 8.08, 4.6, 5.4, true,  4.6, CAR",
            "4.6, 5.4,19.999,6.8, 8.0, true,  4.6, CAR",
            "4.6, 5.4,20.0,  6.8, 8.0, false,20.0, CAR",
            "4.6, 5.4,20.001,6.8, 8.0, false,20.001,CAR",
            "6.8, 8.0, 2.0,  6.8, 8.0, false, 2.0, WALK",
            "9.0,12.0, 9.0,  6.8, 8.0, true,  9.0, RUNNING",
            "4.6,25.0,15.0,  6.8, 8.0, false,15.0, CAR",
            "4.6, 5.4,10.0,  6.8, 8.0, true,  4.6, CAR"
    })
    void classificationAndDetailsAgreeOnReliabilityAndSelectedSpeed(
            double avg, double max, double calculated, double walkingAvg, double walkingMax,
            boolean reliable, double selectedAvg, TripType expected) {
        config.setWalkingMaxAvgSpeed(walkingAvg);
        config.setWalkingMaxMaxSpeed(walkingMax);
        var trip = reportedTrip();
        trip.setTripDuration(3600);
        trip.setDistanceMeters(Math.round(calculated * 1000));
        trip.setAvgGpsSpeed(avg / 3.6);
        trip.setMaxGpsSpeed(max / 3.6);

        assertEquals(expected, classification.classifyTravelType(trip, config));
        assertEquals(expected, classify(avg, max, calculated));
        var details = details(trip);
        assertEquals(expected.name(), details.algorithmClassification());
        assertEquals(reliable, details.statistics().gpsReliable());
        var avgChecks = details.steps().stream().flatMap(step -> step.checks().stream())
                .filter(check -> check.name().startsWith("Average Speed")).toList();
        assertFalse(avgChecks.isEmpty());
        avgChecks.forEach(check -> assertEquals(selectedAvg, check.actual(), 0.000001));
        var maxChecks = details.steps().stream().flatMap(step -> step.checks().stream())
                .filter(check -> check.name().equals("Max Speed")).toList();
        assertFalse(maxChecks.isEmpty());
        maxChecks.forEach(check -> assertEquals(max, check.actual(), 0.000001));
        var passedSteps = details.steps().stream().filter(step -> step.checked() && step.passed()).toList();
        assertFalse(passedSteps.isEmpty());
        assertEquals(expected.name(), passedSteps.getLast().tripType());
        if (!reliable) {
            assertTrue(details.steps().stream().anyMatch(step -> step.reason().contains("Using calculated average speed")));
            assertTrue(details.steps().stream().noneMatch(step -> step.reason().contains("Retaining GPS speeds")));
        }
    }

    @ParameterizedTest
    @CsvSource(value = {"null,null", "null,1.5", "1.28,null"}, nullValues = "null")
    void missingGpsStatisticsCannotEnableWalkingException(Double avg, Double max) {
        assertFalse(classification.isGpsReliable(avg, max, 8.08, config));
        var trip = reportedTrip();
        trip.setAvgGpsSpeed(avg);
        trip.setMaxGpsSpeed(max);
        assertEquals(TripType.RUNNING, classification.classifyTravelType(trip, config));
        assertEquals(TripType.RUNNING, classification.classifyTravelType(
                new TripGpsStatistics(avg, max, 0.1, 0), Duration.ofSeconds(2230), 5002, config));
    }

    private TripClassificationDetailsDTO details(TimelineTripEntity trip) {
        var user = new UserEntity();
        var userId = UUID.randomUUID();
        user.setId(userId);
        trip.setUser(user);
        var service = new TripClassificationDetailsService();
        service.tripRepository = mock(TimelineTripRepository.class);
        service.configProvider = mock(TimelineConfigurationProvider.class);
        service.travelClassification = classification;
        when(service.tripRepository.findByIdOptional(14625L)).thenReturn(Optional.of(trip));
        when(service.configProvider.getConfigurationForUser(userId)).thenReturn(config);

        return service.getTripClassificationDetails(14625L, userId).orElseThrow();
    }

    private TripType classify(double avgKmh, double maxKmh, double calculatedKmh) {
        return classification.classifyTravelType(new TripGpsStatistics(avgKmh / 3.6, maxKmh / 3.6, 0.1, 0),
                Duration.ofHours(1), Math.round(calculatedKmh * 1000), config);
    }

    private TimelineTripEntity reportedTrip() {
        var trip = new TimelineTripEntity();
        trip.setId(14625L);
        trip.setDistanceMeters(5002);
        trip.setTripDuration(2230);
        trip.setAvgGpsSpeed(1.2788565022421496);
        trip.setMaxGpsSpeed(1.5);
        trip.setSpeedVariance(0.11779286281646476);
        trip.setLowAccuracyPointsCount(0);
        trip.setMovementType("RUNNING");
        return trip;
    }
}

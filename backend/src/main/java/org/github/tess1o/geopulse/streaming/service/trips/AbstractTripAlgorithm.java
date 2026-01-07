package org.github.tess1o.geopulse.streaming.service.trips;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public abstract class AbstractTripAlgorithm implements StreamTripAlgorithm {

    @Inject
    TravelClassification travelClassification;
    @Inject
    GpsStatisticsCalculator gpsStatisticsCalculator;

    /**
     * Validate trip against minimum distance and duration requirements.
     */
    protected boolean isValidTrip(Trip trip, TimelineConfig config) {
        if (trip == null) {
            return false;
        }

        Integer minDistance = config.getStaypointRadiusMeters();
        Integer minDuration = config.getStaypointMinDurationMinutes();

        boolean validDistance = minDistance == null || trip.getDistanceMeters() >= minDistance;
        boolean validDuration = minDuration == null || trip.getDuration().toMinutes() >= minDuration;

        return validDistance && validDuration;
    }

    /**
     * Merge multiple trip segments into a single trip.
     * Used by single algorithm to combine fragmented trips.
     */
    protected Trip mergeTripSegments(UUID userId, List<Trip> trips, TimelineConfig config) {
        if (trips.isEmpty()) {
            return null;
        }

        if (trips.size() == 1) {
            return trips.getFirst();
        }

        // Combine all trip segments
        Trip firstTrip = trips.getFirst();
        Trip lastTrip = trips.getLast();

        Instant startTime = firstTrip.getStartTime();
        Instant endTime = lastTrip.getEndTime();


        // Calculate total distance and duration
        double totalDistance = trips.stream()
                .mapToDouble(Trip::getDistanceMeters)
                .sum();

        Duration totalDuration = Duration.between(
                firstTrip.getStartTime(),
                lastTrip.getStartTime().plus(lastTrip.getDuration())
        );

        // Check if any of the trips being merged is an inferred trip (empty GPS statistics)
        boolean hasInferredTrip = trips.stream()
                .anyMatch(t -> t.getStatistics() == null || !t.getStatistics().hasValidData());

        // Classify the overall trip mode
        TripGpsStatistics tripGpsStatistics;
        TripType overallTripType;

        if (hasInferredTrip) {
            // If merging includes an inferred trip, use empty statistics to trigger distance-based classification
            // This prevents polluting flight classification with low-speed GPS data from adjacent car trips
            log.debug("Merged trips include inferred trip - using distance-based classification");
            tripGpsStatistics = TripGpsStatistics.empty();
            overallTripType = classifyMergedTrip(totalDistance, totalDuration, tripGpsStatistics, config);
        } else {
            // Normal case: recalculate GPS statistics from actual GPS points
            tripGpsStatistics = gpsStatisticsCalculator.calculateStatistics(userId, startTime, endTime);
            overallTripType = classifyMergedTrip(totalDistance, totalDuration, tripGpsStatistics, config);
        }

        Trip mergedTrip = Trip.builder()
                .startTime(firstTrip.getStartTime())
                .duration(totalDuration)
                .distanceMeters(totalDistance)
                .startPoint(firstTrip.getStartLocation())
                .endPoint(lastTrip.getEndLocation())
                .statistics(tripGpsStatistics)
                .tripType(overallTripType)
                .build();

        log.debug("Merged {} trips into single trip: {}m, {}min, {}",
                trips.size(), totalDistance, totalDuration.toMinutes(), overallTripType);

        return mergedTrip;
    }

    /**
     * Classify the overall trip type for merged trips.
     */
    protected TripType classifyMergedTrip(double totalDistance, Duration tripDuration, TripGpsStatistics tripGpsStatistics, TimelineConfig config) {
        return travelClassification.classifyTravelType(tripGpsStatistics, tripDuration, Double.valueOf(totalDistance).longValue(), config);
    }
}

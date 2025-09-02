package org.github.tess1o.geopulse.streaming.service.trips;

import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractTripAlgorithm implements StreamTripAlgorithm {

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
    protected Trip mergeTripSegments(List<Trip> trips, TimelineConfig config) {
        if (trips.isEmpty()) {
            return null;
        }

        if (trips.size() == 1) {
            return trips.getFirst();
        }

        // Combine all trip segments
        Trip firstTrip = trips.getFirst();
        Trip lastTrip = trips.getLast();

        // Merge all GPS points from all trips
        List<GPSPoint> combinedPath = trips.stream()
                .flatMap(trip -> trip.getPath().stream())
                .collect(Collectors.toList());

        // Calculate total distance and duration
        double totalDistance = trips.stream()
                .mapToDouble(Trip::getDistanceMeters)
                .sum();

        Duration totalDuration = Duration.between(
                firstTrip.getStartTime(),
                lastTrip.getStartTime().plus(lastTrip.getDuration())
        );

        // Classify the overall trip mode
        TripType overallTripType = classifyMergedTrip(trips, combinedPath, config);

        Trip mergedTrip = Trip.builder()
                .startTime(firstTrip.getStartTime())
                .duration(totalDuration)
                .path(combinedPath)
                .distanceMeters(totalDistance)
                .tripType(overallTripType)
                .build();

        log.debug("Merged {} trips into single trip: {}m, {}min, {}",
                trips.size(), totalDistance, totalDuration.toMinutes(), overallTripType);

        return mergedTrip;
    }

    /**
     * Classify the overall trip type for merged trips.
     */
    protected TripType classifyMergedTrip(List<Trip> trips, List<GPSPoint> combinedPath, TimelineConfig config) {
        // Use the dominant trip type by distance
        double carDistance = trips.stream().filter(t -> t.getTripType() == TripType.CAR).mapToDouble(Trip::getDistanceMeters).sum();
        double walkDistance = trips.stream().filter(t -> t.getTripType() == TripType.WALK).mapToDouble(Trip::getDistanceMeters).sum();
        double otherDistance = trips.stream().filter(t -> t.getTripType() != TripType.CAR && t.getTripType() != TripType.WALK).mapToDouble(Trip::getDistanceMeters).sum();

        if (carDistance > walkDistance && carDistance > otherDistance) {
            return TripType.CAR;
        } else if (walkDistance > otherDistance) {
            return TripType.WALK;
        } else {
            // Fall back to first trip's type or reclassify
            return trips.getFirst().getTripType();
        }
    }
}

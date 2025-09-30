package org.github.tess1o.geopulse.streaming.service.trips;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
    public class StreamingMultipleTripAlgorithm extends AbstractTripAlgorithm {

    /**
     * Minimum contribution ratio for a travel mode to be considered significant.
     * Each mode must account for at least this percentage of total distance to justify keeping trips separate.
     * Example: 0.20 means walking must be at least 20% of total distance to be considered legitimate.
     */
    private static final double MIN_MODE_CONTRIBUTION_RATIO = 0.20;

    /**
     * Apply multi trip algorithm: allow multiple trips between stays if confident about mode changes.
     * This is for legitimate cases like drive→walk or walk→drive transitions.
     */
    @Override
    public List<TimelineEvent> apply(List<TimelineEvent> events, TimelineConfig config) {
        List<TimelineEvent> processedEvents = new ArrayList<>();

        Stay currentStay = null;
        List<Trip> tripSegment = new ArrayList<>();

        for (TimelineEvent event : events) {
            if (event instanceof Stay) {
                Stay stay = (Stay) event;

                // Process accumulated trips for multi-modal analysis
                if (!tripSegment.isEmpty() && currentStay != null) {
                    List<Trip> processedTrips = analyzeMultiModalSegment(tripSegment, config);

                    // Ensure at least one trip between stays for continuity
                    List<Trip> validTrips = processedTrips.stream()
                            .filter(trip -> isValidTrip(trip, config))
                            .collect(Collectors.toList());

                    if (validTrips.isEmpty() && !processedTrips.isEmpty()) {
                        // No valid trips but we have movement between stays - include the best one
                        Trip bestTrip = processedTrips.stream()
                                .max((t1, t2) -> Double.compare(t1.getDistanceMeters(), t2.getDistanceMeters()))
                                .orElse(processedTrips.get(0));
                        log.warn("Including short trip between stays for continuity: {}m, {}min",
                                bestTrip.getDistanceMeters(), bestTrip.getDuration().toMinutes());
                        processedEvents.add(bestTrip);
                    } else {
                        validTrips.forEach(processedEvents::add);
                    }
                }

                tripSegment.clear();
                currentStay = stay;
                processedEvents.add(stay);

            } else if (event instanceof Trip) {
                Trip trip = (Trip) event;
                tripSegment.add(trip);

            } else {
                processedEvents.add(event);
            }
        }

        // Handle remaining trips
        if (!tripSegment.isEmpty()) {
            List<Trip> processedTrips = analyzeMultiModalSegment(tripSegment, config);
            List<Trip> validTrips = processedTrips.stream()
                    .filter(trip -> isValidTrip(trip, config))
                    .collect(Collectors.toList());

            if (validTrips.isEmpty() && !processedTrips.isEmpty() && currentStay != null) {
                // Include best trip for continuity if we had a preceding stay
                Trip bestTrip = processedTrips.stream()
                        .max((t1, t2) -> Double.compare(t1.getDistanceMeters(), t2.getDistanceMeters()))
                        .orElse(processedTrips.get(0));
                log.warn("Including final short trip for continuity: {}m, {}min",
                        bestTrip.getDistanceMeters(), bestTrip.getDuration().toMinutes());
                processedEvents.add(bestTrip);
            } else {
                validTrips.forEach(processedEvents::add);
            }
        }

        log.info("Multi algorithm: processed {} events into {}", events.size(), processedEvents.size());
        return processedEvents;
    }


    /**
     * Analyze trip segment for multi-modal patterns.
     * Only split if confident about legitimate mode changes.
     */
    private List<Trip> analyzeMultiModalSegment(List<Trip> trips, TimelineConfig config) {
        if (trips.isEmpty()) {
            return new ArrayList<>();
        }

        if (trips.size() == 1) {
            return trips;
        }

        // Step 1: Merge consecutive same-type trips first to avoid fragmentation
        List<Trip> condensedTrips = mergeConsecutiveSameTypeTrips(trips, config);

        if (condensedTrips.size() == 1) {
            return condensedTrips;
        }

        // Step 2: Check if we have legitimate mode diversity (not just traffic fragmentation)
        boolean hasSignificantModeChanges = hasLegitimateModChanges(condensedTrips, config);

        if (hasSignificantModeChanges) {
            log.debug("Multi algorithm: keeping {} separate trips due to significant mode changes", condensedTrips.size());
            return condensedTrips;
        } else {
            // Merge remaining trips
            log.debug("Multi algorithm: merging {} similar trips", condensedTrips.size());
            Trip merged = mergeTripSegments(condensedTrips, config);
            return merged != null ? List.of(merged) : new ArrayList<>();
        }
    }

    /**
     * Merge consecutive trips of the same type to eliminate fragmentation.
     * For example: [WALK, WALK, WALK, CAR] -> [WALK, CAR]
     */
    private List<Trip> mergeConsecutiveSameTypeTrips(List<Trip> trips, TimelineConfig config) {
        if (trips.isEmpty()) {
            return new ArrayList<>();
        }

        List<Trip> result = new ArrayList<>();
        List<Trip> currentGroup = new ArrayList<>();
        TripType currentType = null;

        for (Trip trip : trips) {
            if (currentType == null || currentType == trip.getTripType()) {
                // Same type or first trip - add to current group
                currentGroup.add(trip);
                currentType = trip.getTripType();
            } else {
                // Different type - merge current group and start new one
                Trip merged = mergeTripSegments(currentGroup, config);
                if (merged != null) {
                    result.add(merged);
                }
                currentGroup.clear();
                currentGroup.add(trip);
                currentType = trip.getTripType();
            }
        }

        // Merge final group
        if (!currentGroup.isEmpty()) {
            Trip merged = mergeTripSegments(currentGroup, config);
            if (merged != null) {
                result.add(merged);
            }
        }

        log.debug("Merged {} trips into {} condensed trips", trips.size(), result.size());
        return result;
    }

    /**
     * Determine if trip segments represent legitimate mode changes vs traffic fragmentation.
     * Uses relative contribution analysis: both modes must contribute meaningfully to total distance.
     */
    private boolean hasLegitimateModChanges(List<Trip> trips, TimelineConfig config) {
        long walkingTrips = trips.stream().filter(t -> t.getTripType() == TripType.WALK).count();
        long drivingTrips = trips.stream().filter(t -> t.getTripType() == TripType.CAR).count();

        // If all same mode, definitely merge
        if (walkingTrips == 0 || drivingTrips == 0) {
            return false;
        }

        // Calculate total distance for each mode
        double totalWalkDistance = trips.stream()
                .filter(t -> t.getTripType() == TripType.WALK)
                .mapToDouble(Trip::getDistanceMeters)
                .sum();

        double totalDriveDistance = trips.stream()
                .filter(t -> t.getTripType() == TripType.CAR)
                .mapToDouble(Trip::getDistanceMeters)
                .sum();

        // Both modes should contribute meaningfully (at least MIN_MODE_CONTRIBUTION_RATIO of total distance each)
        double totalDistance = totalWalkDistance + totalDriveDistance;
        double walkRatio = totalDistance > 0 ? totalWalkDistance / totalDistance : 0;
        double driveRatio = totalDistance > 0 ? totalDriveDistance / totalDistance : 0;

        boolean legitimate = walkRatio >= MIN_MODE_CONTRIBUTION_RATIO && driveRatio >= MIN_MODE_CONTRIBUTION_RATIO;

        log.debug("Mode change analysis: walking={} ({}m, {:.1f}%), driving={} ({}m, {:.1f}%), legitimate={}",
                walkingTrips, (long)totalWalkDistance, walkRatio * 100,
                drivingTrips, (long)totalDriveDistance, driveRatio * 100, legitimate);

        return legitimate;
    }
}

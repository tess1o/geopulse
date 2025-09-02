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

        // Check if we have legitimate mode diversity (not just traffic fragmentation)
        boolean hasSignificantModeChanges = hasLegitimateModChanges(trips, config);

        if (hasSignificantModeChanges) {
            log.debug("Multi algorithm: keeping {} separate trips due to significant mode changes", trips.size());
            return trips;
        } else {
            // Merge similar/fragmented trips
            log.debug("Multi algorithm: merging {} similar trips", trips.size());
            Trip merged = mergeTripSegments(trips, config);
            return merged != null ? List.of(merged) : new ArrayList<>();
        }
    }

    /**
     * Determine if trip segments represent legitimate mode changes vs traffic fragmentation.
     */
    private boolean hasLegitimateModChanges(List<Trip> trips, TimelineConfig config) {
        // Check 1: Do we have meaningful distance/duration differences?
        long walkingTrips = trips.stream().filter(t -> t.getTripType() == TripType.WALK).count();
        long drivingTrips = trips.stream().filter(t -> t.getTripType() == TripType.CAR).count();

        // If all same mode, definitely merge
        if (walkingTrips == 0 || drivingTrips == 0) {
            return false;
        }

        // Check 2: Are the walking segments significant? (not just short connections)
        boolean hasSignificantWalking = trips.stream()
                .filter(t -> t.getTripType() == TripType.WALK)
                .anyMatch(t -> t.getDistanceMeters() > 100 && t.getDuration().toMinutes() >= 2);

        // Check 3: Are the driving segments significant?
        boolean hasSignificantDriving = trips.stream()
                .filter(t -> t.getTripType() == TripType.CAR)
                .anyMatch(t -> t.getDistanceMeters() > 200 && t.getDuration().toMinutes() >= 3);

        boolean legitimate = hasSignificantWalking && hasSignificantDriving;

        log.debug("Mode change analysis: walking={}, driving={}, significantWalk={}, significantDrive={}, legitimate={}",
                walkingTrips, drivingTrips, hasSignificantWalking, hasSignificantDriving, legitimate);

        return legitimate;
    }
}

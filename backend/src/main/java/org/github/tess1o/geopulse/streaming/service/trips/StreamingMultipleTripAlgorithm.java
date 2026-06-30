package org.github.tess1o.geopulse.streaming.service.trips;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class StreamingMultipleTripAlgorithm extends AbstractTripAlgorithm {

    /**
     * Minimum contribution ratio for a travel mode to be considered significant.
     * Each mode must account for at least this percentage of total distance OR time to justify keeping trips separate.
     * Example: 0.15 means walking must be at least 15% of total distance OR 15% of total time to be considered legitimate.
     */
    private static final double MIN_MODE_CONTRIBUTION_RATIO = 0.15;

    /**
     * Apply multi trip algorithm: allow multiple trips between stays if confident about mode changes.
     * This is for legitimate cases like drive→walk or walk→drive transitions.
     */
    @Override
    public List<TimelineEvent> apply(UUID userId, List<TimelineEvent> events, TimelineConfig config) {
        List<TimelineEvent> processedEvents = new ArrayList<>();

        Stay currentStay = null;
        List<Trip> tripSegment = new ArrayList<>();

        for (TimelineEvent event : events) {
            if (event instanceof Stay) {
                Stay stay = (Stay) event;

                // Process accumulated trips for multi-modal analysis
                if (!tripSegment.isEmpty()) {
                    List<Trip> processedTrips = analyzeMultiModalSegment(userId, tripSegment, config);

                    // Ensure at least one trip between stays for continuity
                    List<Trip> validTrips = processedTrips.stream()
                            .filter(trip -> isValidTrip(trip, config))
                            .collect(Collectors.toList());

                    if (validTrips.isEmpty() && !processedTrips.isEmpty() && currentStay != null) {
                        // No valid trips but we have movement between stays - include the best one
                        Trip bestTrip = processedTrips.stream()
                                .max((t1, t2) -> Double.compare(t1.getDistanceMeters(), t2.getDistanceMeters()))
                                .orElse(processedTrips.get(0));
                        log.warn("Including short trip between stays for continuity: {}m, {}min",
                                bestTrip.getDistanceMeters(), bestTrip.getDuration().toMinutes());
                        processedEvents.add(bestTrip);
                    } else {
                        validTrips.forEach(processedEvents::add);
                        if (validTrips.isEmpty() && currentStay == null) {
                            log.debug("Dropping leading trips below normal thresholds before first stay: count={}",
                                    processedTrips.size());
                        }
                    }
                } else if (currentStay != null && tripSegment.isEmpty()) {
                    // No detected trip between consecutive stays:
                    // - same location -> keep as-is for optional downstream merge pass
                    // - different location -> synthesize continuity trip here
                    if (isSameLocation(currentStay, stay)) {
                        log.debug("Consecutive stays at same location '{}' with no trips detected - " +
                                "left as-is in post-processing; downstream merge pass may consolidate when merge is enabled",
                                stay.getLocationName());
                    } else {
                        Trip continuityTrip = createContinuityTripBetweenStays(currentStay, stay, config);
                        if (continuityTrip != null) {
                            log.warn("Including continuity trip between consecutive stays: '{}' -> '{}' ({}m, {}min)",
                                    currentStay.getLocationName(), stay.getLocationName(),
                                    continuityTrip.getDistanceMeters(), continuityTrip.getDuration().toMinutes());
                            processedEvents.add(continuityTrip);
                        } else {
                            log.warn("Could not create continuity trip between consecutive stays: '{}' -> '{}'",
                                    currentStay.getLocationName(), stay.getLocationName());
                        }
                    }
                }

                tripSegment.clear();
                currentStay = stay;
                processedEvents.add(stay);

            } else if (event instanceof Trip) {
                tripSegment.add((Trip) event);

            } else {
                if (!tripSegment.isEmpty()) {
                    List<Trip> processedTrips = analyzeMultiModalSegment(userId, tripSegment, config);
                    List<Trip> validTrips = processedTrips.stream()
                            .filter(trip -> isValidTrip(trip, config))
                            .collect(Collectors.toList());
                    if (!validTrips.isEmpty()) {
                        processedEvents.addAll(validTrips);
                    } else if (event instanceof DataGap && !processedTrips.isEmpty()) {
                        // Preserve context before a gap even when trip is below normal thresholds.
                        Trip bestTrip = processedTrips.stream()
                                .max((t1, t2) -> Double.compare(t1.getDistanceMeters(), t2.getDistanceMeters()))
                                .orElse(processedTrips.get(0));
                        log.warn("Including short trip before data gap for continuity: {}m, {}min",
                                bestTrip.getDistanceMeters(), bestTrip.getDuration().toMinutes());
                        processedEvents.add(bestTrip);
                    }
                    tripSegment.clear();
                }
                processedEvents.add(event);
            }
        }

        // Handle remaining trips
        if (!tripSegment.isEmpty()) {
            List<Trip> processedTrips = analyzeMultiModalSegment(userId, tripSegment, config);
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
    private List<Trip> analyzeMultiModalSegment(UUID userId, List<Trip> trips, TimelineConfig config) {
        if (trips.isEmpty()) {
            return new ArrayList<>();
        }

        if (trips.size() == 1) {
            return trips;
        }

        // Step 1: Merge consecutive same-type trips first to avoid fragmentation
        List<Trip> condensedTrips = mergeConsecutiveSameTypeTrips(userId, trips, config);

        if (condensedTrips.size() == 1) {
            return condensedTrips;
        }

        // Step 2: Check if we have legitimate mode diversity (not just traffic fragmentation)
        boolean hasSignificantModeChanges = hasLegitimateModChanges(condensedTrips);

        if (hasSignificantModeChanges) {
            log.debug("Multi algorithm: keeping {} separate trips due to significant mode changes", condensedTrips.size());
            return condensedTrips;
        } else {
            // Merge remaining trips
            log.debug("Multi algorithm: merging {} similar trips", condensedTrips.size());
            Trip merged = mergeTripSegments(userId, condensedTrips, config);
            return merged != null ? List.of(merged) : new ArrayList<>();
        }
    }

    /**
     * Merge consecutive trips of the same type to eliminate fragmentation.
     * For example: [WALK, WALK, WALK, CAR] -> [WALK, CAR]
     */
    private List<Trip> mergeConsecutiveSameTypeTrips(UUID userId, List<Trip> trips, TimelineConfig config) {
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
                Trip merged = mergeTripSegments(userId, currentGroup, config);
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
            Trip merged = mergeTripSegments(userId, currentGroup, config);
            if (merged != null) {
                result.add(merged);
            }
        }

        log.debug("Merged {} trips into {} condensed trips", trips.size(), result.size());
        return result;
    }

    /**
     * Determine if trip segments represent legitimate mode changes vs traffic fragmentation.
     * Uses relative contribution analysis: at least two known modes must contribute meaningfully
     * to total distance or time.
     */
    private boolean hasLegitimateModChanges(List<Trip> trips) {
        Map<TripType, ModeContribution> contributionsByType = summarizeModeContributions(trips);
        if (contributionsByType.size() < 2) {
            return false;
        }

        double totalDistance = contributionsByType.values().stream()
                .mapToDouble(ModeContribution::distanceMeters)
                .sum();
        double totalTime = contributionsByType.values().stream()
                .mapToDouble(ModeContribution::durationSeconds)
                .sum();

        if (totalDistance <= 0.0 && totalTime <= 0.0) {
            return false;
        }

        long significantModeCount = contributionsByType.values().stream()
                .filter(contribution -> isSignificantModeContribution(contribution, totalDistance, totalTime))
                .count();

        boolean legitimate = significantModeCount >= 2;

        log.debug("Mode change analysis: contributions={}, significantModes={}, legitimate={}",
                describeModeContributions(contributionsByType, totalDistance, totalTime),
                significantModeCount,
                legitimate);

        return legitimate;
    }

    private Map<TripType, ModeContribution> summarizeModeContributions(List<Trip> trips) {
        Map<TripType, ModeContribution> contributions = new EnumMap<>(TripType.class);

        for (Trip trip : trips) {
            TripType tripType = trip.getTripType();
            if (tripType == null || tripType == TripType.UNKNOWN) {
                continue;
            }

            ModeContribution current = contributions.getOrDefault(tripType, new ModeContribution(0, 0.0, 0.0));
            double distanceMeters = Math.max(0.0, trip.getDistanceMeters());
            double durationSeconds = trip.getDuration() != null ? Math.max(0.0, trip.getDuration().getSeconds()) : 0.0;
            contributions.put(
                    tripType,
                    new ModeContribution(
                            current.tripCount() + 1,
                            current.distanceMeters() + distanceMeters,
                            current.durationSeconds() + durationSeconds
                    )
            );
        }

        return contributions;
    }

    private boolean isSignificantModeContribution(ModeContribution contribution, double totalDistance, double totalTime) {
        double distanceRatio = totalDistance > 0.0 ? contribution.distanceMeters() / totalDistance : 0.0;
        double timeRatio = totalTime > 0.0 ? contribution.durationSeconds() / totalTime : 0.0;
        return distanceRatio >= MIN_MODE_CONTRIBUTION_RATIO
                || timeRatio >= MIN_MODE_CONTRIBUTION_RATIO;
    }

    private String describeModeContributions(Map<TripType, ModeContribution> contributionsByType,
                                             double totalDistance,
                                             double totalTime) {
        return contributionsByType.entrySet().stream()
                .map(entry -> {
                    ModeContribution contribution = entry.getValue();
                    double distanceRatio = totalDistance > 0.0 ? contribution.distanceMeters() / totalDistance : 0.0;
                    double timeRatio = totalTime > 0.0 ? contribution.durationSeconds() / totalTime : 0.0;
                    return String.format(
                            "%s=%d trips, %.0fm, %.1f%%, %.0fs, %.1f%%",
                            entry.getKey(),
                            contribution.tripCount(),
                            contribution.distanceMeters(),
                            distanceRatio * 100.0,
                            contribution.durationSeconds(),
                            timeRatio * 100.0
                    );
                })
                .collect(Collectors.joining("; "));
    }

    private record ModeContribution(int tripCount, double distanceMeters, double durationSeconds) {
    }
}

package org.github.tess1o.geopulse.streaming.service.trips;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class StreamingSingleTripAlgorithm extends AbstractTripAlgorithm {
    public List<TimelineEvent> apply(UUID userd, List<TimelineEvent> events, TimelineConfig config) {
        List<TimelineEvent> processedEvents = new ArrayList<>();

        Stay currentStay = null;
        List<Trip> tripsToMerge = new ArrayList<>();

        for (TimelineEvent event : events) {
            if (event instanceof Stay) {
                Stay stay = (Stay) event;

                // If we have accumulated trips, merge them into one
                if (!tripsToMerge.isEmpty() && currentStay != null) {
                    Trip mergedTrip = mergeTripSegments(userd, tripsToMerge, config);
                    if (mergedTrip != null) {
                        if (isValidTrip(mergedTrip, config)) {
                            processedEvents.add(mergedTrip);
                        } else {
                            // CRITICAL: Always include trip between stays to maintain timeline continuity
                            // Even if it's very short, we need SOME movement between different locations
                            log.warn("Including short trip between stays for continuity: {}m, {}min (below normal thresholds)",
                                    mergedTrip.getDistanceMeters(), mergedTrip.getDuration().toMinutes());
                            processedEvents.add(mergedTrip);
                        }
                    }
                } else if (currentStay != null && tripsToMerge.isEmpty()) {
                    // No trips between consecutive stays - this will be handled by the merger
                    // The MovementTimelineMerger will force-merge consecutive same-location stays for timeline integrity
                    if (currentStay.getLocationName() != null &&
                        currentStay.getLocationName().equals(stay.getLocationName())) {
                        log.debug("Consecutive stays at same location '{}' with no trips detected - " +
                                "will be merged by MovementTimelineMerger for timeline integrity",
                                stay.getLocationName());
                    } else {
                        // Different locations with no trips - unusual but could happen with data gaps
                        log.warn("No trips detected between different locations: '{}' and '{}' - " +
                                "check for data gaps or GPS issues",
                                currentStay.getLocationName(), stay.getLocationName());
                    }
                }

                // Reset for next segment
                tripsToMerge.clear();
                currentStay = stay;
                processedEvents.add(stay);

            } else if (event instanceof Trip) {
                tripsToMerge.add((Trip) event);

            } else {
                // Data gaps and other events finalize the current trip segment
                if (!tripsToMerge.isEmpty()) {
                    Trip mergedTrip = mergeTripSegments(userd, tripsToMerge, config);
                    if (mergedTrip != null && isValidTrip(mergedTrip, config)) {
                        processedEvents.add(mergedTrip);
                    }
                    tripsToMerge.clear();
                }
                processedEvents.add(event);
            }
        }

        // Handle any remaining trips at the end
        if (!tripsToMerge.isEmpty()) {
            Trip mergedTrip = mergeTripSegments(userd, tripsToMerge, config);
            if (mergedTrip != null) {
                if (isValidTrip(mergedTrip, config)) {
                    processedEvents.add(mergedTrip);
                } else if (currentStay != null) {
                    // Include for continuity if we had a preceding stay
                    log.warn("Including final short trip for continuity: {}m, {}min",
                            mergedTrip.getDistanceMeters(), mergedTrip.getDuration().toMinutes());
                    processedEvents.add(mergedTrip);
                }
            }
        }

        log.debug("Single algorithm: processed {} events into {}", events.size(), processedEvents.size());
        return processedEvents;
    }
}

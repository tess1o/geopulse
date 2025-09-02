package org.github.tess1o.geopulse.streaming.service.trips;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@Slf4j
public class StreamingSingleTripAlgorithm extends AbstractTripAlgorithm {
    public List<TimelineEvent> apply(List<TimelineEvent> events, TimelineConfig config) {
        List<TimelineEvent> processedEvents = new ArrayList<>();

        Stay currentStay = null;
        List<Trip> tripsToMerge = new ArrayList<>();

        for (TimelineEvent event : events) {
            if (event instanceof Stay) {
                Stay stay = (Stay) event;

                // If we have accumulated trips, merge them into one
                if (!tripsToMerge.isEmpty() && currentStay != null) {
                    Trip mergedTrip = mergeTripSegments(tripsToMerge, config);
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
                    } else {
                        log.error("Failed to create trip between stays - this should not happen");
                    }
                } else if (currentStay != null && tripsToMerge.isEmpty()) {
                    log.error("TIMELINE INTEGRITY ISSUE: No trips between consecutive stays at {} and {}",
                            currentStay.getLocationName(), stay.getLocationName());
                }

                // Reset for next segment
                tripsToMerge.clear();
                currentStay = stay;
                processedEvents.add(stay);

            } else if (event instanceof Trip) {
                Trip trip = (Trip) event;
                tripsToMerge.add(trip);

            } else {
                // Data gaps and other events pass through unchanged
                processedEvents.add(event);
            }
        }

        // Handle any remaining trips at the end
        if (!tripsToMerge.isEmpty()) {
            Trip mergedTrip = mergeTripSegments(tripsToMerge, config);
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

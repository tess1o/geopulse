package org.github.tess1o.geopulse.streaming.merge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@Slf4j
public class MovementTimelineMergerImpl implements MovementTimelineMerger {

    private final TimelineMergeAnalysisService analysisService;
    private final TimelineMergeExecutionService executionService;

    @Inject
    public MovementTimelineMergerImpl(TimelineMergeAnalysisService analysisService,
                                      TimelineMergeExecutionService executionService) {
        this.analysisService = analysisService;
        this.executionService = executionService;
    }

    /**
     * Merges consecutive stays at the same location that satisfy the merge criteria (see
     * {@link TimelineMergeAnalysisService#canMergeStays}). The timeline is walked in order, so what lies between two
     * stays is exactly what was recorded between them, also when a trip of 0 seconds starts at the same instant as
     * the next stay.
     *
     * @param timelineConfig configuration with merge thresholds
     * @param events         the timeline in order
     * @return the timeline in order, with merged stays in place of the stays and trips they absorbed
     */
    @Override
    public List<TimelineEvent> mergeSameNamedLocations(TimelineConfig timelineConfig, List<TimelineEvent> events) {
        List<TimelineEvent> merged = new ArrayList<>(events.size());
        // Stays merged so far, the trips between them, and what followed the last of them
        List<Stay> group = new ArrayList<>();
        List<Trip> absorbedTrips = new ArrayList<>();
        List<TimelineEvent> sinceLastStay = new ArrayList<>();

        for (TimelineEvent event : events) {
            if (!(event instanceof Stay stay)) {
                sinceLastStay.add(event);
                continue;
            }

            if (!group.isEmpty() && analysisService.canMergeStays(timelineConfig, group.getFirst(), sinceLastStay, stay)) {
                sinceLastStay.stream().map(Trip.class::cast).forEach(absorbedTrips::add);
            } else {
                addGroup(merged, group, absorbedTrips);
                merged.addAll(sinceLastStay);
                group.clear();
                absorbedTrips.clear();
            }
            sinceLastStay.clear();
            group.add(stay);
        }
        addGroup(merged, group, absorbedTrips);
        merged.addAll(sinceLastStay);

        log.info("Merge completed: {} events merged to {}", events.size(), merged.size());
        return merged;
    }

    private void addGroup(List<TimelineEvent> merged, List<Stay> group, List<Trip> absorbedTrips) {
        if (group.size() == 1) {
            merged.add(group.getFirst());
        } else if (group.size() > 1) {
            merged.add(executionService.createMergedStay(group, absorbedTrips));
            log.debug("Merged {} stays at '{}' into one, absorbing {} trips",
                    group.size(), group.getFirst().getLocationName(), absorbedTrips.size());
        }
    }
}

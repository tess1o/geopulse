package org.github.tess1o.geopulse.streaming.merge;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineExplanation;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReason;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReasons;

import java.time.Duration;
import java.util.List;

/**
 * Service responsible for executing timeline merges.
 * Creates merged stay objects from multiple stays and calculates combined durations.
 */
@ApplicationScoped
@Slf4j
public class TimelineMergeExecutionService {

    /**
     * Create a merged stay from a group of stays and the trips between them.
     * The merged stay uses the first stay's properties but combines durations from all stays
     * and the trips it absorbs.
     *
     * @param stays         stays to merge, in timeline order
     * @param absorbedTrips trips between the merged stays
     * @return merged stay with combined duration
     */
    public Stay createMergedStay(List<Stay> stays, List<Trip> absorbedTrips) {
        if (stays == null || stays.isEmpty() || absorbedTrips == null) {
            throw new IllegalArgumentException("Invalid parameters for stay merge");
        }

        log.debug("Creating merged stay from {} stays", stays.size());

        Stay firstStay = stays.getFirst();

        Duration stayDuration = stays.stream().map(Stay::getDuration).reduce(Duration.ZERO, Duration::plus);
        Duration tripDuration = absorbedTrips.stream().map(Trip::getDuration).reduce(Duration.ZERO, Duration::plus);

        log.debug("Merged stay duration: stays={}s, trips={}s", stayDuration.getSeconds(), tripDuration.getSeconds());

        Stay mergedStay = Stay.builder()
                .startTime(firstStay.getStartTime())
                .locationName(firstStay.getLocationName())
                .duration(stayDuration.plus(tripDuration))
                .latitude(firstStay.getLatitude())
                .longitude(firstStay.getLongitude())
                .favoriteId(firstStay.getFavoriteId())
                .geocodingId(firstStay.getGeocodingId())
                .build();
        mergedStay.setExplanation(TimelineExplanation.joined(stays)
                .note(TimelineReason.of(TimelineReasons.MERGED, "stays", stays.size())));
        return mergedStay;
    }
}

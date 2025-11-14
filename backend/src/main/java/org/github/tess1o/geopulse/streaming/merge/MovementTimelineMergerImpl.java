package org.github.tess1o.geopulse.streaming.merge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.domain.RawTimeline;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;

import java.time.Instant;
import java.util.*;

@ApplicationScoped
@Slf4j
public class MovementTimelineMergerImpl implements MovementTimelineMerger {
    
    private final TimelineMergeAnalysisService analysisService;
    private final TimelineMergeExecutionService executionService;
    private final TripFilteringService tripFilteringService;

    @Inject
    public MovementTimelineMergerImpl(TimelineMergeAnalysisService analysisService,
                                    TimelineMergeExecutionService executionService,
                                    TripFilteringService tripFilteringService) {
        this.analysisService = analysisService;
        this.executionService = executionService;
        this.tripFilteringService = tripFilteringService;
    }

    /**
     * Merges stay locations that are duplicates based on location name and satisfy merge criteria:
     * - Trip between duplicates is less than X meters OR less than Y minutes
     *
     * @param timelineConfig configuration with merge thresholds
     * @param timeline The movement timeline to process
     * @return A new MovementTimelineDTO with merged stays and updated trips
     */
    @Override
    public RawTimeline mergeSameNamedLocations(TimelineConfig timelineConfig, RawTimeline timeline) {
        log.info("Merging same named locations for user {}", timeline != null ? timeline.getUserId() : "null");
        
        if (timeline == null || timeline.getStays() == null || timeline.getTrips() == null) {
            log.debug("Timeline is null or has no stays/trips - returning original");
            return timeline;
        }

        List<Stay> stays = timeline.getStays();
        List<Trip> trips = timeline.getTrips();

        if (stays.size() <= 1) {
            log.debug("Only {} stays found - nothing to merge", stays.size());
            return timeline; // Nothing to merge
        }

        // Sort both lists by timestamp to ensure proper order
        stays.sort(Comparator.comparing(Stay::getStartTime));
        trips.sort(Comparator.comparing(Trip::getStartTime));

        log.debug("Processing {} stays and {} trips for merging", stays.size(), trips.size());

        // Process merges using orchestrated services
        MergeResult result = performMerges(timelineConfig, stays, trips, timeline.getDataGaps());

        log.info("Merge completed: {} stays merged to {}, {} trips remain", 
                stays.size(), result.mergedStays.size(), result.remainingTrips.size());

        RawTimeline mergedTimeline = new RawTimeline(
                timeline.getUserId(),
                result.mergedStays,
                result.remainingTrips,
                timeline.getDataGaps()
        );

        return mergedTimeline;
    }

    /**
     * Perform the merge operation using the injected services.
     * This method orchestrates the merge analysis, execution, and trip filtering.
     */
    private MergeResult performMerges(TimelineConfig timelineConfig,
                                      List<Stay> stays,
                                      List<Trip> trips,
                                      List<DataGap> dataGaps) {
        List<Stay> mergedStays = new ArrayList<>();
        Set<Integer> allRemovedTripIndices = new HashSet<>();

        int i = 0;
        while (i < stays.size()) {
            Stay currentStay = stays.get(i);
            List<Integer> mergeGroup = findMergeGroup(timelineConfig, stays, trips, dataGaps, i);

            if (mergeGroup.size() > 1) {
                // Create merged stay using execution service
                Stay mergedStay = executionService.createMergedStay(stays, mergeGroup, trips);
                mergedStays.add(mergedStay);

                // Mark trips for removal using filtering service
                Set<Integer> removedTripIndices = tripFilteringService.markTripsForRemovalInMergeGroup(
                    mergeGroup, stays, trips);
                allRemovedTripIndices.addAll(removedTripIndices);
                
                log.debug("Merged {} stays into one, marked {} trips for removal", 
                         mergeGroup.size(), removedTripIndices.size());
            } else {
                // No merge needed, add original stay
                mergedStays.add(currentStay);
            }

            // Move to next unprocessed stay (skip all merged stays)
            i = mergeGroup.getLast() + 1;
        }

        // Filter trips using filtering service
        List<Trip> remainingTrips = tripFilteringService.filterTrips(trips, allRemovedTripIndices);

        return new MergeResult(mergedStays, remainingTrips);
    }
    
    /**
     * Find a group of consecutive stays that can be merged together.
     * Uses two merge strategies:
     * 1. INTEGRITY MERGE (always applied): Same location + NO trips = force merge (timeline integrity)
     * 2. USER PREFERENCE MERGE (conditional): Same location + short trip = respect config settings
     */
    private List<Integer> findMergeGroup(TimelineConfig timelineConfig,
                                         List<Stay> stays,
                                         List<Trip> trips,
                                         List<DataGap> dataGaps,
                                         int startIndex) {
        List<Integer> mergeGroup = new ArrayList<>();
        mergeGroup.add(startIndex);

        if (startIndex >= stays.size()) {
            return mergeGroup;
        }

        Stay currentStay = stays.get(startIndex);

        // Look for consecutive stays with same location that can be merged
        int j = startIndex + 1;
        while (j < stays.size() && isSameLocation(currentStay, stays.get(j))) {
            Stay lastMergedStay = stays.get(mergeGroup.getLast());
            Stay candidateStay = stays.get(j);

            // INTEGRITY CHECK: If there are NO trips between these same-location stays,
            // this is a timeline integrity issue and must ALWAYS be merged
            boolean hasNoTripsBetween = !hasAnyTripBetweenStays(lastMergedStay, candidateStay, trips);
            boolean hasNoDataGapBetween = !analysisService.hasDataGapBetweenStays(lastMergedStay, candidateStay, dataGaps);

            if (hasNoTripsBetween && hasNoDataGapBetween) {
                // FORCE MERGE for timeline integrity (regardless of config)
                mergeGroup.add(j);
                log.debug("INTEGRITY MERGE: Consecutive stays at '{}' with NO trips/gaps between them - " +
                        "forcing merge to maintain timeline integrity (stay {} and {})",
                        currentStay.getLocationName(), mergeGroup.getLast(), j);
            } else if (timelineConfig.getIsMergeEnabled() &&
                       analysisService.canMergeStays(timelineConfig, lastMergedStay, candidateStay, trips, dataGaps)) {
                // USER PREFERENCE MERGE: Short trip between stays, respect config
                mergeGroup.add(j);
                log.debug("Added stay {} to merge group based on user config (location: '{}')",
                         j, currentStay.getLocationName());
            } else {
                log.debug("Cannot merge stay {} - has trips/gaps between or merge disabled", j);
                break; // Can't merge this stay
            }
            j++;
        }

        return mergeGroup;
    }

    /**
     * Check if there are ANY trips between two stays (used for integrity checks).
     */
    private boolean hasAnyTripBetweenStays(Stay firstStay, Stay secondStay, List<Trip> trips) {
        if (firstStay == null || secondStay == null || trips == null) {
            return false;
        }

        Instant firstStayTime = firstStay.getStartTime();
        Instant secondStayTime = secondStay.getStartTime();

        for (Trip trip : trips) {
            if (analysisService.isTripBetweenStays(trip, firstStayTime, secondStayTime)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if two stay locations represent the same location by comparing source references.
     * This approach is resistant to name changes in favorites.
     * 
     * @param stay1 first stay location
     * @param stay2 second stay location  
     * @return true if they represent the same location
     */
    private boolean isSameLocation(Stay stay1, Stay stay2) {
        // Same favorite location
        if (stay1.getFavoriteId() != null && stay2.getFavoriteId() != null) {
            return stay1.getFavoriteId().equals(stay2.getFavoriteId());
        }
        
        // Same geocoded location
        if (stay1.getGeocodingId() != null && stay2.getGeocodingId() != null) {
            return stay1.getGeocodingId().equals(stay2.getGeocodingId());
        }
        
        // Different source types or missing IDs - fall back to name comparison
        // This handles cases where old data exists or IDs are missing
        if (stay1.getLocationName() != null && stay2.getLocationName() != null) {
            return stay1.getLocationName().equals(stay2.getLocationName());
        }
        
        return false;
    }

    /**
     * Result record for merge operations.
     */
    private record MergeResult(List<Stay> mergedStays, List<Trip> remainingTrips) {
    }
}

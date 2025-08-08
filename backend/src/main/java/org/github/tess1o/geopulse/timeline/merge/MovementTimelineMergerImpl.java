package org.github.tess1o.geopulse.timeline.merge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.timeline.model.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineTripDTO;

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
    public MovementTimelineDTO mergeSameNamedLocations(TimelineConfig timelineConfig, MovementTimelineDTO timeline) {
        log.info("Merging same named locations for user {}", timeline != null ? timeline.getUserId() : "null");
        
        if (timeline == null || timeline.getStays() == null || timeline.getTrips() == null) {
            log.debug("Timeline is null or has no stays/trips - returning original");
            return timeline;
        }

        List<TimelineStayLocationDTO> stays = new ArrayList<>(timeline.getStays());
        List<TimelineTripDTO> trips = new ArrayList<>(timeline.getTrips());

        if (stays.size() <= 1) {
            log.debug("Only {} stays found - nothing to merge", stays.size());
            return timeline; // Nothing to merge
        }

        // Sort both lists by timestamp to ensure proper order
        stays.sort(Comparator.comparing(TimelineStayLocationDTO::getTimestamp));
        trips.sort(Comparator.comparing(TimelineTripDTO::getTimestamp));

        log.debug("Processing {} stays and {} trips for merging", stays.size(), trips.size());

        // Process merges using orchestrated services
        MergeResult result = performMerges(timelineConfig, stays, trips);

        log.info("Merge completed: {} stays merged to {}, {} trips remain", 
                stays.size(), result.mergedStays.size(), result.remainingTrips.size());

        MovementTimelineDTO mergedTimeline = new MovementTimelineDTO(
                timeline.getUserId(),
                result.mergedStays,
                result.remainingTrips
        );
        
        // Preserve metadata from original timeline
        mergedTimeline.setDataSource(timeline.getDataSource());
        mergedTimeline.setLastUpdated(timeline.getLastUpdated());
        
        return mergedTimeline;
    }

    /**
     * Perform the merge operation using the injected services.
     * This method orchestrates the merge analysis, execution, and trip filtering.
     */
    private MergeResult performMerges(TimelineConfig timelineConfig, 
                                    List<TimelineStayLocationDTO> stays, 
                                    List<TimelineTripDTO> trips) {
        List<TimelineStayLocationDTO> mergedStays = new ArrayList<>();
        Set<Integer> allRemovedTripIndices = new HashSet<>();

        int i = 0;
        while (i < stays.size()) {
            TimelineStayLocationDTO currentStay = stays.get(i);
            List<Integer> mergeGroup = findMergeGroup(timelineConfig, stays, trips, i);

            if (mergeGroup.size() > 1) {
                // Create merged stay using execution service
                TimelineStayLocationDTO mergedStay = executionService.createMergedStay(stays, mergeGroup, trips);
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
        List<TimelineTripDTO> remainingTrips = tripFilteringService.filterTrips(trips, allRemovedTripIndices);

        return new MergeResult(mergedStays, remainingTrips);
    }
    
    /**
     * Find a group of consecutive stays that can be merged together.
     * Uses the analysis service to determine merge eligibility.
     */
    private List<Integer> findMergeGroup(TimelineConfig timelineConfig,
                                       List<TimelineStayLocationDTO> stays,
                                       List<TimelineTripDTO> trips,
                                       int startIndex) {
        List<Integer> mergeGroup = new ArrayList<>();
        mergeGroup.add(startIndex);
        
        if (startIndex >= stays.size()) {
            return mergeGroup;
        }
        
        TimelineStayLocationDTO currentStay = stays.get(startIndex);

        // Look for consecutive stays with same location that can be merged
        int j = startIndex + 1;
        while (j < stays.size() && isSameLocation(currentStay, stays.get(j))) {
            // Check if the trip(s) between the last merged stay and current candidate allow merging
            TimelineStayLocationDTO lastMergedStay = stays.get(mergeGroup.getLast());
            TimelineStayLocationDTO candidateStay = stays.get(j);
            
            if (analysisService.canMergeStays(timelineConfig, lastMergedStay, candidateStay, trips)) {
                mergeGroup.add(j);
                log.debug("Added stay {} to merge group (location: '{}')", j, currentStay.getLocationName());
            } else {
                log.debug("Cannot merge stay {} due to analysis criteria", j);
                break; // Can't merge this stay
            }
            j++;
        }
        
        return mergeGroup;
    }

    /**
     * Check if two stay locations represent the same location by comparing source references.
     * This approach is resistant to name changes in favorites.
     * 
     * @param stay1 first stay location
     * @param stay2 second stay location  
     * @return true if they represent the same location
     */
    private boolean isSameLocation(TimelineStayLocationDTO stay1, TimelineStayLocationDTO stay2) {
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
    private record MergeResult(List<TimelineStayLocationDTO> mergedStays, List<TimelineTripDTO> remainingTrips) {
    }
}

package org.github.tess1o.geopulse.timeline.assembly;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.timeline.model.TimelineTripDTO;
import org.github.tess1o.geopulse.timeline.merge.MovementTimelineMerger;
import org.github.tess1o.geopulse.timeline.simplification.PathSimplificationService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service responsible for post-processing operations on assembled timelines.
 * Handles merging, filtering, and other timeline transformations based on configuration.
 */
@ApplicationScoped
@Slf4j
public class TimelineProcessingService {

    private final MovementTimelineMerger timelineMerger;
    private final PathSimplificationService pathSimplificationService;

    @Inject
    public TimelineProcessingService(MovementTimelineMerger timelineMerger,
                                   PathSimplificationService pathSimplificationService) {
        this.timelineMerger = timelineMerger;
        this.pathSimplificationService = pathSimplificationService;
    }

    /**
     * Apply all configured post-processing operations to a timeline.
     * 
     * @param config timeline configuration specifying which operations to apply
     * @param timeline the timeline to process
     * @return processed timeline
     */
    public MovementTimelineDTO processTimeline(TimelineConfig config, MovementTimelineDTO timeline) {
        if (config == null || timeline == null) {
            return timeline;
        }

        MovementTimelineDTO processedTimeline = timeline;

        // Apply merging if enabled (FIRST - before simplification)
        if (config.getIsMergeEnabled()) {
            log.debug("Applying timeline merging for user {}", timeline.getUserId());
            processedTimeline = applyMerging(config, processedTimeline);
        }

        // Apply GPS path simplification if enabled (LAST - after all other processing)
        if (isPathSimplificationEnabled(config)) {
            log.debug("Applying GPS path simplification for user {}", timeline.getUserId());
            processedTimeline = applyPathSimplification(config, processedTimeline);
        }

        // Future processing operations can be added here:
        // - Filtering short stays
        // - Merging short trips
        // - Privacy filtering
        // - Data quality improvements

        return processedTimeline;
    }

    /**
     * Apply GPS path simplification to timeline trips.
     * Reduces the number of GPS points in trip paths while preserving route accuracy.
     * 
     * @param config timeline configuration with path simplification parameters
     * @param timeline timeline to process
     * @return timeline with simplified trip paths
     */
    public MovementTimelineDTO applyPathSimplification(TimelineConfig config, MovementTimelineDTO timeline) {
        if (timeline == null || timeline.getTrips() == null || timeline.getTrips().isEmpty()) {
            return timeline;
        }

        log.debug("Simplifying paths for {} trips", timeline.getTrips().size());
        
        List<TimelineTripDTO> simplifiedTrips = timeline.getTrips().stream()
                .map(trip -> pathSimplificationService.simplifyTripPath(trip, config))
                .collect(Collectors.toList());

        // Count successful simplifications for logging
        int simplificationCount = 0;
        for (int i = 0; i < timeline.getTrips().size(); i++) {
            TimelineTripDTO original = timeline.getTrips().get(i);
            TimelineTripDTO simplified = simplifiedTrips.get(i);
            if (original.getPath() != null && simplified.getPath() != null &&
                simplified.getPath().size() < original.getPath().size()) {
                simplificationCount++;
            }
        }

        log.debug("Successfully simplified {} out of {} trip paths", simplificationCount, timeline.getTrips().size());

        MovementTimelineDTO result = new MovementTimelineDTO(timeline.getUserId(), timeline.getStays(), simplifiedTrips);
        result.setDataSource(timeline.getDataSource());
        result.setLastUpdated(timeline.getLastUpdated());
        result.setIsStale(timeline.getIsStale());
        return result;
    }

    /**
     * Apply merging operations to the timeline.
     * Merges nearby locations and consolidates similar timeline entries.
     * 
     * @param config timeline configuration with merging parameters
     * @param timeline timeline to merge
     * @return merged timeline
     */
    public MovementTimelineDTO applyMerging(TimelineConfig config, MovementTimelineDTO timeline) {
        if (timeline == null) {
            return null;
        }

        log.debug("Merging timeline with {} stays and {} trips", 
                 timeline.getStays().size(), timeline.getTrips().size());
        
        MovementTimelineDTO mergedTimeline = timelineMerger.mergeSameNamedLocations(config, timeline);
        
        log.debug("After merging: {} stays and {} trips", 
                 mergedTimeline.getStays().size(), mergedTimeline.getTrips().size());
        
        return mergedTimeline;
    }

    /**
     * Apply filtering operations to the timeline.
     * This is a placeholder for future filtering implementations.
     * 
     * @param config timeline configuration with filtering parameters
     * @param timeline timeline to filter
     * @return filtered timeline
     */
    public MovementTimelineDTO applyFiltering(TimelineConfig config, MovementTimelineDTO timeline) {
        // Placeholder for future filtering operations:
        // - Remove stays shorter than minimum duration
        // - Remove trips shorter than minimum distance
        // - Apply privacy filters
        // - Remove low-confidence detections
        
        return timeline;
    }

    /**
     * Check if path simplification is enabled in the configuration.
     * 
     * @param config timeline configuration
     * @return true if path simplification is enabled and properly configured
     */
    private boolean isPathSimplificationEnabled(TimelineConfig config) {
        return config.getPathSimplificationEnabled() != null && 
               config.getPathSimplificationEnabled() &&
               config.getPathSimplificationTolerance() != null &&
               config.getPathSimplificationTolerance() > 0;
    }

    /**
     * Check if any post-processing is needed based on configuration.
     * 
     * @param config timeline configuration
     * @return true if any post-processing operations are enabled
     */
    public boolean isProcessingNeeded(TimelineConfig config) {
        if (config == null) {
            return false;
        }
        
        // Check if any processing operations are enabled
        return config.getIsMergeEnabled() || isPathSimplificationEnabled(config); 
        // Future: || config.getIsFilteringEnabled() || config.getIsPrivacyEnabled()
    }
}
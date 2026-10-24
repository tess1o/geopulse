package org.github.tess1o.geopulse.streaming.merge;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;

import java.time.Duration;
import java.util.List;


/**
 * Service responsible for analyzing potential timeline merges.
 * Determines which stays can be merged based on configuration criteria.
 */
@ApplicationScoped
@Slf4j
public class TimelineMergeAnalysisService {

    /**
     * Decide if a stay can join a group of merged stays at the same location. Two merge strategies apply:
     * <ol>
     *   <li>INTEGRITY MERGE: nothing recorded between the stays, so they are one stay split in two and are merged
     *       independent of the distance/time thresholds.</li>
     *   <li>USER PREFERENCE MERGE: only trips between the stays, shorter than both the configured distance and
     *       time.</li>
     * </ol>
     * A data gap between the stays always keeps them apart.
     *
     * @param timelineConfig configuration with merge thresholds
     * @param groupStart     first stay of the group the stay would join
     * @param between        events between the group's last stay and the candidate, in timeline order
     * @param candidate      stay that would join the group
     * @return true if the candidate can join the group
     */
    public boolean canMergeStays(TimelineConfig timelineConfig,
                                 Stay groupStart,
                                 List<TimelineEvent> between,
                                 Stay candidate) {
        if (timelineConfig == null || groupStart == null || candidate == null) {
            return false;
        }

        if (!isSameLocation(groupStart, candidate)) {
            return false;
        }

        // Don't merge stays if there's a data gap between them
        if (between.stream().anyMatch(event -> !(event instanceof Trip))) {
            log.debug("Cannot merge stays - data gap found between {} and {}",
                    groupStart.getStartTime(), candidate.getStartTime());
            return false;
        }

        if (between.isEmpty()) {
            log.debug("INTEGRITY MERGE: Consecutive stays at '{}' with NO trips/gaps between them",
                    candidate.getLocationName());
            return true;
        }

        if (!timelineConfig.getIsMergeEnabled()) {
            return false;
        }

        log.debug("Analyzing merge potential between stays at '{}' from {} to {}",
                candidate.getLocationName(), groupStart.getStartTime(), candidate.getStartTime());

        MergeCriteria criteria = calculateMergeCriteria(between.stream().map(Trip.class::cast).toList());

        // Both: a short trip that took long is a walk around the place, a quick one that went far is a real drive
        boolean canMerge = criteria.totalDistance() < timelineConfig.getMergeMaxDistanceMeters() &&
                criteria.totalDuration().compareTo(Duration.ofMinutes(timelineConfig.getMergeMaxTimeGapMinutes())) < 0;

        log.debug("Merge analysis result: distance={}m, duration={}s, canMerge={}",
                criteria.totalDistance(), criteria.totalDuration().getSeconds(), canMerge);

        return canMerge;
    }

    /**
     * Calculate merge criteria (distance and duration) of the trips between two stays.
     *
     * @param trips trips between the stays
     * @return merge criteria with total distance and duration
     */
    public MergeCriteria calculateMergeCriteria(List<Trip> trips) {
        double totalDistance = 0.0;
        Duration totalDuration = Duration.ZERO;

        for (Trip trip : trips) {
            totalDistance += trip.getDistanceMeters(); // Already in meters
            totalDuration = totalDuration.plus(trip.getDuration());

            log.debug("Found trip between stays: distance={}m, duration={}s",
                    trip.getDistanceMeters(), trip.getDuration().getSeconds());
        }

        return new MergeCriteria(totalDistance, totalDuration);
    }

    /**
     * Check if two stay locations represent the same location by comparing source references.
     * This approach is resistant to name changes in favorites.
     *
     * @param stay1 first stay location
     * @param stay2 second stay location
     * @return true if they represent the same location
     */
    public boolean isSameLocation(Stay stay1, Stay stay2) {
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
     * Record containing merge analysis criteria.
     */
    public record MergeCriteria(
            double totalDistance,  // Total distance in meters
            Duration totalDuration
    ) {
    }
}

package org.github.tess1o.geopulse.timeline.merge;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.timeline.model.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineTripDTO;

import java.time.Instant;
import java.util.List;

/**
 * Service responsible for analyzing potential timeline merges.
 * Determines which stays can be merged based on configuration criteria.
 */
@ApplicationScoped
@Slf4j
public class TimelineMergeAnalysisService {

    /**
     * Analyze if two stays can be merged based on configuration criteria.
     * Checks both distance and duration thresholds for trips between stays.
     *
     * @param timelineConfig configuration with merge thresholds
     * @param firstStay      first stay to analyze
     * @param secondStay     second stay to analyze
     * @param trips          all trips to analyze for trips between stays
     * @return true if stays can be merged
     */
    public boolean canMergeStays(TimelineConfig timelineConfig,
                                 TimelineStayLocationDTO firstStay,
                                 TimelineStayLocationDTO secondStay,
                                 List<TimelineTripDTO> trips) {
        if (timelineConfig == null || firstStay == null || secondStay == null) {
            return false;
        }

        // Only merge stays with the same location name
        if (!firstStay.getLocationName().equals(secondStay.getLocationName())) {
            return false;
        }

        log.debug("Analyzing merge potential between stays at '{}' from {} to {}",
                firstStay.getLocationName(), firstStay.getTimestamp(), secondStay.getTimestamp());

        MergeCriteria criteria = calculateMergeCriteria(firstStay, secondStay, trips);

        boolean canMerge = criteria.totalDistance() < timelineConfig.getMergeMaxDistanceMeters() ||
                criteria.totalDuration() < timelineConfig.getMergeMaxTimeGapMinutes();

        log.debug("Merge analysis result: distance={}m, duration={}min, canMerge={}",
                criteria.totalDistance(), criteria.totalDuration(), canMerge);

        return canMerge;
    }

    /**
     * Calculate merge criteria (distance and duration) between two stays.
     * Analyzes all trips that occur between the two stays chronologically.
     *
     * @param firstStay  first stay
     * @param secondStay second stay
     * @param trips      all trips to analyze
     * @return merge criteria with total distance and duration
     */
    public MergeCriteria calculateMergeCriteria(TimelineStayLocationDTO firstStay,
                                                TimelineStayLocationDTO secondStay,
                                                List<TimelineTripDTO> trips) {
        if (firstStay == null || secondStay == null || trips == null) {
            return new MergeCriteria(0.0, 0);
        }

        double totalDistance = 0.0;
        long totalDuration = 0;

        // Find trips between the two stays chronologically
        Instant firstStayTime = firstStay.getTimestamp();
        Instant secondStayTime = secondStay.getTimestamp();

        for (TimelineTripDTO trip : trips) {
            if (isTripBetweenStays(trip, firstStayTime, secondStayTime)) {
                totalDistance += trip.getDistanceKm() * 1000; // Convert to meters
                totalDuration += trip.getTripDuration();

                log.debug("Found trip between stays: distance={}m, duration={}min",
                        trip.getDistanceKm() * 1000, trip.getTripDuration());
            }
        }

        return new MergeCriteria(totalDistance, totalDuration);
    }

    /**
     * Check if a trip occurs between two stay timestamps.
     *
     * @param trip           trip to check
     * @param firstStayTime  timestamp of first stay
     * @param secondStayTime timestamp of second stay
     * @return true if trip is between the stays
     */
    public boolean isTripBetweenStays(TimelineTripDTO trip, Instant firstStayTime, Instant secondStayTime) {
        if (trip == null || firstStayTime == null || secondStayTime == null) {
            return false;
        }

        return trip.getTimestamp().isAfter(firstStayTime) &&
                trip.getTimestamp().isBefore(secondStayTime);
    }

    /**
     * Record containing merge analysis criteria.
     */
    public record MergeCriteria(
            double totalDistance,  // Total distance in meters
            long totalDuration     // Total duration in minutes
    ) {
    }
}
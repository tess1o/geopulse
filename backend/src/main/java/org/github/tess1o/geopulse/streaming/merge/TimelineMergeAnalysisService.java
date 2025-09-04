package org.github.tess1o.geopulse.streaming.merge;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;

import java.time.Instant;
import java.util.List;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineDataGapDTO;

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
     * @param dataGaps       all data gaps to check for gaps between stays
     * @return true if stays can be merged
     */
    public boolean canMergeStays(TimelineConfig timelineConfig,
                                 TimelineStayLocationDTO firstStay,
                                 TimelineStayLocationDTO secondStay,
                                 List<TimelineTripDTO> trips,
                                 List<TimelineDataGapDTO> dataGaps) {
        if (timelineConfig == null || firstStay == null || secondStay == null) {
            return false;
        }

        // Only merge stays with the same location name
        if (!firstStay.getLocationName().equals(secondStay.getLocationName())) {
            return false;
        }

        // Don't merge stays if there's a data gap between them
        if (hasDataGapBetweenStays(firstStay, secondStay, dataGaps)) {
            log.debug("Cannot merge stays - data gap found between {} and {}", 
                     firstStay.getTimestamp(), secondStay.getTimestamp());
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
     * Backward compatible overload without data gaps.
     * This version will allow merging as long as distance/duration criteria are met.
     */
    public boolean canMergeStays(TimelineConfig timelineConfig,
                                 TimelineStayLocationDTO firstStay,
                                 TimelineStayLocationDTO secondStay,
                                 List<TimelineTripDTO> trips) {
        return canMergeStays(timelineConfig, firstStay, secondStay, trips, null);
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
                totalDistance += trip.getDistanceMeters(); // Already in meters
                totalDuration += trip.getTripDuration();

                log.debug("Found trip between stays: distance={}m, duration={}min",
                        trip.getDistanceMeters(), trip.getTripDuration());
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
     * Check if there's a data gap between two stays.
     * If any data gap exists between the stay timestamps, the stays should not be merged.
     *
     * @param firstStay  first stay
     * @param secondStay second stay
     * @param dataGaps   all data gaps to check
     * @return true if there's a data gap between the stays
     */
    public boolean hasDataGapBetweenStays(TimelineStayLocationDTO firstStay,
                                          TimelineStayLocationDTO secondStay,
                                          List<TimelineDataGapDTO> dataGaps) {
        if (firstStay == null || secondStay == null || dataGaps == null || dataGaps.isEmpty()) {
            return false;
        }

        Instant firstStayTime = firstStay.getTimestamp();
        Instant secondStayTime = secondStay.getTimestamp();

        // Check if any data gap overlaps with the time period between the two stays
        for (TimelineDataGapDTO gap : dataGaps) {
            if (isDataGapBetweenStays(gap, firstStayTime, secondStayTime)) {
                log.debug("Found data gap between stays: gap from {} to {}, stays at {} and {}",
                         gap.getStartTime(), gap.getEndTime(), firstStayTime, secondStayTime);
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a data gap occurs between two stay timestamps.
     *
     * @param gap            data gap to check
     * @param firstStayTime  timestamp of first stay
     * @param secondStayTime timestamp of second stay
     * @return true if gap overlaps with the time period between stays
     */
    private boolean isDataGapBetweenStays(TimelineDataGapDTO gap, Instant firstStayTime, Instant secondStayTime) {
        if (gap == null || firstStayTime == null || secondStayTime == null) {
            return false;
        }

        // Gap overlaps with the time period between stays if:
        // 1. Gap starts after first stay and before second stay, OR
        // 2. Gap ends after first stay and before second stay, OR  
        // 3. Gap completely spans the time between stays
        return (gap.getStartTime().isAfter(firstStayTime) && gap.getStartTime().isBefore(secondStayTime)) ||
               (gap.getEndTime().isAfter(firstStayTime) && gap.getEndTime().isBefore(secondStayTime)) ||
               (gap.getStartTime().isBefore(firstStayTime) && gap.getEndTime().isAfter(secondStayTime));
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
package org.github.tess1o.geopulse.timeline.detection.gaps;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.timeline.model.TimelineDataGapDTO;
import org.github.tess1o.geopulse.timeline.model.TrackPoint;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service responsible for detecting data gaps in GPS tracking data.
 * Identifies periods where GPS data is missing for extended periods,
 * which prevents artificial extension of stays during phone-off periods.
 */
@ApplicationScoped
@Slf4j
public class DataGapDetectionService {

    /**
     * Detect data gaps in GPS track points based on configuration thresholds.
     * 
     * @param config timeline configuration containing gap detection parameters
     * @param trackPoints sorted list of GPS track points (should be sorted by timestamp)
     * @return list of detected data gaps
     */
    public List<TimelineDataGapDTO> detectDataGaps(TimelineConfig config, List<TrackPoint> trackPoints) {
        log.info("=== DATA GAP DETECTION START ===");
        log.info("Input track points: {}", trackPoints != null ? trackPoints.size() : 0);
        
        if (trackPoints == null || trackPoints.size() < 2) {
            log.warn("Not enough track points for gap detection: {}", trackPoints != null ? trackPoints.size() : 0);
            return List.of();
        }

        Integer gapThresholdSeconds = config.getDataGapThresholdSeconds();
        Integer minGapDurationSeconds = config.getDataGapMinDurationSeconds();
        
        log.info("Configuration - Threshold: {}s ({}h), MinDuration: {}s ({}h)", 
                gapThresholdSeconds, gapThresholdSeconds != null ? gapThresholdSeconds / 3600.0 : null,
                minGapDurationSeconds, minGapDurationSeconds != null ? minGapDurationSeconds / 3600.0 : null);
        
        if (gapThresholdSeconds == null || minGapDurationSeconds == null) {
            log.error("Data gap detection thresholds not configured! Threshold: {}, MinDuration: {}", 
                     gapThresholdSeconds, minGapDurationSeconds);
            return List.of();
        }

        // Log time range of track points
        if (!trackPoints.isEmpty()) {
            Instant firstTime = trackPoints.get(0).getTimestamp();
            Instant lastTime = trackPoints.get(trackPoints.size() - 1).getTimestamp();
            long totalSpanHours = Duration.between(firstTime, lastTime).toHours();
            log.info("Track point time range: {} to {} (span: {}h)", firstTime, lastTime, totalSpanHours);
        }

        List<TimelineDataGapDTO> dataGaps = new ArrayList<>();
        long largestGapSeconds = 0;
        int totalGapsFound = 0;
        int gapsTooShort = 0;
        
        // Iterate through consecutive track points to find time gaps
        for (int i = 0; i < trackPoints.size() - 1; i++) {
            TrackPoint currentPoint = trackPoints.get(i);
            TrackPoint nextPoint = trackPoints.get(i + 1);
            
            Instant currentTime = currentPoint.getTimestamp();
            Instant nextTime = nextPoint.getTimestamp();
            
            // Calculate time difference between consecutive points
            long timeDifferenceSeconds = Duration.between(currentTime, nextTime).getSeconds();
            
            if (timeDifferenceSeconds > largestGapSeconds) {
                largestGapSeconds = timeDifferenceSeconds;
            }
            
            // Check if the gap exceeds the threshold
            if (timeDifferenceSeconds > gapThresholdSeconds) {
                totalGapsFound++;
                // Check if the gap meets the minimum duration requirement
                if (timeDifferenceSeconds >= minGapDurationSeconds) {
                    TimelineDataGapDTO gap = new TimelineDataGapDTO(currentTime, nextTime, timeDifferenceSeconds);
                    dataGaps.add(gap);
                    
                    log.info("✓ DATA GAP DETECTED: {} to {} (duration: {}s = {}h = {}min)", 
                             currentTime, nextTime, timeDifferenceSeconds, 
                             timeDifferenceSeconds / 3600.0, timeDifferenceSeconds / 60);
                } else {
                    gapsTooShort++;
                    log.info("Gap found but too short: {} to {} ({}s < {}s threshold)", 
                             currentTime, nextTime, timeDifferenceSeconds, minGapDurationSeconds);
                }
            } else if (timeDifferenceSeconds > 3600) { // Log any gaps > 1 hour for debugging
                log.debug("Large gap but under threshold: {} to {} ({}s = {}h)", 
                         currentTime, nextTime, timeDifferenceSeconds, timeDifferenceSeconds / 3600.0);
            }
        }

        // Merge consecutive gaps
        List<TimelineDataGapDTO> mergedGaps = mergeConsecutiveGaps(dataGaps);
        
        log.info("=== DATA GAP DETECTION SUMMARY ===");
        log.info("Track points processed: {}", trackPoints.size());
        log.info("Largest gap found: {}s ({}h)", largestGapSeconds, largestGapSeconds / 3600.0);
        log.info("Gaps exceeding threshold: {}", totalGapsFound);
        log.info("Gaps too short to record: {}", gapsTooShort);
        log.info("Initial data gaps found: {}", dataGaps.size());
        log.info("Final data gaps after merging: {}", mergedGaps.size());
        
        for (int i = 0; i < mergedGaps.size(); i++) {
            TimelineDataGapDTO gap = mergedGaps.get(i);
            log.info("Gap {}: {} - {} ({}h)", i + 1, gap.getStartTime(), gap.getEndTime(), gap.getDurationSeconds() / 3600.0);
        }
        log.info("=== DATA GAP DETECTION END ===");
        
        return mergedGaps;
    }

    /**
     * Merge consecutive gaps that are adjacent (one ends exactly when the next begins).
     * This prevents the UI from showing multiple separate gap entries for what is actually
     * one continuous period of missing GPS data.
     * 
     * @param gaps list of detected gaps (should be sorted by start time)
     * @return list of merged gaps
     */
    private List<TimelineDataGapDTO> mergeConsecutiveGaps(List<TimelineDataGapDTO> gaps) {
        if (gaps == null || gaps.size() <= 1) {
            return gaps;
        }
        
        // Sort gaps by start time to ensure proper order
        List<TimelineDataGapDTO> sortedGaps = new ArrayList<>(gaps);
        sortedGaps.sort(Comparator.comparing(TimelineDataGapDTO::getStartTime));
        
        List<TimelineDataGapDTO> mergedGaps = new ArrayList<>();
        TimelineDataGapDTO currentGap = sortedGaps.get(0);
        
        for (int i = 1; i < sortedGaps.size(); i++) {
            TimelineDataGapDTO nextGap = sortedGaps.get(i);
            
            // Check if gaps are consecutive (current gap ends exactly when next gap starts)
            if (currentGap.getEndTime().equals(nextGap.getStartTime())) {
                // Merge gaps: extend current gap to include next gap
                long totalDuration = currentGap.getDurationSeconds() + nextGap.getDurationSeconds();
                currentGap = new TimelineDataGapDTO(currentGap.getStartTime(), nextGap.getEndTime(), totalDuration);
                log.debug("Merged consecutive gaps: {} + {} = {} total seconds", 
                         currentGap.getDurationSeconds() - nextGap.getDurationSeconds(), 
                         nextGap.getDurationSeconds(), totalDuration);
            } else {
                // Gaps are not consecutive, add current gap to result and move to next
                mergedGaps.add(currentGap);
                currentGap = nextGap;
            }
        }
        
        // Add the final gap
        mergedGaps.add(currentGap);
        
        log.info("Gap merging: {} initial gaps merged into {} consecutive gaps", 
                gaps.size(), mergedGaps.size());
        
        return mergedGaps;
    }

    /**
     * Check if two consecutive track points represent a data gap.
     * 
     * @param config timeline configuration
     * @param firstPoint first track point
     * @param secondPoint second track point  
     * @return true if there's a significant data gap between the points
     */
    public boolean hasDataGap(TimelineConfig config, TrackPoint firstPoint, TrackPoint secondPoint) {
        if (config.getDataGapThresholdSeconds() == null) {
            return false;
        }
        
        long timeDifferenceSeconds = Duration.between(
            firstPoint.getTimestamp(), 
            secondPoint.getTimestamp()
        ).getSeconds();
        
        return timeDifferenceSeconds > config.getDataGapThresholdSeconds();
    }

    /**
     * Split track points at data gap boundaries.
     * This method can be used to prevent stay point detection algorithms
     * from bridging across data gaps.
     * 
     * @param config timeline configuration
     * @param trackPoints original track points
     * @return list of track point segments, split at gap boundaries
     */
    public List<List<TrackPoint>> splitTrackPointsAtGaps(TimelineConfig config, List<TrackPoint> trackPoints) {
        if (trackPoints == null || trackPoints.size() < 2) {
            return trackPoints == null ? List.of() : List.of(trackPoints);
        }

        List<List<TrackPoint>> segments = new ArrayList<>();
        List<TrackPoint> currentSegment = new ArrayList<>();
        
        for (int i = 0; i < trackPoints.size(); i++) {
            TrackPoint currentPoint = trackPoints.get(i);
            currentSegment.add(currentPoint);
            
            // Check if there's a gap to the next point
            if (i < trackPoints.size() - 1) {
                TrackPoint nextPoint = trackPoints.get(i + 1);
                if (hasDataGap(config, currentPoint, nextPoint)) {
                    // End current segment and start a new one
                    if (!currentSegment.isEmpty()) {
                        segments.add(new ArrayList<>(currentSegment));
                        currentSegment.clear();
                    }
                }
            }
        }
        
        // Add the final segment if it has points
        if (!currentSegment.isEmpty()) {
            segments.add(currentSegment);
        }
        
        log.debug("Split {} track points into {} segments at gap boundaries", 
                 trackPoints.size(), segments.size());
        return segments;
    }
}
package org.github.tess1o.geopulse.streaming.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * Represents a data gap event - a period where no GPS data was available.
 * Data gaps are created when the time between consecutive GPS points
 * exceeds the configured data gap threshold, typically indicating that
 * the user's device was turned off or GPS was disabled.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataGap implements TimelineEvent {
    
    private Instant startTime;
    private Duration duration;
    
    /**
     * Create a data gap from start time to end time.
     *
     * @param startTime when the data gap began
     * @param endTime when the data gap ended
     * @return new DataGap instance
     */
    public static DataGap fromTimeRange(Instant startTime, Instant endTime) {
        Duration gapDuration = Duration.between(startTime, endTime);
        return DataGap.builder()
            .startTime(startTime)
            .duration(gapDuration)
            .build();
    }
    
    @Override
    public TimelineEventType getType() {
        return TimelineEventType.DATA_GAP;
    }
    
    /**
     * Check if this data gap is longer than a specified threshold.
     *
     * @param threshold the minimum duration to compare against
     * @return true if this gap is longer than the threshold
     */
    public boolean isLongerThan(Duration threshold) {
        return duration.compareTo(threshold) > 0;
    }
    
    /**
     * Get a human-readable description of this data gap.
     *
     * @return description string including duration
     */
    public String getDescription() {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        
        if (hours > 0) {
            return String.format("Data gap: %d hours %d minutes", hours, minutes);
        } else {
            return String.format("Data gap: %d minutes", minutes);
        }
    }
}
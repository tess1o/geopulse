package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO representing a gap in GPS data within a timeline.
 * Represents periods where no GPS data is available between two points,
 * indicating unknown user activity during that time.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimelineDataGapDTO {
    
    /**
     * Start time of the data gap
     */
    private Instant startTime;
    
    /**
     * End time of the data gap  
     */
    private Instant endTime;
    
    /**
     * Duration of the gap in seconds
     */
    private long durationSeconds;
    
    /**
     * Constructor that automatically calculates duration
     */
    public TimelineDataGapDTO(Instant startTime, Instant endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationSeconds = endTime.getEpochSecond() - startTime.getEpochSecond();
    }
    
    /**
     * Get duration in minutes for convenience
     */
    public long getDurationMinutes() {
        return durationSeconds / 60;
    }
}
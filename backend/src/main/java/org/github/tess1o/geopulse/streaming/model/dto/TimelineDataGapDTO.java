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
     * Database ID for targeting this gap in manual override actions.
     */
    private Long id;
    
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
     * True when this is the currently open/ongoing gap from the latest GPS point to now.
     * Ongoing gaps are not convertible to manual stays.
     */
    private boolean ongoing;
    
    /**
     * Constructor that automatically calculates duration
     */
    public TimelineDataGapDTO(Long id, Instant startTime, Instant endTime) {
        this(id, startTime, endTime, false);
    }

    /**
     * Constructor that automatically calculates duration and allows ongoing flag assignment.
     */
    public TimelineDataGapDTO(Long id, Instant startTime, Instant endTime, boolean ongoing) {
        this.id = id;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationSeconds = endTime.getEpochSecond() - startTime.getEpochSecond();
        this.ongoing = ongoing;
    }
    
    /**
     * Get duration in minutes for convenience
     */
    public long getDurationMinutes() {
        return durationSeconds / 60;
    }
}

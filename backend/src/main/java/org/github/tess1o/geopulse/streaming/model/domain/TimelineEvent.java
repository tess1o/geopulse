package org.github.tess1o.geopulse.streaming.model.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Base interface for all timeline events produced by the streaming timeline processor.
 * Timeline events represent distinct activities: stays (stationary periods), 
 * trips (movement periods), and data gaps (periods without GPS data).
 */
public interface TimelineEvent {
    
    /**
     * Get the start time of this timeline event.
     *
     * @return the timestamp when this event began
     */
    Instant getStartTime();
    
    /**
     * Get the duration of this timeline event.
     *
     * @return the duration of this event
     */
    Duration getDuration();
    
    /**
     * Get the end time of this timeline event (calculated as startTime + duration).
     *
     * @return the timestamp when this event ended
     */
    default Instant getEndTime() {
        return getStartTime().plus(getDuration());
    }
    
    /**
     * Get the type of this timeline event.
     *
     * @return the event type
     */
    TimelineEventType getType();

    /**
     * Check if this event contains the given timestamp.
     *
     * @param timestamp the timestamp to check
     * @return true if the timestamp falls within this event's time range
     */
    default boolean contains(Instant timestamp) {
        return !timestamp.isBefore(getStartTime()) && timestamp.isBefore(getEndTime());
    }
}
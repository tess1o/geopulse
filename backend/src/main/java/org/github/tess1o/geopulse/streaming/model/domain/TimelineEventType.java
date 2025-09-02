package org.github.tess1o.geopulse.streaming.model.domain;

/**
 * Enumeration of the different types of timeline events that can be generated
 * by the streaming timeline processor.
 */
public enum TimelineEventType {
    /**
     * A stay event represents a period when the user was stationary at a location
     * for longer than the configured minimum duration threshold.
     */
    STAY,
    
    /**
     * A trip event represents a period of movement between two locations,
     * including the path taken and classification of movement type (walk, car, etc.).
     */
    TRIP,
    
    /**
     * A data gap event represents a period where no GPS data was available,
     * typically when the user's device was turned off or GPS was disabled.
     */
    DATA_GAP
}
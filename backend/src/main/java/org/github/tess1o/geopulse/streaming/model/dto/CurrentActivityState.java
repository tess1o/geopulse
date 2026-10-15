package org.github.tess1o.geopulse.streaming.model.dto;

/** What the user is doing right now, as far as the timeline knows. */
public enum CurrentActivityState {
    /** Staying at a place. */
    STAY,
    /** Moving between places. */
    TRIP,
    /** No GPS data has arrived for longer than the data gap threshold. */
    DATA_GAP,
    /** GPS data exists, but the timeline has not been generated yet. */
    UNKNOWN,
    /** The user has no GPS data. */
    NO_DATA
}

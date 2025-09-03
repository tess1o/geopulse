package org.github.tess1o.geopulse.streaming.model.shared;

/**
 * Enumeration of trip types that can be classified by analyzing GPS movement patterns.
 * Trip classification is based on speed analysis and movement characteristics.
 */
public enum TripType {
    /**
     * Walking trip - characterized by low speeds and human movement patterns.
     */
    WALK,
    
    /**
     * Car trip - characterized by higher speeds and vehicular movement patterns.
     */
    CAR,
    
    /**
     * Unknown trip type - used when classification cannot be determined reliably
     * due to insufficient data or ambiguous speed patterns.
     */
    UNKNOWN;
}
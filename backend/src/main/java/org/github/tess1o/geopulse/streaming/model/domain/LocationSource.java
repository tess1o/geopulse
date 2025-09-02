package org.github.tess1o.geopulse.streaming.model.domain;

/**
 * Enumeration to track the source of a timeline location name.
 * Used for debugging, user information, and proper handling of location data.
 */
public enum LocationSource {
    /**
     * Location name comes from an active user favorite
     */
    FAVORITE,
    
    /**
     * Location name comes from geocoding service results
     */
    GEOCODING,
    
    /**
     * Location name preserved from a deleted favorite (historical name)
     */
    HISTORICAL
}
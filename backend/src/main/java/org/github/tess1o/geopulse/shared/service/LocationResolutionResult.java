package org.github.tess1o.geopulse.shared.service;

import lombok.Builder;
import lombok.Data;

/**
 * Result of location name resolution containing both the display name
 * and references to the source data for referential integrity.
 */
@Data
@Builder
public class LocationResolutionResult {
    
    /**
     * The resolved location name for display
     */
    private String locationName;
    
    /**
     * ID of the favorite location if this location was resolved from user's favorites.
     * Will be null if location came from geocoding.
     */
    private Long favoriteId;
    
    /**
     * ID of the geocoding result if this location was resolved via external geocoding.
     * Will be null if location came from user's favorites.
     */
    private Long geocodingId;
    
    /**
     * Create a result from a favorite location
     */
    public static LocationResolutionResult fromFavorite(String locationName, Long favoriteId) {
        return LocationResolutionResult.builder()
                .locationName(locationName)
                .favoriteId(favoriteId)
                .build();
    }
    
    /**
     * Create a result from geocoding
     */
    public static LocationResolutionResult fromGeocoding(String locationName, Long geocodingId) {
        return LocationResolutionResult.builder()
                .locationName(locationName)
                .geocodingId(geocodingId)
                .build();
    }
}
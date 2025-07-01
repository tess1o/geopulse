package org.github.tess1o.geopulse.geocoding.model.common;

import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * Common interface for geocoding results that can be formatted into display names.
 * Providers implement this to provide only the essential information needed for storage.
 */
public interface FormattableGeocodingResult {
    
    /**
     * Get the request coordinates (what was asked for).
     * 
     * @return Request coordinates
     */
    Point getRequestCoordinates();
    
    /**
     * Get the result coordinates (what the provider returned).
     * Might be slightly different from request coordinates.
     * 
     * @return Result coordinates
     */
    Point getResultCoordinates();
    
    /**
     * Get the bounding box for this location (optional).
     * 
     * @return Bounding box polygon or null
     */
    Polygon getBoundingBox();
    
    /**
     * Get the formatted display name that should be shown to end users.
     * This is calculated by each provider's formatter and represents the final
     * human-readable string (e.g., "Empire State Building (350 5th Ave)").
     * 
     * @return Formatted display name
     */
    String getFormattedDisplayName();
    
    /**
     * Get the name of the provider that generated this result.
     * 
     * @return Provider name
     */
    String getProviderName();
    
    /**
     * Get the city name from the geocoding result.
     * 
     * @return City name or null if not available
     */
    String getCity();
    
    /**
     * Get the country name from the geocoding result.
     * 
     * @return Country name or null if not available
     */
    String getCountry();
}
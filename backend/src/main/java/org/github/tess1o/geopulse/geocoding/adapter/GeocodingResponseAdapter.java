package org.github.tess1o.geopulse.geocoding.adapter;

import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.locationtech.jts.geom.Point;

/**
 * Simplified adapter interface for converting provider-specific geocoding responses
 * to a common, simplified format with pre-formatted display names.
 * 
 * @param <T> The provider-specific response type
 */
public interface GeocodingResponseAdapter<T> {
    
    /**
     * Adapt a provider-specific response to a simplified geocoding result.
     * The adapter should format the display name according to the provider's
     * best practices and return only essential information.
     * 
     * @param providerResponse The provider-specific response
     * @param requestCoordinates The original request coordinates
     * @param providerName Name of the provider that generated the response
     * @return Simplified geocoding result with formatted display name
     */
    FormattableGeocodingResult adapt(T providerResponse, Point requestCoordinates, String providerName);

    
    /**
     * Get the name of the provider this adapter handles.
     * 
     * @return Provider name
     */
    String getProviderName();
}
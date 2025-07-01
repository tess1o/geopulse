package org.github.tess1o.geopulse.geocoding.model.googlemaps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Viewport/bounds from Google Maps Geocoding API.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleMapsViewport {
    
    /**
     * Northeast corner of the viewport.
     */
    private GoogleMapsLocation northeast;
    
    /**
     * Southwest corner of the viewport.
     */
    private GoogleMapsLocation southwest;
}
package org.github.tess1o.geopulse.geocoding.model.googlemaps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Location coordinates from Google Maps Geocoding API.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleMapsLocation {
    
    /**
     * Latitude value.
     */
    private double lat;
    
    /**
     * Longitude value.
     */
    private double lng;
}
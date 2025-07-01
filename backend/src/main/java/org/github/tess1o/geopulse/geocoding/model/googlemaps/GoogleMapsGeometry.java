package org.github.tess1o.geopulse.geocoding.model.googlemaps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Geometry information from Google Maps Geocoding API.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleMapsGeometry {
    
    /**
     * Geocoded latitude and longitude value.
     */
    private GoogleMapsLocation location;
    
    /**
     * Additional data about the specified location.
     */
    @JsonProperty("location_type")
    private String locationType;
    
    /**
     * Recommended viewport for displaying the returned result.
     */
    private GoogleMapsViewport viewport;
    
    /**
     * Bounding box which can fully contain the returned result (optional).
     */
    private GoogleMapsViewport bounds;
}
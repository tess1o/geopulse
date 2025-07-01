package org.github.tess1o.geopulse.geocoding.model.mapbox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Mapbox Geocoding API response format.
 * Based on the Mapbox Geocoding API documentation.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MapboxResponse {
    
    /**
     * "FeatureCollection" for GeoJSON compliance.
     */
    private String type;
    
    /**
     * Array of feature objects.
     */
    private List<MapboxFeature> features;
    
    /**
     * Query string that was searched for.
     */
    private List<String> query;
    
    /**
     * Attribution information.
     */
    private String attribution;
}
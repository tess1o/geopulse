package org.github.tess1o.geopulse.geocoding.model.mapbox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Geometry information from Mapbox Geocoding API.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MapboxGeometry {
    
    /**
     * Geometry type (usually "Point").
     */
    private String type;
    
    /**
     * Coordinates array [longitude, latitude].
     */
    private List<Double> coordinates;
}
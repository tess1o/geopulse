package org.github.tess1o.geopulse.geocoding.model.mapbox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Individual feature from Mapbox Geocoding API.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MapboxFeature {
    
    /**
     * "Feature" for GeoJSON compliance.
     */
    private String type;
    
    /**
     * Feature's unique identifier.
     */
    private String id;
    
    /**
     * Human-readable place name.
     */
    @JsonProperty("place_name")
    private String placeName;
    
    /**
     * Array of feature types describing the feature.
     */
    @JsonProperty("place_type")
    private List<String> placeType;
    
    /**
     * Feature's numerical score from 0-1 based on input query.
     */
    private Double relevance;
    
    /**
     * Object describing the spatial geometry of the feature.
     */
    private MapboxGeometry geometry;
    
    /**
     * Object containing the feature's properties.
     */
    private MapboxProperties properties;
    
    /**
     * Human-readable text representing the feature in the requested language.
     */
    private String text;
    
    /**
     * Array representing the hierarchy of encompassing parent features.
     */
    private List<MapboxContext> context;
    
    /**
     * Bounding box of the feature.
     */
    private List<Double> bbox;
    
    /**
     * Center point of the feature.
     */
    private List<Double> center;
}
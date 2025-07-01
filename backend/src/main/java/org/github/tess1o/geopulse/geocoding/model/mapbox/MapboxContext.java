package org.github.tess1o.geopulse.geocoding.model.mapbox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Context information from Mapbox Geocoding API representing parent features.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MapboxContext {
    
    /**
     * Feature's unique identifier.
     */
    private String id;
    
    /**
     * Human-readable text representing the feature.
     */
    private String text;
    
    /**
     * Feature's Wikidata identifier.
     */
    private String wikidata;
    
    /**
     * Feature's short code (for countries, regions).
     */
    @JsonProperty("short_code")
    private String shortCode;
}
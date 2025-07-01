package org.github.tess1o.geopulse.geocoding.model.googlemaps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Address component from Google Maps Geocoding API.
 * Each component contains the name and type information.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleMapsAddressComponent {
    
    /**
     * Full text description or name of the address component.
     */
    @JsonProperty("long_name")
    private String longName;
    
    /**
     * Abbreviated textual name for the address component.
     */
    @JsonProperty("short_name")
    private String shortName;
    
    /**
     * Array indicating the type of the address component.
     */
    private List<String> types;
}
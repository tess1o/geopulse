package org.github.tess1o.geopulse.geocoding.model.googlemaps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Plus code information from Google Maps Geocoding API.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleMapsPlusCode {
    
    /**
     * Global code that can be used to identify the location.
     */
    @JsonProperty("global_code")
    private String globalCode;
    
    /**
     * Compound code for the location.
     */
    @JsonProperty("compound_code")
    private String compoundCode;
}
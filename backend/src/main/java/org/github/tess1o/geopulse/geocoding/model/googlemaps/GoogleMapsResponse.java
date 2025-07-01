package org.github.tess1o.geopulse.geocoding.model.googlemaps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Google Maps Geocoding API response format.
 * Based on the Google Maps Geocoding API documentation.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleMapsResponse {
    
    /**
     * Array of geocoded address information and geometry information.
     */
    private List<GoogleMapsResult> results;
    
    /**
     * Status of the request (OK, ZERO_RESULTS, etc.).
     */
    private String status;
    
    /**
     * Error message if status is not OK.
     */
    @JsonProperty("error_message")
    private String errorMessage;
}
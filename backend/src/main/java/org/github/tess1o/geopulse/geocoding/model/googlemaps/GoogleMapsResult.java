package org.github.tess1o.geopulse.geocoding.model.googlemaps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Individual result from Google Maps Geocoding API.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleMapsResult {
    
    /**
     * Array of separate address components.
     */
    @JsonProperty("address_components")
    private List<GoogleMapsAddressComponent> addressComponents;
    
    /**
     * Human-readable address of this location.
     */
    @JsonProperty("formatted_address")
    private String formattedAddress;
    
    /**
     * Geometry information about the result.
     */
    private GoogleMapsGeometry geometry;
    
    /**
     * Unique identifier for the place.
     */
    @JsonProperty("place_id")
    private String placeId;
    
    /**
     * Array indicating the type of the returned result.
     */
    private List<String> types;
    
    /**
     * Plus code for the location.
     */
    @JsonProperty("plus_code")
    private GoogleMapsPlusCode plusCode;
}
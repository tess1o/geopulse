package org.github.tess1o.geopulse.geocoding.model.mapbox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Properties from Mapbox Geocoding API feature.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MapboxProperties {
    /**
     * Feature's Foursquare ID if available.
     */
    private String foursquare;

    /**
     * Feature's landmark status.
     */
    private Boolean landmark;

    /**
     * Feature's street address.
     */
    private String address;

    /**
     * Feature's category.
     */
    private String category;

    /**
     * Feature's Maki icon identifier.
     */
    private String maki;

    /**
     * Feature's Wikidata identifier.
     */
    private String wikidata;

    /**
     * Feature's short code.
     */
    @JsonProperty("short_code")
    private String shortCode;

    @JsonProperty("name_preferred")
    private String namePreffered;

    @JsonProperty("full_address")
    private String fullAddress;

    @JsonProperty("name")
    private String name;
}
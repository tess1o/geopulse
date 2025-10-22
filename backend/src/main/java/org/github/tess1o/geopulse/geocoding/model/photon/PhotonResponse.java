package org.github.tess1o.geopulse.geocoding.model.photon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PhotonResponse {

    private String type;
    private List<Feature> features;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Feature {
        private String type;
        private Properties properties;
        private Geometry geometry;

    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Properties {
        @JsonProperty("osm_type")
        private String osmType;

        @JsonProperty("osm_id")
        private Long osmId;

        @JsonProperty("osm_key")
        private String osmKey;

        @JsonProperty("osm_value")
        private String osmValue;

        private String type;
        private String postcode;
        private String housenumber;
        private String countrycode;
        private String name;
        private String country;
        private String city;
        private String district;
        private String locality;
        private String street;
        private String state;
        private String county;
        private List<Double> extent;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Geometry {
        private String type;
        private List<Double> coordinates; // [longitude, latitude]

        // Convenience methods
        public Double getLongitude() {
            return coordinates != null && coordinates.size() >= 1 ? coordinates.get(0) : null;
        }

        public Double getLatitude() {
            return coordinates != null && coordinates.size() >= 2 ? coordinates.get(1) : null;
        }
    }
}

package org.github.tess1o.geopulse.geocoding.model.geoapify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeoapifyResponse {

    private List<Result> results;
    private List<Feature> features;

    public List<Result> getEffectiveResults() {
        if (results != null && !results.isEmpty()) {
            return results;
        }
        if (features == null || features.isEmpty()) {
            return List.of();
        }

        return features.stream()
                .map(Feature::toResult)
                .filter(Objects::nonNull)
                .toList();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private String formatted;
        private String name;
        private String housenumber;
        private String street;
        private String postcode;
        private String city;
        private String town;
        private String village;
        private String municipality;
        private String county;
        private String state;
        private String country;

        @JsonProperty("country_code")
        private String countryCode;

        private Double lat;
        private Double lon;
        private Bbox bbox;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Bbox {
        private Double lon1;
        private Double lat1;
        private Double lon2;
        private Double lat2;

        static Bbox fromList(List<Double> values) {
            if (values == null || values.size() < 4) {
                return null;
            }
            Bbox bbox = new Bbox();
            bbox.setLon1(values.get(0));
            bbox.setLat1(values.get(1));
            bbox.setLon2(values.get(2));
            bbox.setLat2(values.get(3));
            return bbox;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Feature {
        private Result properties;
        private Geometry geometry;
        private List<Double> bbox;

        Result toResult() {
            Result result = properties == null ? new Result() : properties;
            if (geometry != null && geometry.getLongitude() != null && geometry.getLatitude() != null) {
                if (result.getLon() == null) {
                    result.setLon(geometry.getLongitude());
                }
                if (result.getLat() == null) {
                    result.setLat(geometry.getLatitude());
                }
            }
            if (result.getBbox() == null) {
                result.setBbox(Bbox.fromList(bbox));
            }
            return result;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Geometry {
        private String type;
        private List<Double> coordinates;

        public Double getLongitude() {
            return coordinates != null && !coordinates.isEmpty() ? coordinates.get(0) : null;
        }

        public Double getLatitude() {
            return coordinates != null && coordinates.size() >= 2 ? coordinates.get(1) : null;
        }
    }
}

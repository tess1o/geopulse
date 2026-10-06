package org.github.tess1o.geopulse.mapmatching.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** A Valhalla {@code /route} request: a route through the given locations, in order. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValhallaRouteRequest {
    private List<Location> locations;
    private String costing;
    @JsonProperty("directions_type")
    private String directionsType;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private double lat;
        private double lon;
    }
}

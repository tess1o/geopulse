package org.github.tess1o.geopulse.mapmatching.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ValhallaRouteResponse {
    private Trip trip;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Trip {
        private List<Leg> legs;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Leg {
        /** Encoded polyline, 6-digit precision. */
        private String shape;
        private Summary summary;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Summary {
        /** Kilometres (Valhalla's default unit). */
        private Double length;
        /** Seconds. */
        private Double time;
    }
}

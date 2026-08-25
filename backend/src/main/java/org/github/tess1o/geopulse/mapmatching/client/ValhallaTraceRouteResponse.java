package org.github.tess1o.geopulse.mapmatching.client;

import lombok.Data;

import java.util.List;

@Data
public class ValhallaTraceRouteResponse {
    private String type;
    private ValhallaGeometry geometry;
    private List<ValhallaFeature> features;
    private ValhallaTrip trip;
    private List<ValhallaAlternate> alternates;

    @Data
    public static class ValhallaGeometry {
        private String type;
        private List<List<Double>> coordinates;
    }

    @Data
    public static class ValhallaFeature {
        private ValhallaGeometry geometry;
    }

    @Data
    public static class ValhallaTrip {
        private List<ValhallaLeg> legs;
    }

    @Data
    public static class ValhallaLeg {
        private String shape;
    }

    @Data
    public static class ValhallaAlternate {
        private ValhallaTrip trip;
    }
}

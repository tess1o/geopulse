package org.github.tess1o.geopulse.digest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DigestHighlight {
    private TripHighlight longestTrip;
    private PlaceHighlight mostVisited;
    private BusiestDay busiestDay;
    private String[] peakHours;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TripHighlight {
        private double distance;
        private String destination;
        private Instant date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlaceHighlight {
        private String name;
        private int visits;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BusiestDay {
        private Instant date;
        private int trips;
        private double distance;
    }
}

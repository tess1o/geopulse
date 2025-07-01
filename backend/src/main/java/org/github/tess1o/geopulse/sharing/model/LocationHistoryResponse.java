package org.github.tess1o.geopulse.sharing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationHistoryResponse {

    @JsonProperty("current")
    private CurrentLocationData current;

    @JsonProperty("history")
    private List<HistoricalLocationData> history;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentLocationData {
        @JsonProperty("latitude")
        private double latitude;

        @JsonProperty("longitude")
        private double longitude;

        @JsonProperty("timestamp")
        private Instant timestamp;

        @JsonProperty("accuracy")
        private Double accuracy;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalLocationData {
        @JsonProperty("latitude")
        private double latitude;

        @JsonProperty("longitude")
        private double longitude;

        @JsonProperty("timestamp")
        private Instant timestamp;
    }
}
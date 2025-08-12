package org.github.tess1o.geopulse.gps.integrations.dawarich.model.stats;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DawarichStatsResponse {
    @JsonProperty("totalDistanceKm")
    private int totalDistanceKm;

    @JsonProperty("totalPointsTracked")
    private long totalPointsTracked;

    @JsonProperty("totalReverseGeocodedPoints")
    private long totalReverseGeocodedPoints;

    @JsonProperty("totalCountriesVisited")
    private int totalCountriesVisited;

    @JsonProperty("totalCitiesVisited")
    private int totalCitiesVisited;

    @JsonProperty("yearlyStats")
    private List<DawarichYearlyStats> yearlyStats;
}

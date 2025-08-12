package org.github.tess1o.geopulse.gps.integrations.dawarich.model.stats;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DawarichYearlyStats {
    @JsonProperty("year")
    private int year;

    @JsonProperty("totalDistanceKm")
    private int totalDistanceKm;

    @JsonProperty("totalCountriesVisited")
    private int totalCountriesVisited;

    @JsonProperty("totalCitiesVisited")
    private int totalCitiesVisited;

    @JsonProperty("monthlyDistanceKm")
    private DawarichMonthlyDistanceKm monthlyDistanceKm;
}

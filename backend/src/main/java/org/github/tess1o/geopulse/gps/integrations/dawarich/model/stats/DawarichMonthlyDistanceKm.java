package org.github.tess1o.geopulse.gps.integrations.dawarich.model.stats;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DawarichMonthlyDistanceKm {
    @JsonProperty("january")
    private int january;

    @JsonProperty("february")
    private int february;

    @JsonProperty("march")
    private int march;

    @JsonProperty("april")
    private int april;

    @JsonProperty("may")
    private int may;

    @JsonProperty("june")
    private int june;

    @JsonProperty("july")
    private int july;

    @JsonProperty("august")
    private int august;

    @JsonProperty("september")
    private int september;

    @JsonProperty("october")
    private int october;

    @JsonProperty("november")
    private int november;

    @JsonProperty("december")
    private int december;
}

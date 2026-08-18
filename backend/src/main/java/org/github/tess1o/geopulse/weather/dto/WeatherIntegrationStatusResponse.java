package org.github.tess1o.geopulse.weather.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherIntegrationStatusResponse {
    private boolean enabled;
    private boolean configured;
    private String provider;
    private String attributionUrl;
}


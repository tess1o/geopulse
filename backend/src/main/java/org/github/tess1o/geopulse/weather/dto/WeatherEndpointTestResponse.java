package org.github.tess1o.geopulse.weather.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherEndpointTestResponse {
    private boolean success;
    private int statusCode;
    private String message;
    private String url;
}

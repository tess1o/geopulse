package org.github.tess1o.geopulse.weather.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherTestResponse {
    private boolean success;
    private int statusCode;
    private String message;
    private String provider;
    private String url;
}

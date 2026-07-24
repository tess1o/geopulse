package org.github.tess1o.geopulse.weather.dto;

import lombok.*;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherProviderSample {
    private double requestedLatitude;
    private double requestedLongitude;
    private Double providerLatitude;
    private Double providerLongitude;
    private Instant observedAt;
    private String timezone;
    private Integer weatherCode;
    private Double temperature;
    private Double apparentTemperature;
    private Double humidity;
    private Double precipitation;
    private Double rain;
    private Double snowfall;
    private Double cloudCover;
    private Double windSpeed;
    private Double windGust;
    private Double windDirection;
    private Double pressure;
    private Map<String, Object> rawData;
}

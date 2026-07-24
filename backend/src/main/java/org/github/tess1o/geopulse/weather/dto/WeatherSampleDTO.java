package org.github.tess1o.geopulse.weather.dto;

import lombok.*;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherSampleDTO {
    private Long id;
    private String provider;
    private WeatherTargetSource source;
    private double latitude;
    private double longitude;
    private Instant observedAt;
    private Instant fetchedAt;
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
}

package org.github.tess1o.geopulse.insight.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherSampleInsight {
    private Instant observedAt;
    private Double latitude;
    private Double longitude;
    private Integer weatherCode;
    private String condition;
    private Double temperature;
    private Double precipitation;
    private Double snowfall;
    private Double windSpeed;
}

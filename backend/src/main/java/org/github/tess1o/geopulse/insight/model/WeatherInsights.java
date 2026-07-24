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
public class WeatherInsights {
    private long samplesCount;
    private WeatherSampleInsight coldestTemperature;
    private WeatherSampleInsight hottestTemperature;
    private Double averageTemperature;
    private WeatherDailyPrecipitationInsight wettestDay;
    private long rainySamplesCount;
    private long snowySamplesCount;
    private WeatherSampleInsight windiestSample;
    private WeatherConditionInsight dominantCondition;
    private Instant weatherCoverageStart;
    private Instant weatherCoverageEnd;
}

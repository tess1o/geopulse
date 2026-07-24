package org.github.tess1o.geopulse.insight.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherDailyPrecipitationInsight {
    private String date;
    private Double precipitation;
}

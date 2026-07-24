package org.github.tess1o.geopulse.insight.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherConditionInsight {
    private Integer weatherCode;
    private String label;
    private String severity;
    private long samplesCount;
}

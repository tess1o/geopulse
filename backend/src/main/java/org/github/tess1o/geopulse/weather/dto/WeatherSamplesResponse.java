package org.github.tess1o.geopulse.weather.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherSamplesResponse {
    private boolean enabled;
    private boolean configured;
    private String provider;
    private String attributionUrl;
    private Map<String, String> units;
    private List<WeatherSampleDTO> samples;
}

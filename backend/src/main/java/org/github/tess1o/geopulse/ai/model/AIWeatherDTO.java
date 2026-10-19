package org.github.tess1o.geopulse.ai.model;

import org.github.tess1o.geopulse.weather.dto.WeatherSampleDTO;

import java.util.List;
import java.util.Map;

/**
 * Weather recorded along the user's GPS data. When there are more samples than requested they are thinned evenly
 * across the range, so {@code samples} still covers all of it.
 */
public record AIWeatherDTO(
        String provider,
        String attributionUrl,
        Map<String, String> units,
        List<WeatherSampleDTO> samples,
        int totalSamples,
        boolean thinned
) {
}

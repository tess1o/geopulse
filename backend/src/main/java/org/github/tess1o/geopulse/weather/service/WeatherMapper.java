package org.github.tess1o.geopulse.weather.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.weather.dto.WeatherSampleDTO;
import org.github.tess1o.geopulse.weather.model.WeatherSampleEntity;

import java.util.List;

@ApplicationScoped
public class WeatherMapper {

    public List<WeatherSampleDTO> toDtos(List<WeatherSampleEntity> samples) {
        return samples.stream()
                .map(this::toDto)
                .toList();
    }

    public WeatherSampleDTO toDto(WeatherSampleEntity sample) {
        return WeatherSampleDTO.builder()
                .id(sample.getId())
                .provider(sample.getProvider())
                .source(sample.getSource())
                .latitude(sample.getRequestedLatitude())
                .longitude(sample.getRequestedLongitude())
                .observedAt(sample.getObservedAt())
                .fetchedAt(sample.getFetchedAt())
                .weatherCode(sample.getWeatherCode())
                .temperature(sample.getTemperature())
                .apparentTemperature(sample.getApparentTemperature())
                .humidity(sample.getHumidity())
                .precipitation(sample.getPrecipitation())
                .rain(sample.getRain())
                .snowfall(sample.getSnowfall())
                .cloudCover(sample.getCloudCover())
                .windSpeed(sample.getWindSpeed())
                .windGust(sample.getWindGust())
                .windDirection(sample.getWindDirection())
                .pressure(sample.getPressure())
                .build();
    }
}

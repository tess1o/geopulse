package org.github.tess1o.geopulse.export.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WeatherSamplesDataDto {
    private String dataType;
    private Instant exportDate;
    private Instant startDate;
    private Instant endDate;
    private List<WeatherSampleDto> samples;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class WeatherSampleDto {
        private Long id;
        private String provider;
        private String source;
        private Double requestedLatitude;
        private Double requestedLongitude;
        private Double providerLatitude;
        private Double providerLongitude;
        private Double latitudeBucket;
        private Double longitudeBucket;
        private Instant observedAt;
        private Instant fetchedAt;
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
        private Instant createdAt;
        private Instant updatedAt;
    }
}

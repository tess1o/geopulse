package org.github.tess1o.geopulse.weather.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PirateWeatherResponse {
    private Double latitude;
    private Double longitude;
    private String timezone;
    private Double offset;
    private Double elevation;
    private PirateWeatherDataPoint currently;
    private PirateWeatherDataBlock hourly;
    private Map<String, Object> flags;

    @Data
    public static class PirateWeatherDataBlock {
        private String summary;
        private String icon;
        private List<PirateWeatherDataPoint> data;
    }

    @Data
    public static class PirateWeatherDataPoint {
        private Long time;
        private String summary;
        private String icon;
        private Double precipIntensity;
        private Double precipAccumulation;
        private String precipType;
        private Double rainIntensity;
        private Double snowIntensity;
        private Double temperature;
        private Double apparentTemperature;
        private Double humidity;
        private Double pressure;
        private Double windSpeed;
        private Double windGust;
        private Double windBearing;
        private Double cloudCover;
    }
}

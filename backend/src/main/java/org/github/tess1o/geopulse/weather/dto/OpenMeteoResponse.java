package org.github.tess1o.geopulse.weather.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class OpenMeteoResponse {
    private Double latitude;
    private Double longitude;
    private String timezone;

    @JsonProperty("generationtime_ms")
    private Double generationTimeMs;

    @JsonProperty("utc_offset_seconds")
    private Integer utcOffsetSeconds;

    @JsonProperty("current_units")
    private Map<String, String> currentUnits;

    @JsonProperty("hourly_units")
    private Map<String, String> hourlyUnits;

    private OpenMeteoCurrent current;
    private OpenMeteoHourly hourly;

    @Data
    public static class OpenMeteoCurrent {
        private String time;

        @JsonProperty("temperature_2m")
        private Double temperature2m;

        @JsonProperty("apparent_temperature")
        private Double apparentTemperature;

        @JsonProperty("relative_humidity_2m")
        private Double relativeHumidity2m;

        private Double precipitation;
        private Double rain;
        private Double snowfall;

        @JsonProperty("weather_code")
        private Integer weatherCode;

        @JsonProperty("cloud_cover")
        private Double cloudCover;

        @JsonProperty("wind_speed_10m")
        private Double windSpeed10m;

        @JsonProperty("wind_gusts_10m")
        private Double windGusts10m;

        @JsonProperty("wind_direction_10m")
        private Double windDirection10m;

        @JsonProperty("pressure_msl")
        private Double pressureMsl;
    }

    @Data
    public static class OpenMeteoHourly {
        private List<String> time;

        @JsonProperty("temperature_2m")
        private List<Double> temperature2m;

        @JsonProperty("apparent_temperature")
        private List<Double> apparentTemperature;

        @JsonProperty("relative_humidity_2m")
        private List<Double> relativeHumidity2m;

        private List<Double> precipitation;
        private List<Double> rain;
        private List<Double> snowfall;

        @JsonProperty("weather_code")
        private List<Integer> weatherCode;

        @JsonProperty("cloud_cover")
        private List<Double> cloudCover;

        @JsonProperty("wind_speed_10m")
        private List<Double> windSpeed10m;

        @JsonProperty("wind_gusts_10m")
        private List<Double> windGusts10m;

        @JsonProperty("wind_direction_10m")
        private List<Double> windDirection10m;

        @JsonProperty("pressure_msl")
        private List<Double> pressureMsl;
    }
}

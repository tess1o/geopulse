package org.github.tess1o.geopulse.weather.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.github.tess1o.geopulse.weather.dto.OpenMeteoResponse;
import org.github.tess1o.geopulse.weather.dto.WeatherProviderSample;
import org.github.tess1o.geopulse.weather.dto.WeatherTestResponse;
import org.github.tess1o.geopulse.weather.service.WeatherConfigurationService;

import java.io.Closeable;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public class OpenMeteoWeatherClient {

    private static final String WEATHER_VARIABLES = String.join(",",
            "temperature_2m",
            "relative_humidity_2m",
            "apparent_temperature",
            "precipitation",
            "rain",
            "snowfall",
            "weather_code",
            "cloud_cover",
            "wind_speed_10m",
            "wind_gusts_10m",
            "wind_direction_10m",
            "pressure_msl"
    );

    @Inject
    WeatherConfigurationService configurationService;

    @Inject
    ObjectMapper objectMapper;

    public WeatherProviderSample fetchCurrent(double latitude, double longitude) {
        OpenMeteoRestClient client = buildClient(configurationService.forecastUrl());
        try {
            Response response = client.forecast(latitude, longitude, WEATHER_VARIABLES, null, null, null, "UTC", apiKeyOrNull());
            OpenMeteoResponse payload = readPayload(response);
            return fromCurrent(payload, latitude, longitude);
        } finally {
            closeClient(client);
        }
    }

    public WeatherProviderSample fetchHourly(double latitude, double longitude, Instant targetAt) {
        Instant hour = targetAt.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        boolean archive = hour.isBefore(Instant.now().minus(java.time.Duration.ofDays(2)));
        OpenMeteoRestClient client = buildClient(archive ? configurationService.archiveUrl() : configurationService.forecastUrl());
        try {
            OpenMeteoResponse payload;
            if (archive) {
                LocalDate date = LocalDateTime.ofInstant(hour, ZoneOffset.UTC).toLocalDate();
                Response response = client.archive(latitude, longitude, WEATHER_VARIABLES, date.toString(), date.toString(), "UTC", apiKeyOrNull());
                payload = readPayload(response);
            } else {
                String openMeteoHour = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
                        .withZone(ZoneOffset.UTC)
                        .format(hour);
                Response response = client.forecast(latitude, longitude, null, WEATHER_VARIABLES, openMeteoHour, openMeteoHour, "UTC", apiKeyOrNull());
                payload = readPayload(response);
            }
            return fromHourly(payload, latitude, longitude, hour)
                    .orElseThrow(() -> new IllegalStateException("Open-Meteo response did not include target hour " + hour));
        } finally {
            closeClient(client);
        }
    }

    public WeatherTestResponse testConnection() {
        String forecastUrl = configurationService.forecastUrl();
        if (forecastUrl.isBlank()) {
            return WeatherTestResponse.builder()
                    .success(false)
                    .statusCode(0)
                    .provider(WeatherConfigurationService.PROVIDER_OPEN_METEO)
                    .url(forecastUrl)
                    .message("Forecast URL is empty")
                    .build();
        }

        OpenMeteoRestClient client = buildClient(forecastUrl);
        try {
            Response response = client.forecast(51.5074, -0.1278, WEATHER_VARIABLES, null, null, null, "UTC", apiKeyOrNull());
            int status = response.getStatus();
            if (status >= 200 && status < 300) {
                response.close();
                return WeatherTestResponse.builder()
                        .success(true)
                        .statusCode(status)
                        .provider(WeatherConfigurationService.PROVIDER_OPEN_METEO)
                        .url(forecastUrl)
                        .message("Open-Meteo endpoint is reachable")
                        .build();
            }

            String error = safeErrorBody(response);
            return WeatherTestResponse.builder()
                    .success(false)
                    .statusCode(status)
                    .provider(WeatherConfigurationService.PROVIDER_OPEN_METEO)
                    .url(forecastUrl)
                    .message(error.isBlank() ? "Open-Meteo endpoint returned HTTP " + status : error)
                    .build();
        } catch (Exception e) {
            log.warn("Open-Meteo test failed: {}", e.getMessage());
            return WeatherTestResponse.builder()
                    .success(false)
                    .statusCode(0)
                    .provider(WeatherConfigurationService.PROVIDER_OPEN_METEO)
                    .url(forecastUrl)
                    .message(e.getMessage())
                    .build();
        } finally {
            closeClient(client);
        }
    }

    private OpenMeteoRestClient buildClient(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Open-Meteo URL is not configured");
        }
        return RestClientBuilder.newBuilder()
                .baseUri(URI.create(url))
                .build(OpenMeteoRestClient.class);
    }

    private OpenMeteoResponse readPayload(Response response) {
        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            String body = safeErrorBody(response);
            throw new WebApplicationException(
                    body.isBlank() ? "Open-Meteo returned HTTP " + status : body,
                    status
            );
        }
        try {
            return response.readEntity(OpenMeteoResponse.class);
        } finally {
            response.close();
        }
    }

    private WeatherProviderSample fromCurrent(OpenMeteoResponse payload, double requestedLatitude, double requestedLongitude) {
        OpenMeteoResponse.OpenMeteoCurrent current = payload.getCurrent();
        if (current == null) {
            throw new IllegalStateException("Open-Meteo response did not include current weather");
        }

        return WeatherProviderSample.builder()
                .requestedLatitude(requestedLatitude)
                .requestedLongitude(requestedLongitude)
                .providerLatitude(payload.getLatitude())
                .providerLongitude(payload.getLongitude())
                .observedAt(parseOpenMeteoTime(current.getTime()))
                .timezone(payload.getTimezone())
                .weatherCode(current.getWeatherCode())
                .temperature(current.getTemperature2m())
                .apparentTemperature(current.getApparentTemperature())
                .humidity(current.getRelativeHumidity2m())
                .precipitation(current.getPrecipitation())
                .rain(current.getRain())
                .snowfall(current.getSnowfall())
                .cloudCover(current.getCloudCover())
                .windSpeed(current.getWindSpeed10m())
                .windGust(current.getWindGusts10m())
                .windDirection(current.getWindDirection10m())
                .pressure(current.getPressureMsl())
                .rawData(rawData(payload))
                .build();
    }

    private Optional<WeatherProviderSample> fromHourly(OpenMeteoResponse payload, double requestedLatitude, double requestedLongitude, Instant targetAt) {
        OpenMeteoResponse.OpenMeteoHourly hourly = payload.getHourly();
        if (hourly == null || hourly.getTime() == null) {
            return Optional.empty();
        }

        for (int i = 0; i < hourly.getTime().size(); i++) {
            Instant observedAt = parseOpenMeteoTime(hourly.getTime().get(i));
            if (!observedAt.equals(targetAt)) {
                continue;
            }

            return Optional.of(WeatherProviderSample.builder()
                    .requestedLatitude(requestedLatitude)
                    .requestedLongitude(requestedLongitude)
                    .providerLatitude(payload.getLatitude())
                    .providerLongitude(payload.getLongitude())
                    .observedAt(observedAt)
                    .timezone(payload.getTimezone())
                    .weatherCode(valueAt(hourly.getWeatherCode(), i))
                    .temperature(valueAt(hourly.getTemperature2m(), i))
                    .apparentTemperature(valueAt(hourly.getApparentTemperature(), i))
                    .humidity(valueAt(hourly.getRelativeHumidity2m(), i))
                    .precipitation(valueAt(hourly.getPrecipitation(), i))
                    .rain(valueAt(hourly.getRain(), i))
                    .snowfall(valueAt(hourly.getSnowfall(), i))
                    .cloudCover(valueAt(hourly.getCloudCover(), i))
                    .windSpeed(valueAt(hourly.getWindSpeed10m(), i))
                    .windGust(valueAt(hourly.getWindGusts10m(), i))
                    .windDirection(valueAt(hourly.getWindDirection10m(), i))
                    .pressure(valueAt(hourly.getPressureMsl(), i))
                    .rawData(rawData(payload))
                    .build());
        }

        return Optional.empty();
    }

    private <T> T valueAt(java.util.List<T> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    private Instant parseOpenMeteoTime(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now().truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        }
        return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
    }

    private Map<String, Object> rawData(OpenMeteoResponse payload) {
        return objectMapper.convertValue(payload, new TypeReference<>() {
        });
    }

    private String apiKeyOrNull() {
        String apiKey = configurationService.apiKey();
        return apiKey.isBlank() ? null : apiKey;
    }

    private String safeErrorBody(Response response) {
        try {
            String body = response.readEntity(String.class);
            response.close();
            return body == null ? "" : body;
        } catch (Exception e) {
            return "";
        }
    }

    private void closeClient(OpenMeteoRestClient client) {
        if (client instanceof Closeable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.debug("Failed to close Open-Meteo REST client", e);
            }
        }
    }
}

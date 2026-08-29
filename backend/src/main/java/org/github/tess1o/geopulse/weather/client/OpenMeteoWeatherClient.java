package org.github.tess1o.geopulse.weather.client;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.github.tess1o.geopulse.weather.dto.OpenMeteoResponse;
import org.github.tess1o.geopulse.weather.dto.WeatherEndpointTestResponse;
import org.github.tess1o.geopulse.weather.dto.WeatherProviderSample;
import org.github.tess1o.geopulse.weather.dto.WeatherTestResponse;
import org.github.tess1o.geopulse.weather.service.WeatherConfigurationService;

import java.io.Closeable;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

@ApplicationScoped
@Slf4j
public class OpenMeteoWeatherClient implements WeatherProviderClient {

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

    @ConfigProperty(name = "geopulse.weather.open-meteo.connect-timeout-seconds", defaultValue = "5")
    long connectTimeoutSeconds;

    @ConfigProperty(name = "geopulse.weather.open-meteo.read-timeout-seconds", defaultValue = "15")
    long readTimeoutSeconds;

    private final Map<String, OpenMeteoRestClient> clients = new ConcurrentHashMap<>();

    @Override
    public String providerKey() {
        return WeatherConfigurationService.PROVIDER_OPEN_METEO;
    }

    public WeatherProviderSample fetchCurrent(double latitude, double longitude) {
        String forecastUrl = configurationService.forecastUrl();
        OpenMeteoRestClient client = null;
        try {
            client = buildClient(forecastUrl);
            Response response = client.forecast(latitude, longitude, WEATHER_VARIABLES, null, null, null, "UTC", apiKeyOrNull());
            OpenMeteoResponse payload = readPayload(response);
            return fromCurrent(payload, latitude, longitude);
        } catch (WeatherProviderException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new WeatherProviderException(WeatherProviderErrorKind.CONFIG_ERROR, "Open-Meteo URL is invalid: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new WeatherProviderException(
                    WeatherProviderErrorKind.PROVIDER_UNAVAILABLE,
                    failureMessage("forecast current weather", forecastUrl, e),
                    e);
        }
    }

    public WeatherProviderSample fetchHourly(double latitude, double longitude, Instant targetAt) {
        Instant hour = targetAt.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        WeatherProviderSample sample = fetchHourlyBatch(latitude, longitude, List.of(hour)).get(hour);
        if (sample == null) {
            throw new WeatherProviderException(
                    WeatherProviderErrorKind.NO_DATA,
                    "Open-Meteo response did not include target hour " + hour);
        }
        return sample;
    }

    @Override
    public Map<Instant, WeatherProviderSample> fetchHourlyBatch(
            double latitude,
            double longitude,
            List<Instant> targetHours) {
        if (targetHours == null || targetHours.isEmpty()) {
            return Map.of();
        }
        List<Instant> hours = targetHours.stream()
                .filter(java.util.Objects::nonNull)
                .map(value -> value.truncatedTo(java.time.temporal.ChronoUnit.HOURS))
                .distinct()
                .sorted()
                .toList();
        if (hours.isEmpty()) {
            return Map.of();
        }
        Instant firstHour = hours.getFirst();
        Instant lastHour = hours.getLast();
        boolean archive = lastHour.isBefore(Instant.now().minus(java.time.Duration.ofDays(2)));
        String baseUrl = archive ? configurationService.archiveUrl() : configurationService.forecastUrl();
        String endpoint = archive ? "archive hourly weather" : "forecast hourly weather";
        OpenMeteoRestClient client = null;
        try {
            client = buildClient(baseUrl);
            OpenMeteoResponse payload;
            if (archive) {
                LocalDate startDate = LocalDateTime.ofInstant(firstHour, ZoneOffset.UTC).toLocalDate();
                LocalDate endDate = LocalDateTime.ofInstant(lastHour, ZoneOffset.UTC).toLocalDate();
                Response response = client.archive(latitude, longitude, WEATHER_VARIABLES, startDate.toString(), endDate.toString(), "UTC", apiKeyOrNull());
                payload = readPayload(response);
            } else {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(ZoneOffset.UTC);
                Response response = client.forecast(latitude, longitude, null, WEATHER_VARIABLES,
                        formatter.format(firstHour), formatter.format(lastHour), "UTC", apiKeyOrNull());
                payload = readPayload(response);
            }
            return fromHourlyBatch(payload, latitude, longitude, new java.util.HashSet<>(hours));
        } catch (WeatherProviderException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new WeatherProviderException(WeatherProviderErrorKind.CONFIG_ERROR, "Open-Meteo URL is invalid: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new WeatherProviderException(
                    WeatherProviderErrorKind.PROVIDER_UNAVAILABLE,
                    failureMessage(endpoint, baseUrl, e),
                    e);
        }
    }

    @Override
    public WeatherTestResponse testConnection(BooleanSupplier beforeExternalCall) {
        String forecastUrl = configurationService.forecastUrl();
        WeatherEndpointTestResponse forecast = testForecastEndpoint(forecastUrl, beforeExternalCall);
        WeatherEndpointTestResponse archive = testArchiveEndpoint(configurationService.archiveUrl(), beforeExternalCall);
        boolean success = forecast.isSuccess() && archive.isSuccess();
        WeatherEndpointTestResponse failedEndpoint = !forecast.isSuccess() ? forecast : archive;
        String message = success
                ? "Open-Meteo forecast and archive endpoints are reachable"
                : "Open-Meteo " + (!forecast.isSuccess() ? "forecast" : "archive")
                + " endpoint failed: " + failedEndpoint.getMessage();
        int statusCode = success ? forecast.getStatusCode() : failedEndpoint.getStatusCode();
        String url = success ? forecast.getUrl() : failedEndpoint.getUrl();

        return WeatherTestResponse.builder()
                .success(success)
                .statusCode(statusCode)
                .provider(WeatherConfigurationService.PROVIDER_OPEN_METEO)
                .url(url)
                .message(message)
                .forecast(forecast)
                .archive(archive)
                .build();
    }

    private WeatherEndpointTestResponse testForecastEndpoint(String forecastUrl, BooleanSupplier beforeExternalCall) {
        if (forecastUrl == null || forecastUrl.isBlank()) {
            return WeatherEndpointTestResponse.builder()
                    .success(false)
                    .statusCode(0)
                    .url(forecastUrl)
                    .message("Forecast URL is empty")
                    .build();
        }

        OpenMeteoRestClient client = null;
        try {
            client = buildClient(forecastUrl);
            if (!beforeExternalCall.getAsBoolean()) {
                return quotaExceededTestResponse(forecastUrl);
            }
            Response response = client.forecast(51.5074, -0.1278, WEATHER_VARIABLES, null, null, null, "UTC", apiKeyOrNull());
            return endpointTestResponse("forecast", forecastUrl, response);
        } catch (Exception e) {
            log.error("Open-Meteo forecast test failed for {}: {}", forecastUrl, rootCauseMessage(e), e);
            return WeatherEndpointTestResponse.builder()
                    .success(false)
                    .statusCode(0)
                    .url(forecastUrl)
                    .message(rootCauseMessage(e))
                    .build();
        }
    }

    private WeatherEndpointTestResponse testArchiveEndpoint(String archiveUrl, BooleanSupplier beforeExternalCall) {
        if (archiveUrl == null || archiveUrl.isBlank()) {
            return WeatherEndpointTestResponse.builder()
                    .success(false)
                    .statusCode(0)
                    .url(archiveUrl)
                    .message("Archive URL is empty")
                    .build();
        }

        OpenMeteoRestClient client = null;
        try {
            client = buildClient(archiveUrl);
            if (!beforeExternalCall.getAsBoolean()) {
                return quotaExceededTestResponse(archiveUrl);
            }
            LocalDate date = LocalDate.now(ZoneOffset.UTC).minusDays(5);
            Response response = client.archive(51.5074, -0.1278, WEATHER_VARIABLES, date.toString(), date.toString(), "UTC", apiKeyOrNull());
            return endpointTestResponse("archive", archiveUrl, response);
        } catch (Exception e) {
            log.error("Open-Meteo archive test failed for {}: {}", archiveUrl, rootCauseMessage(e), e);
            return WeatherEndpointTestResponse.builder()
                    .success(false)
                    .statusCode(0)
                    .url(archiveUrl)
                    .message(rootCauseMessage(e))
                    .build();
        }
    }

    private WeatherEndpointTestResponse quotaExceededTestResponse(String url) {
        return WeatherEndpointTestResponse.builder()
                .success(false)
                .statusCode(429)
                .url(url)
                .message("Daily weather request limit exhausted")
                .build();
    }

    private WeatherEndpointTestResponse endpointTestResponse(String endpoint, String url, Response response) {
        int status = response.getStatus();
        if (status >= 200 && status < 300) {
            response.close();
            return WeatherEndpointTestResponse.builder()
                    .success(true)
                    .statusCode(status)
                    .url(url)
                    .message("Open-Meteo " + endpoint + " endpoint is reachable")
                    .build();
        }

        String error = safeErrorBody(response);
        return WeatherEndpointTestResponse.builder()
                .success(false)
                .statusCode(status)
                .url(url)
                .message(error.isBlank() ? "Open-Meteo " + endpoint + " endpoint returned HTTP " + status : error)
                .build();
    }

    private synchronized OpenMeteoRestClient buildClient(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Open-Meteo URL is not configured");
        }
        int connectTimeout = Math.max(1, configurationService.openMeteoConnectTimeoutSeconds());
        int readTimeout = Math.max(1, configurationService.openMeteoReadTimeoutSeconds());
        java.util.Set<String> activeUrls = new java.util.HashSet<>(java.util.List.of(
                clientCacheKey(configurationService.forecastUrl(), connectTimeout, readTimeout),
                clientCacheKey(configurationService.archiveUrl(), connectTimeout, readTimeout)));
        clients.entrySet().removeIf(entry -> {
            if (activeUrls.contains(entry.getKey())) {
                return false;
            }
            closeClient(entry.getValue());
            return true;
        });
        return clients.computeIfAbsent(clientCacheKey(url, connectTimeout, readTimeout), key -> RestClientBuilder.newBuilder()
                .baseUri(URI.create(url))
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .build(OpenMeteoRestClient.class));
    }

    private String clientCacheKey(String url, int connectTimeout, int readTimeout) {
        return url + "|" + connectTimeout + "|" + readTimeout;
    }

    private OpenMeteoResponse readPayload(Response response) {
        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            Instant retryAfter = retryAfter(response);
            String body = safeErrorBody(response);
            throw classifyHttpError(status, body, retryAfter);
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
            throw new WeatherProviderException(WeatherProviderErrorKind.INVALID_RESPONSE, "Open-Meteo response did not include current weather");
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
                .rawData(null)
                .build();
    }

    private Optional<WeatherProviderSample> fromHourly(OpenMeteoResponse payload, double requestedLatitude, double requestedLongitude, Instant targetAt) {
        return Optional.ofNullable(fromHourlyBatch(payload, requestedLatitude, requestedLongitude, java.util.Set.of(targetAt)).get(targetAt));
    }

    private Map<Instant, WeatherProviderSample> fromHourlyBatch(
            OpenMeteoResponse payload,
            double requestedLatitude,
            double requestedLongitude,
            java.util.Set<Instant> targetHours) {
        OpenMeteoResponse.OpenMeteoHourly hourly = payload.getHourly();
        if (hourly == null || hourly.getTime() == null) {
            return Map.of();
        }

        Map<Instant, WeatherProviderSample> result = new LinkedHashMap<>();
        for (int i = 0; i < hourly.getTime().size(); i++) {
            Instant observedAt = parseOpenMeteoTime(hourly.getTime().get(i));
            if (!targetHours.contains(observedAt)) {
                continue;
            }

            result.put(observedAt, WeatherProviderSample.builder()
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
                    .rawData(null)
                    .build());
        }

        return result;
    }

    private <T> T valueAt(java.util.List<T> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    private Instant parseOpenMeteoTime(String value) {
        if (value == null || value.isBlank()) {
            throw new WeatherProviderException(WeatherProviderErrorKind.INVALID_RESPONSE, "Open-Meteo response did not include an observation time");
        }
        try {
            return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new WeatherProviderException(WeatherProviderErrorKind.INVALID_RESPONSE, "Open-Meteo response included invalid time: " + value, e);
        }
    }

    private String apiKeyOrNull() {
        String apiKey = configurationService.apiKey();
        return apiKey.isBlank() ? null : apiKey;
    }

    private String failureMessage(String operation, String baseUrl, RuntimeException e) {
        return "Open-Meteo " + operation + " request failed for " + baseUrl + ": " + rootCauseMessage(e);
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getMessage();
        }
        return root.getClass().getName() + (message == null || message.isBlank() ? "" : ": " + message);
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

    private WeatherProviderException classifyHttpError(int status, String body, Instant retryAfter) {
        String message = body == null || body.isBlank() ? "Open-Meteo returned HTTP " + status : body;
        String normalized = message.toLowerCase();
        if (status == 429 || containsQuotaText(normalized)) {
            return new WeatherProviderException(WeatherProviderErrorKind.QUOTA_EXCEEDED, status, retryAfter, message);
        }
        if (status == 401 || status == 403) {
            return new WeatherProviderException(WeatherProviderErrorKind.CONFIG_ERROR, status, retryAfter, message);
        }
        if (status == 400 || status == 404) {
            return new WeatherProviderException(WeatherProviderErrorKind.CONFIG_ERROR, status, retryAfter, message);
        }
        if (status >= 500) {
            return new WeatherProviderException(WeatherProviderErrorKind.PROVIDER_UNAVAILABLE, status, retryAfter, message);
        }
        if (normalized.contains("no data") || normalized.contains("not available")) {
            return new WeatherProviderException(WeatherProviderErrorKind.NO_DATA, status, retryAfter, message);
        }
        return new WeatherProviderException(WeatherProviderErrorKind.INVALID_RESPONSE, status, retryAfter, message);
    }

    private boolean containsQuotaText(String value) {
        return value.contains("quota")
                || value.contains("rate limit")
                || value.contains("rate-limit")
                || value.contains("too many requests");
    }

    private Instant retryAfter(Response response) {
        String value = response.getHeaderString("Retry-After");
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            long seconds = Long.parseLong(trimmed);
            return Instant.now().plusSeconds(Math.max(0, seconds));
        } catch (NumberFormatException ignored) {
            try {
                return ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
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

    @PreDestroy
    void closeClients() {
        clients.values().forEach(this::closeClient);
        clients.clear();
    }
}

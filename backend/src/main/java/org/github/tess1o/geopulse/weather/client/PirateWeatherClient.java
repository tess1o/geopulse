package org.github.tess1o.geopulse.weather.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.github.tess1o.geopulse.weather.dto.PirateWeatherResponse;
import org.github.tess1o.geopulse.weather.dto.WeatherEndpointTestResponse;
import org.github.tess1o.geopulse.weather.dto.WeatherProviderSample;
import org.github.tess1o.geopulse.weather.dto.WeatherTestResponse;
import org.github.tess1o.geopulse.weather.service.WeatherConfigurationService;

import java.io.Closeable;
import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@Slf4j
public class PirateWeatherClient implements WeatherProviderClient {

    private static final String UNITS = "ca";
    private static final String CURRENT_EXCLUDE = "minutely,hourly,daily,alerts";
    private static final String HOURLY_EXCLUDE = "currently,minutely,daily,alerts";
    private static final long HOURLY_MATCH_TOLERANCE_SECONDS = 60 * 60;

    @Inject
    WeatherConfigurationService configurationService;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "geopulse.weather.pirate.connect-timeout-seconds", defaultValue = "5")
    long connectTimeoutSeconds;

    @ConfigProperty(name = "geopulse.weather.pirate.read-timeout-seconds", defaultValue = "15")
    long readTimeoutSeconds;

    @Override
    public String providerKey() {
        return WeatherConfigurationService.PROVIDER_PIRATE_WEATHER;
    }

    @Override
    public WeatherProviderSample fetchCurrent(double latitude, double longitude) {
        requireApiKey();
        String baseUrl = configurationService.pirateBaseUrl();
        PirateWeatherRestClient client = null;
        try {
            client = buildClient(baseUrl);
            Response response = client.forecast(configurationService.pirateApiKey(), latitude, longitude, UNITS, CURRENT_EXCLUDE);
            PirateWeatherResponse payload = readPayload(response);
            PirateWeatherResponse.PirateWeatherDataPoint current = payload.getCurrently();
            if (current == null) {
                throw new WeatherProviderException(WeatherProviderErrorKind.INVALID_RESPONSE,
                        "Pirate Weather response did not include current weather");
            }
            return fromDataPoint(payload, current, latitude, longitude);
        } catch (WeatherProviderException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new WeatherProviderException(WeatherProviderErrorKind.CONFIG_ERROR, "Pirate Weather URL is invalid: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new WeatherProviderException(
                    WeatherProviderErrorKind.PROVIDER_UNAVAILABLE,
                    failureMessage("current weather", baseUrl, e),
                    e);
        } finally {
            closeClient(client);
        }
    }

    @Override
    public WeatherProviderSample fetchHourly(double latitude, double longitude, Instant targetAt) {
        requireApiKey();
        Instant hour = targetAt.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        String baseUrl = configurationService.pirateTimeMachineUrl();
        PirateWeatherRestClient client = null;
        try {
            client = buildClient(baseUrl);
            Response response = client.timeMachine(
                    configurationService.pirateApiKey(),
                    latitude,
                    longitude,
                    hour.getEpochSecond(),
                    UNITS,
                    HOURLY_EXCLUDE);
            PirateWeatherResponse payload = readPayload(response);
            PirateWeatherResponse.PirateWeatherDataPoint hourly = matchingHour(payload, hour)
                    .orElseThrow(() -> new WeatherProviderException(
                            WeatherProviderErrorKind.NO_DATA,
                            "Pirate Weather response did not include target hour " + hour));
            return fromDataPoint(payload, hourly, latitude, longitude);
        } catch (WeatherProviderException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new WeatherProviderException(WeatherProviderErrorKind.CONFIG_ERROR, "Pirate Weather URL is invalid: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new WeatherProviderException(
                    WeatherProviderErrorKind.PROVIDER_UNAVAILABLE,
                    failureMessage("hourly weather", baseUrl, e),
                    e);
        } finally {
            closeClient(client);
        }
    }

    @Override
    public WeatherTestResponse testConnection() {
        String forecastUrl = configurationService.pirateBaseUrl();
        String timeMachineUrl = configurationService.pirateTimeMachineUrl();
        WeatherEndpointTestResponse forecast = testForecastEndpoint(forecastUrl);
        WeatherEndpointTestResponse archive = testArchiveEndpoint(timeMachineUrl);
        boolean success = forecast.isSuccess() && archive.isSuccess();
        WeatherEndpointTestResponse failedEndpoint = !forecast.isSuccess() ? forecast : archive;
        String message = success
                ? "Pirate Weather forecast and time machine endpoints are reachable"
                : "Pirate Weather " + (!forecast.isSuccess() ? "forecast" : "time machine")
                + " endpoint failed: " + failedEndpoint.getMessage();
        int statusCode = success ? forecast.getStatusCode() : failedEndpoint.getStatusCode();
        String url = success ? forecast.getUrl() : failedEndpoint.getUrl();

        return WeatherTestResponse.builder()
                .success(success)
                .statusCode(statusCode)
                .provider(providerKey())
                .url(url)
                .message(message)
                .forecast(forecast)
                .archive(archive)
                .build();
    }

    private WeatherEndpointTestResponse testForecastEndpoint(String baseUrl) {
        String configError = providerConfigurationError(baseUrl);
        if (configError != null) {
            return WeatherEndpointTestResponse.builder()
                    .success(false)
                    .statusCode(0)
                    .url(baseUrl)
                    .message(configError)
                    .build();
        }

        PirateWeatherRestClient client = null;
        try {
            client = buildClient(baseUrl);
            Response response = client.forecast(configurationService.pirateApiKey(), 51.5074, -0.1278, UNITS, CURRENT_EXCLUDE);
            return endpointTestResponse("forecast", baseUrl, response);
        } catch (Exception e) {
            log.error("Pirate Weather forecast test failed for {}: {}", baseUrl, rootCauseMessage(e), e);
            return WeatherEndpointTestResponse.builder()
                    .success(false)
                    .statusCode(0)
                    .url(baseUrl)
                    .message(rootCauseMessage(e))
                    .build();
        } finally {
            closeClient(client);
        }
    }

    private WeatherEndpointTestResponse testArchiveEndpoint(String baseUrl) {
        String configError = providerConfigurationError(baseUrl);
        if (configError != null) {
            return WeatherEndpointTestResponse.builder()
                    .success(false)
                    .statusCode(0)
                    .url(baseUrl)
                    .message(configError)
                    .build();
        }

        PirateWeatherRestClient client = null;
        try {
            client = buildClient(baseUrl);
            Instant testHour = Instant.now().minus(java.time.Duration.ofDays(5))
                    .truncatedTo(java.time.temporal.ChronoUnit.HOURS);
            Response response = client.timeMachine(
                    configurationService.pirateApiKey(),
                    51.5074,
                    -0.1278,
                    testHour.getEpochSecond(),
                    UNITS,
                    HOURLY_EXCLUDE);
            return endpointTestResponse("time machine", baseUrl, response);
        } catch (Exception e) {
            log.error("Pirate Weather time machine test failed for {}: {}", baseUrl, rootCauseMessage(e), e);
            return WeatherEndpointTestResponse.builder()
                    .success(false)
                    .statusCode(0)
                    .url(baseUrl)
                    .message(rootCauseMessage(e))
                    .build();
        } finally {
            closeClient(client);
        }
    }

    private String providerConfigurationError(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "Pirate Weather URL is empty";
        }
        if (configurationService.pirateApiKey().isBlank()) {
            return "Pirate Weather API key is empty";
        }
        return null;
    }

    private void requireApiKey() {
        if (configurationService.pirateApiKey().isBlank()) {
            throw new WeatherProviderException(WeatherProviderErrorKind.CONFIG_ERROR, "Pirate Weather API key is not configured");
        }
    }

    private PirateWeatherRestClient buildClient(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Pirate Weather URL is not configured");
        }
        return RestClientBuilder.newBuilder()
                .baseUri(URI.create(url))
                .connectTimeout(Math.max(1, connectTimeoutSeconds), TimeUnit.SECONDS)
                .readTimeout(Math.max(1, readTimeoutSeconds), TimeUnit.SECONDS)
                .build(PirateWeatherRestClient.class);
    }

    private PirateWeatherResponse readPayload(Response response) {
        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            Instant retryAfter = retryAfter(response);
            String body = safeErrorBody(response);
            throw classifyHttpError(status, body, retryAfter);
        }
        try {
            return response.readEntity(PirateWeatherResponse.class);
        } finally {
            response.close();
        }
    }

    private WeatherEndpointTestResponse endpointTestResponse(String endpoint, String url, Response response) {
        int status = response.getStatus();
        if (status >= 200 && status < 300) {
            response.close();
            return WeatherEndpointTestResponse.builder()
                    .success(true)
                    .statusCode(status)
                    .url(url)
                    .message("Pirate Weather " + endpoint + " endpoint is reachable")
                    .build();
        }

        String error = safeErrorBody(response);
        return WeatherEndpointTestResponse.builder()
                .success(false)
                .statusCode(status)
                .url(url)
                .message(error.isBlank() ? "Pirate Weather " + endpoint + " endpoint returned HTTP " + status : error)
                .build();
    }

    private Optional<PirateWeatherResponse.PirateWeatherDataPoint> matchingHour(PirateWeatherResponse payload, Instant targetAt) {
        List<PirateWeatherResponse.PirateWeatherDataPoint> points = payload.getHourly() == null
                ? List.of()
                : payload.getHourly().getData();
        if (points == null || points.isEmpty()) {
            return Optional.empty();
        }

        return points.stream()
                .filter(point -> point.getTime() != null)
                .min(Comparator.comparingLong(point -> Math.abs(point.getTime() - targetAt.getEpochSecond())))
                .filter(point -> Math.abs(point.getTime() - targetAt.getEpochSecond()) <= HOURLY_MATCH_TOLERANCE_SECONDS);
    }

    private WeatherProviderSample fromDataPoint(PirateWeatherResponse payload,
                                                PirateWeatherResponse.PirateWeatherDataPoint point,
                                                double requestedLatitude,
                                                double requestedLongitude) {
        if (point.getTime() == null) {
            throw new WeatherProviderException(WeatherProviderErrorKind.INVALID_RESPONSE,
                    "Pirate Weather response did not include an observation time");
        }

        return WeatherProviderSample.builder()
                .requestedLatitude(requestedLatitude)
                .requestedLongitude(requestedLongitude)
                .providerLatitude(payload.getLatitude())
                .providerLongitude(payload.getLongitude())
                .observedAt(Instant.ofEpochSecond(point.getTime()))
                .timezone(payload.getTimezone())
                .weatherCode(weatherCode(point))
                .temperature(point.getTemperature())
                .apparentTemperature(point.getApparentTemperature())
                .humidity(percent(point.getHumidity()))
                .precipitation(point.getPrecipIntensity())
                .rain(firstNonNull(point.getRainIntensity(), rainFromPrecip(point)))
                .snowfall(firstNonNull(point.getSnowIntensity(), snowFromPrecip(point)))
                .cloudCover(percent(point.getCloudCover()))
                .windSpeed(point.getWindSpeed())
                .windGust(point.getWindGust())
                .windDirection(point.getWindBearing())
                .pressure(point.getPressure())
                .rawData(rawData(payload))
                .build();
    }

    private Integer weatherCode(PirateWeatherResponse.PirateWeatherDataPoint point) {
        String icon = normalize(point.getIcon());
        String precipType = normalize(point.getPrecipType());
        if (icon.contains("thunderstorm")) {
            return 95;
        }
        if (icon.contains("sleet") || "sleet".equals(precipType)) {
            return 67;
        }
        if (icon.contains("snow") || "snow".equals(precipType)) {
            return 71;
        }
        if (icon.contains("rain") || "rain".equals(precipType) || icon.contains("precipitation")) {
            return icon.contains("drizzle") || icon.contains("light-rain") ? 51 : 61;
        }
        if (icon.contains("fog") || icon.contains("mist") || icon.contains("haze") || icon.contains("smoke")) {
            return 45;
        }
        if (icon.contains("cloudy")) {
            return icon.contains("partly") || icon.contains("mostly") ? 2 : 3;
        }
        if (icon.contains("clear")) {
            return 0;
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private Double percent(Double value) {
        return value == null ? null : value * 100.0;
    }

    private Double rainFromPrecip(PirateWeatherResponse.PirateWeatherDataPoint point) {
        return "rain".equals(normalize(point.getPrecipType())) ? point.getPrecipIntensity() : null;
    }

    private Double snowFromPrecip(PirateWeatherResponse.PirateWeatherDataPoint point) {
        return "snow".equals(normalize(point.getPrecipType())) ? point.getPrecipIntensity() : null;
    }

    private Double firstNonNull(Double first, Double second) {
        return first != null ? first : second;
    }

    private Map<String, Object> rawData(PirateWeatherResponse payload) {
        return objectMapper.convertValue(payload, new TypeReference<>() {
        });
    }

    private String failureMessage(String operation, String baseUrl, RuntimeException e) {
        return "Pirate Weather " + operation + " request failed for " + baseUrl + ": " + rootCauseMessage(e);
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
        String message = body == null || body.isBlank() ? "Pirate Weather returned HTTP " + status : body;
        String normalized = message.toLowerCase(Locale.ROOT);
        if (status == 429 || containsQuotaText(normalized)) {
            return new WeatherProviderException(WeatherProviderErrorKind.QUOTA_EXCEEDED, status, retryAfter, message);
        }
        if (status == 400 || status == 401 || status == 403 || status == 404) {
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
            value = response.getHeaderString("Ratelimit-Reset");
        }
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

    private void closeClient(PirateWeatherRestClient client) {
        if (client instanceof Closeable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.debug("Failed to close Pirate Weather REST client", e);
            }
        }
    }
}

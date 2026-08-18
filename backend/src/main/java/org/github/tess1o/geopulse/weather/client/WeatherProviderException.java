package org.github.tess1o.geopulse.weather.client;

import lombok.Getter;

import java.time.Instant;

@Getter
public class WeatherProviderException extends RuntimeException {

    private final WeatherProviderErrorKind kind;
    private final int statusCode;
    private final Instant retryAfter;
    private final String providerKey;

    public WeatherProviderException(WeatherProviderErrorKind kind, String message) {
        this(kind, 0, null, message, null);
    }

    public WeatherProviderException(WeatherProviderErrorKind kind, int statusCode, Instant retryAfter, String message) {
        this(kind, statusCode, retryAfter, message, null);
    }

    public WeatherProviderException(WeatherProviderErrorKind kind, String message, Throwable cause) {
        this(kind, 0, null, message, cause);
    }

    public WeatherProviderException(WeatherProviderErrorKind kind, int statusCode, Instant retryAfter, String message, Throwable cause) {
        this(kind, statusCode, retryAfter, message, cause, null);
    }

    public WeatherProviderException(WeatherProviderErrorKind kind, int statusCode, Instant retryAfter, String message, Throwable cause, String providerKey) {
        super(message, cause);
        this.kind = kind;
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
        this.providerKey = providerKey;
    }
}

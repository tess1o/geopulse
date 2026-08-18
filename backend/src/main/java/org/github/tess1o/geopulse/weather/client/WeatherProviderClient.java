package org.github.tess1o.geopulse.weather.client;

import org.github.tess1o.geopulse.weather.dto.WeatherProviderSample;
import org.github.tess1o.geopulse.weather.dto.WeatherTestResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

public interface WeatherProviderClient {

    String providerKey();

    WeatherProviderSample fetchCurrent(double latitude, double longitude);

    WeatherProviderSample fetchHourly(double latitude, double longitude, Instant targetAt);

    /**
     * Fetches several target hours using one provider request. Returned keys are UTC hours.
     */
    Map<Instant, WeatherProviderSample> fetchHourlyBatch(
            double latitude,
            double longitude,
            List<Instant> targetHours);

    WeatherTestResponse testConnection(BooleanSupplier beforeExternalCall);

    default WeatherTestResponse testConnection() {
        return testConnection(() -> true);
    }
}

package org.github.tess1o.geopulse.weather.client;

import org.github.tess1o.geopulse.weather.dto.WeatherProviderSample;
import org.github.tess1o.geopulse.weather.dto.WeatherTestResponse;

import java.time.Instant;

public interface WeatherProviderClient {

    String providerKey();

    WeatherProviderSample fetchCurrent(double latitude, double longitude);

    WeatherProviderSample fetchHourly(double latitude, double longitude, Instant targetAt);

    WeatherTestResponse testConnection();
}

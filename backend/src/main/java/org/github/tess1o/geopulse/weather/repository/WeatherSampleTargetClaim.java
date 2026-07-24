package org.github.tess1o.geopulse.weather.repository;

import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;

import java.time.Instant;
import java.util.UUID;

public record WeatherSampleTargetClaim(
        Long id,
        UUID userId,
        String provider,
        double latitude,
        double longitude,
        double latitudeBucket,
        double longitudeBucket,
        Instant targetAt,
        WeatherTargetSource source
) {
}

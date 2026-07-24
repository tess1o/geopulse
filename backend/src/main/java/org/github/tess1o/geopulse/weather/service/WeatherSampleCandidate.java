package org.github.tess1o.geopulse.weather.service;

import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;

import java.time.Instant;

public record WeatherSampleCandidate(
        double latitude,
        double longitude,
        Instant targetAt,
        WeatherTargetSource source,
        int priority
) {
}

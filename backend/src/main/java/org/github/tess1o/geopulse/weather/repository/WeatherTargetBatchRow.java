package org.github.tess1o.geopulse.weather.repository;

import java.time.Instant;

public record WeatherTargetBatchRow(
        double latitude,
        double longitude,
        double latitudeBucket,
        double longitudeBucket,
        Instant targetAt
) {
}

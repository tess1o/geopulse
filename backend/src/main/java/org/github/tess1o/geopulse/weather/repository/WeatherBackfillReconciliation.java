package org.github.tess1o.geopulse.weather.repository;

import java.time.Instant;
import java.util.UUID;

public record WeatherBackfillReconciliation(
        UUID userId,
        Instant chunkStart,
        Instant chunkEnd,
        Instant rangeEnd
) {
}

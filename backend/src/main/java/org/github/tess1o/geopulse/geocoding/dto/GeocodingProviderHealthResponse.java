package org.github.tess1o.geopulse.geocoding.dto;

import java.time.Instant;

public record GeocodingProviderHealthResponse(
        String name,
        String displayName,
        boolean primary,
        boolean fallback,
        Status status,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        String lastErrorMessage,
        Instant circuitBreakerObservedOpenAt
) {
    public enum Status { HEALTHY, DEGRADED, CIRCUIT_OPEN, UNKNOWN }
}

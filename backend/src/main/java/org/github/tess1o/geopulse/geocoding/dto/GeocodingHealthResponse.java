package org.github.tess1o.geopulse.geocoding.dto;

import java.util.List;

public record GeocodingHealthResponse(Status status, List<GeocodingProviderHealthResponse> providers) {
    public enum Status { HEALTHY, DEGRADED, CIRCUIT_OPEN, UNKNOWN, NOT_CONFIGURED }
}

package org.github.tess1o.geopulse.gps.service;

import java.util.Map;

/**
 * A skipped GPS point: the reason code plus the values needed to explain it (for example the
 * reported accuracy and the configured threshold).
 */
public record GpsIngestRejection(GpsIngestRejectionCode code, Map<String, Object> params) {

    public GpsIngestRejection {
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public static GpsIngestRejection of(GpsIngestRejectionCode code) {
        return new GpsIngestRejection(code, Map.of());
    }
}

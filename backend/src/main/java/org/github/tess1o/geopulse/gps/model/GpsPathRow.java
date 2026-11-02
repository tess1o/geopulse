package org.github.tess1o.geopulse.gps.model;

import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import java.time.Instant;
import java.util.Map;

/** Scalar path data without a managed entity or geometry. */
public record GpsPathRow(Long id, double longitude, double latitude, Instant timestamp,
                         Double accuracy, Double altitude, Double velocity, GpsSourceType sourceType,
                         Map<String, Object> telemetry) {
}

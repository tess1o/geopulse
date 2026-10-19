package org.github.tess1o.geopulse.gps.model;

import java.time.Instant;
import java.util.List;

public record GpsPointUpdateResult(
        List<GpsPointDTO> points,
        Instant earliestAffectedTimestamp,
        boolean coordinatesChanged
) {
}

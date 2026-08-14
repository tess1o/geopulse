package org.github.tess1o.geopulse.gps.model;

import java.time.Instant;

public record GpsPointDeleteResult(
        int deletedCount,
        Instant earliestAffectedTimestamp
) {
}

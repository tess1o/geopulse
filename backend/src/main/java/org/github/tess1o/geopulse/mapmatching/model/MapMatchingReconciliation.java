package org.github.tess1o.geopulse.mapmatching.model;

import java.time.Instant;
import java.util.UUID;

public record MapMatchingReconciliation(
        long id,
        UUID userId,
        MapMatchingSource source,
        Instant rangeStart,
        Instant rangeEnd,
        Instant cursorAt,
        long cursorTripId,
        Instant eligibleAt,
        long totalTrips,
        long scannedTrips,
        Instant completedAt
) {
}

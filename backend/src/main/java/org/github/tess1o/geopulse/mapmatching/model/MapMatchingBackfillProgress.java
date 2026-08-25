package org.github.tess1o.geopulse.mapmatching.model;

import java.time.Instant;

public record MapMatchingBackfillProgress(
        long totalTrips,
        long scannedTrips,
        long totalUsers,
        long completedUsers,
        Instant lastActivityAt
) {
    public long remainingTrips() {
        return Math.max(0, totalTrips - scannedTrips);
    }

    public long remainingUsers() {
        return Math.max(0, totalUsers - completedUsers);
    }

    public double percent() {
        if (totalTrips <= 0) {
            return totalUsers > 0 && completedUsers >= totalUsers ? 100.0 : 0.0;
        }
        return Math.min(100.0, Math.max(0.0, scannedTrips * 100.0 / totalTrips));
    }
}

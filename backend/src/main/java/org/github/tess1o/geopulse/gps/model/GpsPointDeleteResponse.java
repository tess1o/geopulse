package org.github.tess1o.geopulse.gps.model;

import java.util.UUID;

public record GpsPointDeleteResponse(
        int deletedCount,
        UUID timelineJobId,
        boolean timelineRegenerationScheduled,
        boolean coverageRebuildScheduled
) {
}

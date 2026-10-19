package org.github.tess1o.geopulse.gps.model;

import java.util.UUID;

public record GpsPointBulkUpdateResponse(
        int updatedCount,
        UUID timelineJobId,
        boolean timelineRegenerationScheduled,
        boolean coverageRebuildScheduled
) {
}

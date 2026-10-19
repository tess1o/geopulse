package org.github.tess1o.geopulse.gps.model;

import java.util.UUID;

public record GpsPointUpdateResponse(
        GpsPointDTO point,
        UUID timelineJobId,
        boolean timelineRegenerationScheduled,
        boolean coverageRebuildScheduled
) {
}

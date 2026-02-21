package org.github.tess1o.geopulse.coverage.model;

import java.time.Instant;

public record CoverageProcessingCursor(
        Instant timestamp,
        Long pointId
) {
}

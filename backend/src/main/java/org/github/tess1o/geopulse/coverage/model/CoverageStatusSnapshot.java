package org.github.tess1o.geopulse.coverage.model;

import java.time.Instant;

public record CoverageStatusSnapshot(
        boolean userEnabled,
        boolean processing,
        boolean hasCells,
        Instant lastProcessed,
        Instant processingStartedAt
) {
}

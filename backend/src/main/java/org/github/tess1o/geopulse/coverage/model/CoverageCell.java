package org.github.tess1o.geopulse.coverage.model;

public record CoverageCell(
        double latitude,
        double longitude,
        int gridMeters,
        long seenCount
) {
}

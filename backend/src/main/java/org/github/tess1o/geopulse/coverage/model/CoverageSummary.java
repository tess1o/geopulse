package org.github.tess1o.geopulse.coverage.model;

public record CoverageSummary(
        int gridMeters,
        long totalCells,
        double areaSquareKm
) {
}

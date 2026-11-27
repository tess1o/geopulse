package org.github.tess1o.geopulse.coverage.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Cells of one grid inside a bounding box. A cell spans {@code [cellX * gridMeters, (cellX + 1) * gridMeters]} by
 * {@code [cellY * gridMeters, (cellY + 1) * gridMeters]} in Web Mercator (EPSG:3857) metres.
 */
public record CoverageCells(
        @Schema(description = "Grid cell size in meters.", examples = "50")
        int gridMeters,
        @Schema(description = "Whether the bounding box holds more cells than were returned.")
        boolean truncated,
        @Schema(description = "Cells as `[cellX, cellY, seenCount]`.", examples = "[[11152, 6522, 3]]")
        List<long[]> cells
) {
}

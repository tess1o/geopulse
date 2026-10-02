package org.github.tess1o.geopulse.streaming.model.dto;

public record TimelineCountResponse(
        long stays,
        long trips,
        long dataGaps,
        long totalItems,
        long limit
) {
}

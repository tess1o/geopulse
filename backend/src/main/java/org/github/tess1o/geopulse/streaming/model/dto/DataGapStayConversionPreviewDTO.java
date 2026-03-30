package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * Preview payload for default Data Gap -> Stay conversion using latest known point.
 */
@Builder
public record DataGapStayConversionPreviewDTO(
        Long dataGapId,
        Instant startTime,
        Instant endTime,
        Long durationSeconds,
        Double anchorLatitude,
        Double anchorLongitude,
        String locationName,
        Long favoriteId,
        Long geocodingId
) {
}

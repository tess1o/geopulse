package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * Response payload for manual Data Gap -> Stay override operations.
 */
@Builder
public record DataGapStayOverrideResponseDTO(
        Long overrideId,
        Long dataGapId,
        Long stayId,
        String locationStrategy,
        String locationName,
        Instant startTime,
        Instant endTime,
        Long stayDurationSeconds,
        Instant regenerationStartTime
) {
}

package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

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
        Instant regenerationStartTime,
        /** The job regenerating the timeline after a conversion was undone, to follow; null when none could start now. */
        UUID timelineJobId,
        /** Whether the regeneration was queued behind one that is already running (then timelineJobId is null). */
        boolean timelineJobQueued
) {
    public DataGapStayOverrideResponseDTO withTimelineJob(UUID jobId, boolean queued) {
        return new DataGapStayOverrideResponseDTO(overrideId, dataGapId, stayId, locationStrategy, locationName,
                startTime, endTime, stayDurationSeconds, regenerationStartTime, jobId, queued);
    }
}

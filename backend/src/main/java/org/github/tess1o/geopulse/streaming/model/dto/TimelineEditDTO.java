package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Builder;
import org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionOp;
import org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionStatus;
import org.github.tess1o.geopulse.streaming.edits.payload.BeforeValues;
import org.github.tess1o.geopulse.streaming.edits.payload.CorrectionPayload;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record TimelineEditDTO(
        Long id,
        UUID groupId,
        long seq,
        TimelineCorrectionOp op,
        Instant anchorStart,
        Instant anchorEnd,
        Instant affectedFrom,
        Instant affectedTo,
        int payloadVersion,
        CorrectionPayload payload,
        BeforeValues beforeValues,
        TimelineCorrectionStatus status,
        boolean applied,
        TimelineEditTargetDTO target,
        List<TimelineEditTargetDTO> effects,
        UUID replacedByGroupId,
        Instant createdAt,
        Instant updatedAt
) {
}

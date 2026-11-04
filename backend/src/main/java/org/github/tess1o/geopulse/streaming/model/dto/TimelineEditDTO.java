package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Builder;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditStatus;

import java.time.Instant;
import java.util.Map;

@Builder
public record TimelineEditDTO(
        Long id,
        java.util.UUID groupId,
        long seq,
        TimelineEditKind kind,
        Instant anchorStart,
        Instant anchorEnd,
        Instant affectedFrom,
        Instant affectedTo,
        Map<String, Object> payload,
        Map<String, Object> beforeValues,
        TimelineEditStatus status,
        boolean applied,
        TimelineEditTargetDTO target,
        Instant createdAt,
        Instant updatedAt
) {
}

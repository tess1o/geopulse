package org.github.tess1o.geopulse.streaming.edits;

import org.github.tess1o.geopulse.streaming.edits.payload.CorrectionPayload;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEventType;

import java.time.Instant;
import java.util.List;

public record TimelineEditCommand(
        Long id,
        long seq,
        TimelineEditKind kind,
        Instant anchorStart,
        Instant anchorEnd,
        CorrectionPayload payload,
        TimelineEventType sourceType,
        List<Long> prerequisiteSequences
) {
    public TimelineEditCommand(Long id, long seq, TimelineEditKind kind, Instant anchorStart, Instant anchorEnd,
                               CorrectionPayload payload) {
        this(id, seq, kind, anchorStart, anchorEnd, payload, null, List.of());
    }
}

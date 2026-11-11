package org.github.tess1o.geopulse.streaming.edits;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TimelineEditCommand(
        Long id,
        long seq,
        TimelineEditKind kind,
        Instant anchorStart,
        Instant anchorEnd,
        Map<String, Object> payload,
        List<Long> prerequisiteSequences
) {
    public TimelineEditCommand(Long id, long seq, TimelineEditKind kind, Instant anchorStart, Instant anchorEnd,
                               Map<String, Object> payload) {
        this(id, seq, kind, anchorStart, anchorEnd, payload, List.of());
    }
}

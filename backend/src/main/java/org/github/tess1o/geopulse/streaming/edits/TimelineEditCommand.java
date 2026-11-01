package org.github.tess1o.geopulse.streaming.edits;

import java.time.Instant;
import java.util.Map;

public record TimelineEditCommand(
        Long id,
        long seq,
        TimelineEditKind kind,
        Instant anchorStart,
        Instant anchorEnd,
        Map<String, Object> payload
) {
}

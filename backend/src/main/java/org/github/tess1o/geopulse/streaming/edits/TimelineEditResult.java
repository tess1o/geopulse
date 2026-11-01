package org.github.tess1o.geopulse.streaming.edits;

import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;

import java.util.List;
import java.util.Map;

public record TimelineEditResult(
        List<TimelineEvent> events,
        Map<Long, TimelineEvent> applications,
        List<Long> unappliedEditIds
) {
    public List<Long> appliedEditIds() {
        return applications.keySet().stream().toList();
    }
}

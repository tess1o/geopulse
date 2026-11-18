package org.github.tess1o.geopulse.streaming.edits;

import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;

import java.util.List;
import java.util.Map;

/**
 * The result of folding corrections over the detected timeline. {@code effects} maps each in-effect correction to
 * the event it produced or changed; {@code unappliedEditIds} lists corrections that contributed nothing.
 */
public record TimelineEditResult(
        List<TimelineEvent> events,
        Map<Long, List<TimelineEvent>> effects,
        List<Long> unappliedEditIds
) {
    public List<Long> appliedEditIds() {
        return effects.keySet().stream().toList();
    }
}

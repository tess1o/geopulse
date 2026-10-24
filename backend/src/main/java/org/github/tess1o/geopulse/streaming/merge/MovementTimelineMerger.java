package org.github.tess1o.geopulse.streaming.merge;

import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;

import java.util.List;

public interface MovementTimelineMerger {
    /**
     * Merge consecutive stays at the same location on raw timeline objects.
     * This preserves rich GPS data throughout the merging process.
     *
     * @param events the timeline in order, as trip post-processing produced it
     * @return the timeline in order, with merged stays in place of the stays and trips they absorbed
     */
    List<TimelineEvent> mergeSameNamedLocations(TimelineConfig timelineConfig, List<TimelineEvent> events);
}

package org.github.tess1o.geopulse.streaming.merge;

import org.github.tess1o.geopulse.streaming.model.domain.RawTimeline;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;

public interface MovementTimelineMerger {
    /**
     * Merge same named locations on raw timeline objects.
     * This preserves rich GPS data throughout the merging process.
     */
    RawTimeline mergeSameNamedLocations(TimelineConfig timelineConfig, RawTimeline input);
}

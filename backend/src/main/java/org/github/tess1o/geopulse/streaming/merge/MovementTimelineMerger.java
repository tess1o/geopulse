package org.github.tess1o.geopulse.streaming.merge;

import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;

public interface MovementTimelineMerger {
    MovementTimelineDTO mergeSameNamedLocations(TimelineConfig timelineConfig, MovementTimelineDTO input);
}

package org.github.tess1o.geopulse.timeline.merge;

import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;

public interface MovementTimelineMerger {
    MovementTimelineDTO mergeSameNamedLocations(TimelineConfig timelineConfig, MovementTimelineDTO input);
}

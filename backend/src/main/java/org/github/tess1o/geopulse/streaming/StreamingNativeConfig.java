package org.github.tess1o.geopulse.streaming;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.TimelineJobProgress;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.domain.ProcessorMode;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEventType;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineDataGapDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;
import org.github.tess1o.geopulse.streaming.model.entity.*;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;

@RegisterForReflection(targets = {
        TimelineStayEntity.class,
        TimelineTripEntity.class,
        TimelineDataGapEntity.class,
        LocationSource.class,
        ProcessorMode.class,
        TimelineEventType.class,
        TripType.class,
        TimelineDataGapDTO.class,
        MovementTimelineDTO.class,
        TimelineStayLocationDTO.class,
        TimelineTripDTO.class,
        TimelineDataGapDTO.class,
        TimelineConfig.class,
        TimelineJobProgress.class,
        TimelineJobProgress.JobStatus.class,
})
public class StreamingNativeConfig {
}

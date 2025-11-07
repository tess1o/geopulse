package org.github.tess1o.geopulse.streaming;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.domain.ProcessorMode;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEventType;
import org.github.tess1o.geopulse.streaming.model.dto.*;
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
        PagedPlaceVisitsDTO.class,
        PlaceDetailsDTO.class,
        PlaceGeometryDTO.class,
        PlaceStatisticsDTO.class,
        PlaceVisitDTO.class
})
public class StreamingNativeConfig {
}

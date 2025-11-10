package org.github.tess1o.geopulse.streaming.service.trips;

import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;

import java.util.List;
import java.util.UUID;

public interface StreamTripAlgorithm {
    List<TimelineEvent> apply(UUID userId, List<TimelineEvent> events, TimelineConfig config);
}

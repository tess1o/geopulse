package org.github.tess1o.geopulse.streaming.service.trips;

import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;

import java.util.List;

public interface StreamTripAlgorithm {
    List<TimelineEvent> apply(List<TimelineEvent> events, TimelineConfig config);
}

package org.github.tess1o.geopulse.streaming.engine;

import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.UserState;

import java.time.Duration;

record DataGapContext(
        GPSPoint lastPoint,
        GPSPoint currentPoint,
        UserState userState,
        TimelineConfig config,
        Duration gapDuration
) {
}

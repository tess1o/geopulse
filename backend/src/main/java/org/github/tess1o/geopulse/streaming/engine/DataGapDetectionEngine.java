package org.github.tess1o.geopulse.streaming.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.UserState;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.service.StreamingDataGapService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Engine responsible for detecting data gaps in GPS data streams.
 * A data gap occurs when the time between consecutive GPS points exceeds
 * the configured threshold, indicating the device was off or GPS disabled.
 */
@Slf4j
@ApplicationScoped
public class DataGapDetectionEngine {
    @Inject
    StreamingDataGapService dataGapService;

    @Inject
    DataGapRuleRegistry dataGapRuleRegistry;

    /**
     * Check if there is a data gap between the last processed point and the current point.
     * If a gap is detected, creates appropriate timeline events and resets user state.
     *
     * @param currentPoint the current GPS point being processed
     * @param userState    current user processing state
     * @param config       timeline configuration containing gap detection parameters
     * @return list of timeline events created due to the gap (may be empty)
     */
    public List<TimelineEvent> checkForDataGap(GPSPoint currentPoint, UserState userState, TimelineConfig config) {
        List<TimelineEvent> gapEvents = new ArrayList<>();

        GPSPoint lastPoint = userState.getLastProcessedPoint();
        if (lastPoint == null) {
            log.debug("No previous GPS point - no gap to check");
            return gapEvents;
        }

        Duration timeDelta = Duration.between(lastPoint.getTimestamp(), currentPoint.getTimestamp());
        if (!dataGapService.shouldCreateDataGap(config, lastPoint.getTimestamp(), currentPoint.getTimestamp())) {
            return gapEvents;
        }

        log.info("Data gap detected: {} duration between {} and {}",
                timeDelta, lastPoint.getTimestamp(), currentPoint.getTimestamp());

        DataGapContext context = new DataGapContext(lastPoint, currentPoint, userState, config, timeDelta);
        for (DataGapRule rule : dataGapRuleRegistry.getOrderedRules()) {
            if (rule.apply(context, gapEvents)) {
                return gapEvents;
            }
        }

        log.warn("No data gap rule handled context for {} -> {}",
                lastPoint.getTimestamp(), currentPoint.getTimestamp());
        return gapEvents;
    }
}

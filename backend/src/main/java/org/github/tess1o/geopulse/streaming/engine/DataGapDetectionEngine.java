package org.github.tess1o.geopulse.streaming.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.UserState;
import org.github.tess1o.geopulse.streaming.service.StreamingDataGapService;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;

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
    TimelineEventFinalizationService finalizationService;

    @Inject
    StreamingDataGapService dataGapService;

    /**
     * Check if there is a data gap between the last processed point and the current point.
     * If a gap is detected, creates appropriate timeline events and resets user state.
     *
     * @param currentPoint the current GPS point being processed
     * @param userState    current user processing state
     * @param config       timeline configuration containing gap detection parameters
     * @param userId       user identifier for logging purposes
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

        // Use the service to determine if we should create a data gap
        if (dataGapService.shouldCreateDataGap(config, lastPoint.getTimestamp(), currentPoint.getTimestamp())) {
            log.info("Data gap detected: {} duration between {} and {}",
                    timeDelta, lastPoint.getTimestamp(), currentPoint.getTimestamp());

            // Finalize any active event before creating the gap
            TimelineEvent activeEvent = finalizeActiveEvent(userState, lastPoint, config);
            if (activeEvent != null) {
                gapEvents.add(activeEvent);
                log.debug("Finalized active event before gap: {} from {} to {}",
                        activeEvent.getType(), activeEvent.getStartTime(), activeEvent.getEndTime());
            }

            // Create the data gap event
            DataGap gap = DataGap.fromTimeRange(lastPoint.getTimestamp(), currentPoint.getTimestamp());
            gapEvents.add(gap);
            log.debug("Created data gap event: {} duration", gap.getDuration());

            // Reset user state after gap - gap breaks continuity
            userState.reset();
            log.debug("User state reset due to data gap");
        }

        return gapEvents;
    }

    /**
     * Finalize any active timeline event when a data gap is detected.
     * This ensures events are properly closed before gap creation.
     *
     * @param userState current user state
     * @param lastPoint the last GPS point before the gap
     * @param config    timeline configuration for validation
     * @return finalized timeline event, or null if no active event
     */
    private TimelineEvent finalizeActiveEvent(UserState userState, GPSPoint lastPoint, TimelineConfig config) {
        if (!userState.hasActivePoints()) {
            return null;
        }

        switch (userState.getCurrentMode()) {
            case POTENTIAL_STAY:
            case CONFIRMED_STAY:
                return finalizationService.finalizeStayWithoutLocation(userState, config);

            case IN_TRIP:
                return finalizationService.finalizeTripForGap(userState, lastPoint);

            case UNKNOWN:
            default:
                log.debug("No active event to finalize in mode: {}", userState.getCurrentMode());
                return null;
        }
    }
}
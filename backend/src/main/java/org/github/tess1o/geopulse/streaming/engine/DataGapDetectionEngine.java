package org.github.tess1o.geopulse.streaming.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.ProcessorMode;
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

            // Check if we should infer a stay instead of creating a gap
            boolean shouldInfer = shouldInferStayDuringGap(currentPoint, userState, config, timeDelta);
            log.info("Gap stay inference check: shouldInfer={}, enabled={}",
                    shouldInfer, config.getGapStayInferenceEnabled());
            if (shouldInfer) {
                log.info("Gap stay inference applied - skipping gap creation, points are at same location");
                // Don't create gap, don't reset state - let state machine process the point normally
                // The point will be added to activePoints and duration calculation will span the gap
                return gapEvents;
            }

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
     * Determines whether to infer a stay during a data gap instead of creating a gap.
     * This feature helps capture overnight stays at home or extended stays where
     * the app doesn't send GPS data but the user remains at the same location.
     *
     * @param currentPoint the GPS point after the gap
     * @param userState    current user processing state
     * @param config       timeline configuration
     * @param gapDuration  duration of the gap
     * @return true if a stay should be inferred instead of creating a gap
     */
    private boolean shouldInferStayDuringGap(GPSPoint currentPoint, UserState userState,
                                              TimelineConfig config, Duration gapDuration) {
        // Check if feature is enabled
        Boolean enabled = config.getGapStayInferenceEnabled();
        if (enabled == null || !enabled) {
            log.debug("Gap stay inference is disabled (enabled={})", enabled);
            return false;
        }

        // Check if we have active points to compare against
        if (!userState.hasActivePoints()) {
            log.debug("No active points for gap stay inference comparison");
            return false;
        }

        // Check if current mode is POTENTIAL_STAY or CONFIRMED_STAY (not IN_TRIP)
        ProcessorMode mode = userState.getCurrentMode();
        if (mode == ProcessorMode.IN_TRIP || mode == ProcessorMode.UNKNOWN) {
            log.debug("Gap stay inference not applicable for mode: {}", mode);
            return false;
        }

        // Check if gap is within max duration
        Integer maxGapHours = config.getGapStayInferenceMaxGapHours();
        if (maxGapHours != null && maxGapHours > 0) {
            long gapHours = gapDuration.toHours();
            if (gapHours > maxGapHours) {
                log.debug("Gap duration {}h exceeds max allowed {}h for stay inference",
                        gapHours, maxGapHours);
                return false;
            }
        }

        // Calculate distance between centroid and current point
        GPSPoint centroid = userState.calculateCentroid();
        if (centroid == null) {
            log.debug("Could not calculate centroid for gap stay inference");
            return false;
        }

        double distance = centroid.distanceTo(currentPoint);
        Integer radiusMeters = config.getStaypointRadiusMeters();
        if (radiusMeters == null) {
            radiusMeters = 50; // default
        }

        if (distance > radiusMeters) {
            log.debug("Distance {}m from centroid exceeds stay radius {}m - creating gap instead",
                    String.format("%.1f", distance), radiusMeters);
            return false;
        }

        log.info("Gap stay inference conditions met: mode={}, gap={}h, distance={}m (radius={}m)",
                mode, gapDuration.toHours(), String.format("%.1f", distance), radiusMeters);
        return true;
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
                return finalizationService.finalizeTripForGap(userState, lastPoint, config);

            case UNKNOWN:
            default:
                log.debug("No active event to finalize in mode: {}", userState.getCurrentMode());
                return null;
        }
    }
}
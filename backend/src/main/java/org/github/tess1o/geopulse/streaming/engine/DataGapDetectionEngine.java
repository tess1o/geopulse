package org.github.tess1o.geopulse.streaming.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.ProcessorMode;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.domain.UserState;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.service.StreamingDataGapService;
import org.github.tess1o.geopulse.streaming.service.trips.TravelClassification;
import org.github.tess1o.geopulse.streaming.service.trips.TripGpsStatistics;
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

    @Inject
    TravelClassification travelClassification;

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

            // Priority 1: Check if we should infer a stay instead of creating a gap
            boolean shouldInferStay = shouldInferStayDuringGap(currentPoint, userState, config, timeDelta);
            log.info("Gap stay inference check: shouldInfer={}, enabled={}",
                    shouldInferStay, config.getGapStayInferenceEnabled());
            if (shouldInferStay) {
                log.info("Gap stay inference applied - skipping gap creation, points are at same location");
                // Don't create gap, don't reset state - let state machine process the point normally
                // The point will be added to activePoints and duration calculation will span the gap
                return gapEvents;
            }

            // Priority 2: Check if we should infer a trip instead of creating a gap
            boolean shouldInferTrip = shouldInferTripDuringGap(lastPoint, currentPoint, userState, config, timeDelta);
            log.info("Gap trip inference check: shouldInfer={}, enabled={}",
                    shouldInferTrip, config.getGapTripInferenceEnabled());

            if (shouldInferTrip) {
                log.info("Gap trip inference applied - creating inferred trip for gap");

                // Finalize any active event before creating the inferred trip
                TimelineEvent activeEvent = finalizeActiveEvent(userState, lastPoint, config);
                if (activeEvent != null) {
                    gapEvents.add(activeEvent);
                    log.debug("Finalized active event before inferred trip: {} from {} to {}",
                            activeEvent.getType(), activeEvent.getStartTime(), activeEvent.getEndTime());
                }

                // Create the inferred trip
                Trip inferredTrip = createInferredTrip(lastPoint, currentPoint, config);
                gapEvents.add(inferredTrip);
                log.debug("Created inferred trip: {} distance, {} duration, type {}",
                        inferredTrip.getDistanceMeters(), inferredTrip.getDuration(), inferredTrip.getTripType());

                // Reset user state after gap - gap breaks continuity
                userState.reset();
                log.debug("User state reset after inferred trip");

                return gapEvents;
            }

            // Priority 3: No inference - create normal data gap
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
     * Determines whether to infer a trip during a data gap instead of creating a gap.
     * This feature helps capture long-distance movements where GPS data was unavailable,
     * such as international flights or long drives where the phone was off.
     *
     * Inference applies when:
     * - Feature is enabled
     * - Gap duration is within configured range (min/max hours)
     * - Distance between points exceeds minimum threshold
     *
     * Note: This check runs AFTER gap stay inference, which already handles the
     * case where points are at the same location. Therefore, no mode check is needed.
     *
     * @param lastPoint    the GPS point before the gap
     * @param currentPoint the GPS point after the gap
     * @param userState    current user processing state
     * @param config       timeline configuration
     * @param gapDuration  duration of the gap
     * @return true if a trip should be inferred instead of creating a gap
     */
    private boolean shouldInferTripDuringGap(GPSPoint lastPoint, GPSPoint currentPoint,
                                              UserState userState, TimelineConfig config,
                                              Duration gapDuration) {
        // Check if feature is enabled
        Boolean enabled = config.getGapTripInferenceEnabled();
        if (enabled == null || !enabled) {
            log.debug("Gap trip inference is disabled (enabled={})", enabled);
            return false;
        }

        // Check minimum gap duration
        Integer minGapHours = config.getGapTripInferenceMinGapHours();
        if (minGapHours != null && minGapHours > 0) {
            long gapHours = gapDuration.toHours();
            if (gapHours < minGapHours) {
                log.debug("Gap duration {}h is below minimum {}h for trip inference",
                        gapHours, minGapHours);
                return false;
            }
        }

        // Check maximum gap duration
        Integer maxGapHours = config.getGapTripInferenceMaxGapHours();
        if (maxGapHours != null && maxGapHours > 0) {
            long gapHours = gapDuration.toHours();
            if (gapHours > maxGapHours) {
                log.debug("Gap duration {}h exceeds maximum {}h for trip inference",
                        gapHours, maxGapHours);
                return false;
            }
        }

        // Calculate distance between last point and current point
        double distance = lastPoint.distanceTo(currentPoint);
        Integer minDistanceMeters = config.getGapTripInferenceMinDistanceMeters();
        if (minDistanceMeters == null) {
            minDistanceMeters = 100000; // default 100km
        }

        if (distance < minDistanceMeters) {
            log.debug("Distance {}m is below minimum {}m for trip inference",
                    String.format("%.1f", distance), minDistanceMeters);
            return false;
        }

        log.info("Gap trip inference conditions met: mode={}, gap={}h, distance={}m (min={}m)",
                userState.getCurrentMode(), gapDuration.toHours(), String.format("%.1f", distance), minDistanceMeters);
        return true;
    }

    /**
     * Creates an inferred trip from a data gap.
     * The trip consists of only two GPS points (before and after the gap).
     * Trip classification is performed using the existing classification algorithm.
     *
     * IMPORTANT: We use empty GPS statistics instead of calculating from the 2 points
     * because the GPS speeds at those points (captured before/after the gap) are
     * unrelated to the actual travel that occurred during the gap.
     * Example: A flight shows stationary speeds (2-4 km/h) at departure/arrival.
     *
     * @param lastPoint    GPS point before the gap (trip origin)
     * @param currentPoint GPS point after the gap (trip destination)
     * @param config       timeline configuration for trip classification
     * @return inferred Trip object with classification
     */
    private Trip createInferredTrip(GPSPoint lastPoint, GPSPoint currentPoint, TimelineConfig config) {
        // Build minimal GPS path (2 points)
        List<GPSPoint> tripPath = new ArrayList<>();
        tripPath.add(lastPoint);
        tripPath.add(currentPoint);

        // Calculate trip metrics
        Duration tripDuration = Duration.between(lastPoint.getTimestamp(), currentPoint.getTimestamp());
        double distanceMeters = lastPoint.distanceTo(currentPoint);

        // Use EMPTY statistics for inferred trips
        // The GPS speeds at the 2 boundary points are meaningless for classification
        // (e.g., stationary at home before flight, stationary after landing)
        // This triggers distance-based heuristics in TravelClassification
        TripGpsStatistics gpsStatistics = TripGpsStatistics.empty();

        // Classify the trip using existing algorithm
        // This will use classifyWithoutGpsStatistics() which has distance-based heuristics
        TripType tripType = travelClassification.classifyTravelType(
                gpsStatistics,
                tripDuration,
                Double.valueOf(distanceMeters).longValue(),
                config
        );

        log.debug("Inferred trip classification: type={}, distance={}m, duration={}h, avgSpeed={}km/h",
                tripType,
                String.format("%.0f", distanceMeters),
                String.format("%.2f", tripDuration.toHours() + (tripDuration.toMinutesPart() / 60.0)),
                String.format("%.1f", (distanceMeters / 1000.0) / (tripDuration.getSeconds() / 3600.0)));

        // Build and return the inferred trip
        return Trip.builder()
                .startTime(lastPoint.getTimestamp())
                .duration(tripDuration)
                .statistics(gpsStatistics)
                .startPoint(lastPoint)
                .endPoint(currentPoint)
                .distanceMeters(distanceMeters)
                .tripType(tripType)
                .build();
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
package org.github.tess1o.geopulse.streaming.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.*;

import java.time.Duration;
import java.util.*;

/**
 * Core streaming timeline processor implementing the state machine algorithm.
 * Processes GPS points one by one, maintaining user state and generating timeline events
 * (stays, trips, data gaps) based on movement patterns and configured thresholds.
 *
 * <p>Features accuracy-based filtering to improve data quality:</p>
 * <ul>
 *   <li><strong>Pre-filtering:</strong> Removes GPS points with accuracy exceeding the configured threshold</li>
 *   <li><strong>Cluster validation:</strong> Ensures staypoint clusters meet minimum accuracy ratio requirements</li>
 *   <li><strong>Configurable thresholds:</strong> Uses staypointMaxAccuracyThreshold and staypointMinAccuracyRatio from TimelineConfig</li>
 * </ul>
 *
 * <p>Accuracy validation can be disabled by setting useVelocityAccuracy=false in the configuration.</p>
 */
@ApplicationScoped
@Slf4j
public class StreamingTimelineProcessor {

    @Inject
    DataGapDetectionEngine dataGapEngine;

    @Inject
    TimelineEventFinalizationService finalizationService;

    /**
     * Process GPS points to generate timeline events (stays, trips, data gaps).
     *
     * <p>This method applies accuracy-based filtering before processing:</p>
     * <ol>
     *   <li>Filters GPS points using staypointMaxAccuracyThreshold</li>
     *   <li>Processes filtered points through the state machine</li>
     *   <li>Validates staypoint clusters using staypointMinAccuracyRatio</li>
     *   <li>Populates location data for finalized stays</li>
     * </ol>
     *
     * @param contextPoints existing GPS points for warming up the state machine
     * @param newPoints     new GPS points to process
     * @param config        timeline configuration with accuracy thresholds
     * @param userId        user identifier for location resolution
     * @return list of finalized timeline events
     */
    public List<TimelineEvent> processPoints(List<GPSPoint> contextPoints, List<GPSPoint> newPoints, TimelineConfig config, UUID userId) {
        UserState userState = new UserState();
        List<TimelineEvent> finalizedEvents = new ArrayList<>();

        // Filter points by accuracy before processing
        List<GPSPoint> filteredContextPoints = filterPointsByAccuracy(contextPoints, config);
        List<GPSPoint> filteredNewPoints = filterPointsByAccuracy(newPoints, config);

        // 1. Warm up the state machine with context points (no gap detection)
        for (GPSPoint point : filteredContextPoints) {
            processPoint(point, userState, config, finalizedEvents, false);
        }

        // 2. Process new points (with gap detection, except for first point after context)
        boolean isFirstNewPoint = !filteredContextPoints.isEmpty();
        for (GPSPoint point : filteredNewPoints) {
            // Skip gap detection for first new point if we had context points 
            // (to avoid detecting gap in deleted event period)
            boolean detectGaps = !isFirstNewPoint;
            processPoint(point, userState, config, finalizedEvents, detectGaps);
            isFirstNewPoint = false;
        }

        // 3. Finalize any remaining active event
        TimelineEvent finalEvent = finalizeActiveEvent(userState, config, userId);
        if (finalEvent != null) {
            finalizedEvents.add(finalEvent);
        }

        // 4. Batch populate location data for all stays at once
        finalizationService.populateStayLocations(finalizedEvents, userId);

        return finalizedEvents;
    }

    /**
     * Process a single GPS point through the state machine.
     * This is the main entry point for the streaming algorithm.
     *
     * @param point     the GPS point to process
     * @param userState current user processing state
     * @param config    timeline configuration with user preferences
     * @return processing result with updated state and any finalized events
     */
    private void processPoint(GPSPoint point, UserState userState, TimelineConfig config, List<TimelineEvent> finalizedEvents, boolean detectGaps) {
        // 1. Check for data gaps first - this can interrupt any state (only for new points, not context)
        if (detectGaps) {
            List<TimelineEvent> gapEvents = dataGapEngine.checkForDataGap(point, userState, config);
            finalizedEvents.addAll(gapEvents);
        }

        // 2. Main state machine processing
        ProcessingResult stateResult = processStateMachine(point, userState, config);
        if (detectGaps) {
            finalizedEvents.addAll(stateResult.getFinalizedEvents());
        }

        // 3. Update last processed point
        userState.setLastProcessedPoint(point);
    }

    /**
     * Process the main state machine logic for a GPS point.
     *
     * @param point     GPS point to process
     * @param userState current state
     * @param config    timeline configuration
     * @return processing result from state machine
     */
    private ProcessingResult processStateMachine(GPSPoint point, UserState userState, TimelineConfig config) {
        switch (userState.getCurrentMode()) {
            case UNKNOWN:
                return handleUnknownState(point, userState);

            case POTENTIAL_STAY:
                return handlePotentialStayState(point, userState, config);

            case CONFIRMED_STAY:
                return handleConfirmedStayState(point, userState, config);

            case IN_TRIP:
                return handleTripState(point, userState, config);

            default:
                log.warn("Unknown processor mode: {}", userState.getCurrentMode());
                return ProcessingResult.withStateOnly(userState);
        }
    }

    /**
     * Handle UNKNOWN state - transition to POTENTIAL_STAY with first point.
     */
    private ProcessingResult handleUnknownState(GPSPoint point, UserState userState) {
        userState.setCurrentMode(ProcessorMode.POTENTIAL_STAY);
        userState.clearActivePoints();
        userState.addActivePoint(point);

        return ProcessingResult.withStateOnly(userState);
    }

    /**
     * Handle POTENTIAL_STAY state - check distance and duration to determine next state.
     */
    private ProcessingResult handlePotentialStayState(GPSPoint point, UserState userState, TimelineConfig config) {
        GPSPoint centroid = userState.calculateCentroid();
        double distance = centroid.distanceTo(point);
        double stayRadius = getStayRadius(config);

        log.debug("POTENTIAL_STAY: point={}, centroid={}, distance={}, stayRadius={}",
                point.getTimestamp(), centroid.getTimestamp(), distance, stayRadius);

        if (distance > stayRadius) {
            log.debug("Distance {} > stayRadius {} - transitioning to trip", distance, stayRadius);
            // Point is far - transition to trip
            return transitionToTrip(point, userState);
        } else {
            // Point is close - add to potential stay
            userState.addActivePoint(point);

            // Check if we've been here long enough to confirm the stay
            Duration stayDuration = calculateCurrentStayDuration(userState);
            Duration minStayDuration = getMinStayDuration(config);

            log.debug("POTENTIAL_STAY: stayDuration={}, minStayDuration={}, activePoints={}",
                    stayDuration, minStayDuration, userState.getActivePoints().size());

            if (stayDuration.compareTo(minStayDuration) >= 0) {
                log.debug("CONFIRMED_STAY: Duration {} >= min duration {}", stayDuration, minStayDuration);
                userState.setCurrentMode(ProcessorMode.CONFIRMED_STAY);
            }

            return ProcessingResult.withStateOnly(userState);
        }
    }

    /**
     * Handle CONFIRMED_STAY state - check if user has moved away.
     */
    private ProcessingResult handleConfirmedStayState(GPSPoint point, UserState userState, TimelineConfig config) {
        GPSPoint centroid = userState.calculateCentroid();
        double distance = centroid.distanceTo(point);
        double stayRadius = getStayRadius(config);

        if (distance > stayRadius) {
            // User has moved away - finalize stay without location data
            TimelineEvent finalizedStay = finalizationService.finalizeStayWithoutLocation(userState, config);

            // Start new trip
            userState.setCurrentMode(ProcessorMode.IN_TRIP);
            userState.clearActivePoints();
            userState.addActivePoint(point);

            return ProcessingResult.withSingleEvent(userState, finalizedStay);
        } else {
            // Still at the same location - continue stay
            userState.addActivePoint(point);
            return ProcessingResult.withStateOnly(userState);
        }
    }

    /**
     * Handle IN_TRIP state - continue trip until sustained stopping is detected.
     */
    private ProcessingResult handleTripState(GPSPoint point, UserState userState, TimelineConfig config) {
        userState.addActivePoint(point);
//
        // Check for sustained stopping (multiple consecutive slow points over time)
        if (isSustainedStopInTrip(userState, config)) {
            // Finalize the trip up to where stopping began
            TimelineEvent finalizedTrip = finalizationService.finalizeTrip(userState, config);

            // Start new potential stay
            userState.setCurrentMode(ProcessorMode.POTENTIAL_STAY);
            userState.clearActivePoints();
            userState.addActivePoint(point);

            return ProcessingResult.withSingleEvent(userState, finalizedTrip);
        }

        return ProcessingResult.withStateOnly(userState);
    }

    /**
     * Transition from potential stay to trip state.
     */
    private ProcessingResult transitionToTrip(GPSPoint point, UserState userState) {
        userState.setCurrentMode(ProcessorMode.IN_TRIP);
        userState.addActivePoint(point);
        return ProcessingResult.withStateOnly(userState);
    }

    /**
     * Finalize any active event when processing completes.
     * This is called at the end of processing a GPS point sequence.
     *
     * @param userState final user state
     * @param config    timeline configuration
     * @param userId    user identifier for location matching
     * @return finalized event or null if none active
     */
    public TimelineEvent finalizeActiveEvent(UserState userState, TimelineConfig config, java.util.UUID userId) {
        if (!userState.hasActivePoints()) {
            return null;
        }

        switch (userState.getCurrentMode()) {
            case POTENTIAL_STAY:
            case CONFIRMED_STAY:
                return finalizationService.finalizeStayWithoutLocation(userState, config);

            case IN_TRIP:
                return finalizationService.finalizeTrip(userState, config);

            case UNKNOWN:
            default:
                return null;
        }
    }


    // Configuration getters with fallback defaults


    private double getStayRadius(TimelineConfig config) {
        return config.getStaypointRadiusMeters();
    }

    private Duration getMinStayDuration(TimelineConfig config) {
        Integer staypointMinDurationMinutes = config.getStaypointMinDurationMinutes();
        return Duration.ofMinutes(staypointMinDurationMinutes);
    }

    private double getStopSpeedThreshold(TimelineConfig config) {
        Double threshold = config.getStaypointVelocityThreshold();
        return threshold != null ? threshold : 2;
    }

    /**
     * Check for arrival/stopping during a trip using flexible criteria.
     * Handles both sustained stops (traffic avoidance) and immediate arrivals (destination reached).
     */
    private boolean isSustainedStopInTrip(UserState userState, TimelineConfig config) {
        List<GPSPoint> activePoints = userState.copyActivePoints();
        if (activePoints.size() < 3) {  // Need at least 3 points
            return false;
        }

        double stopSpeedThreshold = getStopSpeedThreshold(config);
        double stayRadius = getStayRadius(config);

        // Get recent points for analysis (last 3-4 points)
        int recentPointsToCheck = Math.min(3, activePoints.size());
        List<GPSPoint> recentPoints = activePoints.subList(activePoints.size() - recentPointsToCheck, activePoints.size());

        // Check 1: Arrival Detection - Are recent points spatially clustered with low speed?
        GPSPoint lastPoint = recentPoints.get(recentPoints.size() - 1);
        boolean spatiallyClusteredAndSlow = recentPoints.stream()
                .allMatch(p -> p.distanceTo(lastPoint) <= stayRadius && p.getSpeed() <= stopSpeedThreshold);

        if (spatiallyClusteredAndSlow) {
            Duration clusterDuration = Duration.between(recentPoints.get(0).getTimestamp(), lastPoint.getTimestamp());
            // Much more flexible for arrivals - 30 seconds is enough if spatially clustered
            boolean isArrival = clusterDuration.compareTo(Duration.ofSeconds(90)) >= 0;

            if (isArrival) {
                return true; // Clear arrival detected
            }
        }

        // Check 2: Sustained Stop Detection - For traffic light avoidance (more conservative)
        // Only if we have enough points and they're consistently slow for longer period
        if (recentPoints.size() >= 2) {
            boolean allRecentSlow = recentPoints.stream()
                    .allMatch(p -> p.getSpeed() < stopSpeedThreshold);

            if (allRecentSlow) {
                Duration slowDuration = Duration.between(recentPoints.get(0).getTimestamp(), lastPoint.getTimestamp());
                boolean sustainedStop = slowDuration.compareTo(Duration.ofMinutes(1)) >= 0; // Reduced from 2 to 1 minute

                return sustainedStop;
            }
        }

        return false;
    }


    /**
     * Calculate current stay duration from active points (extracted for reuse).
     */
    private Duration calculateCurrentStayDuration(UserState userState) {
        GPSPoint firstPoint = userState.getFirstActivePoint();
        GPSPoint lastPoint = userState.getLastActivePoint();

        if (firstPoint == null || lastPoint == null) {
            return Duration.ZERO;
        }

        return Duration.between(firstPoint.getTimestamp(), lastPoint.getTimestamp());
    }

    /**
     * Filter GPS points based on accuracy threshold to improve data quality.
     * Points with accuracy exceeding the threshold are filtered out to prevent
     * unreliable GPS data from affecting timeline processing.
     *
     * @param points the GPS points to filter
     * @param config timeline configuration containing accuracy threshold
     * @return filtered list of GPS points with acceptable accuracy
     */
    private List<GPSPoint> filterPointsByAccuracy(List<GPSPoint> points, TimelineConfig config) {
        if (points == null || points.isEmpty()) {
            return points;
        }

        Double maxAccuracyThreshold = config.getStaypointMaxAccuracyThreshold();
        if (maxAccuracyThreshold == null || maxAccuracyThreshold <= 0) {
            // No filtering if threshold is not set or invalid
            return points;
        }

        List<GPSPoint> filteredPoints = points.stream()
                .filter(point -> point.getAccuracy() <= maxAccuracyThreshold)
                .toList();

        if (filteredPoints.size() != points.size()) {
            log.debug("Filtered {} GPS points due to poor accuracy (threshold: {}m). Remaining: {}/{}",
                    points.size() - filteredPoints.size(), maxAccuracyThreshold,
                    filteredPoints.size(), points.size());
        }

        return filteredPoints;
    }
}
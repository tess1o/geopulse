package org.github.tess1o.geopulse.streaming.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.ProcessorMode;
import org.github.tess1o.geopulse.streaming.model.domain.UserState;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates whether a data gap can be treated as stay continuity instead of creating a DataGap.
 *
 * This service intentionally performs no event finalization and does not mutate the caller's state.
 * It returns an internal plan that the engine can apply.
 */
@Slf4j
@ApplicationScoped
class GapStayInferenceService {

    private static final Duration MAX_IN_TRIP_LOCAL_EXCURSION_DURATION = Duration.ofMinutes(30);
    private static final double IN_TRIP_LOCAL_EXCURSION_RADIUS_MULTIPLIER = 2;

    @Inject
    TripStopHeuristicsService tripStopHeuristicsService;

    GapStayInferencePlan tryInfer(GPSPoint currentPoint, UserState userState,
                                  TimelineConfig config, Duration gapDuration) {
        Boolean enabled = config.getGapStayInferenceEnabled();
        if (enabled == null || !enabled) {
            log.debug("Gap stay inference is disabled (enabled={})", enabled);
            return GapStayInferencePlan.none();
        }

        if (!userState.hasActivePoints()) {
            log.debug("No active points for gap stay inference comparison");
            return GapStayInferencePlan.none();
        }

        Integer maxGapHours = config.getGapStayInferenceMaxGapHours();
        if (maxGapHours != null && maxGapHours > 0) {
            long gapHours = gapDuration.toHours();
            if (gapHours > maxGapHours) {
                log.debug("Gap duration {}h exceeds max allowed {}h for stay inference",
                        gapHours, maxGapHours);
                return GapStayInferencePlan.none();
            }
        }

        ProcessorMode mode = userState.getCurrentMode();
        if (mode == ProcessorMode.UNKNOWN) {
            log.debug("Gap stay inference not applicable for mode: {}", mode);
            return GapStayInferencePlan.none();
        }

        int stayRadiusMeters = tripStopHeuristicsService.getStayRadiusMeters(config);

        if (mode == ProcessorMode.IN_TRIP) {
            GapStayInferencePlan localTripPlan =
                    tryInferForShortLocalTrip(currentPoint, userState, stayRadiusMeters, gapDuration);
            if (localTripPlan.isInferred()) {
                return localTripPlan;
            }
            return tryInferFromTripTailArrival(currentPoint, userState, config, stayRadiusMeters, gapDuration);
        }

        return tryInferForStayModes(currentPoint, userState, stayRadiusMeters, gapDuration, mode);
    }

    private GapStayInferencePlan tryInferForStayModes(GPSPoint currentPoint, UserState userState,
                                                      int stayRadiusMeters, Duration gapDuration,
                                                      ProcessorMode mode) {
        GPSPoint centroid = userState.calculateCentroid();
        if (centroid == null) {
            log.debug("Could not calculate centroid for gap stay inference");
            return GapStayInferencePlan.none();
        }

        double distance = centroid.distanceTo(currentPoint);
        if (distance > stayRadiusMeters) {
            log.debug("Distance {}m from centroid exceeds stay radius {}m - creating gap instead",
                    String.format("%.1f", distance), stayRadiusMeters);
            return GapStayInferencePlan.none();
        }

        log.info("Gap stay inference conditions met: mode={}, gap={}h, distance={}m (radius={}m)",
                mode, gapDuration.toHours(), String.format("%.1f", distance), stayRadiusMeters);
        return GapStayInferencePlan.continueExistingStay();
    }

    private GapStayInferencePlan tryInferForShortLocalTrip(GPSPoint currentPoint, UserState userState,
                                                           int stayRadiusMeters, Duration gapDuration) {
        List<GPSPoint> activeTripPoints = userState.copyActivePoints();
        if (activeTripPoints.size() < 2) {
            log.debug("Gap stay inference not applicable for IN_TRIP with fewer than 2 active points");
            return GapStayInferencePlan.none();
        }

        GPSPoint firstTripPoint = activeTripPoints.get(0);
        GPSPoint lastTripPoint = activeTripPoints.get(activeTripPoints.size() - 1);
        if (firstTripPoint.getTimestamp() == null || lastTripPoint.getTimestamp() == null) {
            log.debug("Gap stay inference not applicable for IN_TRIP with missing timestamps");
            return GapStayInferencePlan.none();
        }

        Duration pendingTripDuration = Duration.between(firstTripPoint.getTimestamp(), lastTripPoint.getTimestamp());
        if (pendingTripDuration.compareTo(MAX_IN_TRIP_LOCAL_EXCURSION_DURATION) > 0) {
            log.debug("Pending IN_TRIP duration {} exceeds local excursion limit {} for gap stay inference",
                    pendingTripDuration, MAX_IN_TRIP_LOCAL_EXCURSION_DURATION);
            return GapStayInferencePlan.none();
        }

        double resumeDistance = lastTripPoint.distanceTo(currentPoint);
        if (resumeDistance > stayRadiusMeters) {
            log.debug("IN_TRIP resume distance {}m exceeds stay radius {}m - creating gap instead",
                    String.format("%.1f", resumeDistance), stayRadiusMeters);
            return GapStayInferencePlan.none();
        }

        double maxDistanceFromTripEnd = 0.0;
        for (GPSPoint tripPoint : activeTripPoints) {
            maxDistanceFromTripEnd = Math.max(maxDistanceFromTripEnd, tripPoint.distanceTo(lastTripPoint));
        }

        double localExcursionLimitMeters = stayRadiusMeters * IN_TRIP_LOCAL_EXCURSION_RADIUS_MULTIPLIER;
        if (maxDistanceFromTripEnd > localExcursionLimitMeters) {
            log.debug("Pending IN_TRIP spread {}m exceeds local excursion limit {}m (radius={}m x {})",
                    String.format("%.1f", maxDistanceFromTripEnd),
                    String.format("%.1f", localExcursionLimitMeters),
                    stayRadiusMeters,
                    IN_TRIP_LOCAL_EXCURSION_RADIUS_MULTIPLIER);
            return GapStayInferencePlan.none();
        }

        List<GPSPoint> localPoints = collectPointsWithinRadius(activeTripPoints, lastTripPoint, stayRadiusMeters);

        log.info("Gap stay inference conditions met for short local IN_TRIP: gap={}h, pendingTrip={}, " +
                        "resumeDistance={}m, spread={}m (radius={}m)",
                gapDuration.toHours(),
                pendingTripDuration,
                String.format("%.1f", resumeDistance),
                String.format("%.1f", maxDistanceFromTripEnd),
                stayRadiusMeters);
        return GapStayInferencePlan.replaceWithConfirmedStay(localPoints);
    }

    private GapStayInferencePlan tryInferFromTripTailArrival(GPSPoint currentPoint, UserState userState,
                                                             TimelineConfig config, int stayRadiusMeters,
                                                             Duration gapDuration) {
        List<GPSPoint> activeTripPoints = userState.copyActivePoints();
        TripStopHeuristicsService.TailArrivalClusterMatch tailMatch =
                tripStopHeuristicsService.findGapTailArrivalClusterMatch(activeTripPoints, currentPoint, config);
        if (!tailMatch.isMatched()) {
            return GapStayInferencePlan.none();
        }
        int stopClusterStartIndex = tailMatch.getStartIndex();

        List<GPSPoint> tripPoints = new ArrayList<>(activeTripPoints.subList(0, stopClusterStartIndex));
        List<GPSPoint> stoppedTailPoints = new ArrayList<>(activeTripPoints.subList(stopClusterStartIndex, activeTripPoints.size()));

        log.info("Gap stay inference conditions met for IN_TRIP tail arrival: gap={}h, tailPoints={}, tailDuration={}, " +
                        "resumeDistance={}m (radius={}m), finalizedTripPoints={}",
                gapDuration.toHours(),
                stoppedTailPoints.size(),
                tailMatch.getTailDuration(),
                String.format("%.1f", tailMatch.getResumeDistanceMeters()),
                tailMatch.getStayRadiusMeters(),
                tripPoints.size());

        return GapStayInferencePlan.finalizeTripAndReplaceWithConfirmedStay(tripPoints, stoppedTailPoints);
    }

    private List<GPSPoint> collectPointsWithinRadius(List<GPSPoint> points, GPSPoint anchorPoint, int radiusMeters) {
        List<GPSPoint> localPoints = new ArrayList<>();
        for (GPSPoint point : points) {
            if (point.distanceTo(anchorPoint) <= radiusMeters) {
                localPoints.add(point);
            }
        }
        if (localPoints.isEmpty()) {
            localPoints.add(anchorPoint);
        }
        return localPoints;
    }

}

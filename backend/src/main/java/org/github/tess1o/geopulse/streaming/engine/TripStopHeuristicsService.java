package org.github.tess1o.geopulse.streaming.engine;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;

import java.time.Duration;
import java.util.List;

/**
 * Shared stop/arrival heuristics used by trip processing and gap stay inference.
 * Centralizes threshold defaults and cluster checks to avoid drift between code paths.
 */
@ApplicationScoped
class TripStopHeuristicsService {

    private static final int DEFAULT_STAY_RADIUS_METERS = 50;
    private static final Duration DEFAULT_ARRIVAL_DETECTION_DURATION = Duration.ofSeconds(90);
    private static final Duration DEFAULT_SUSTAINED_STOP_DURATION = Duration.ofSeconds(60);
    private static final int DEFAULT_TRIP_ARRIVAL_MIN_POINTS = 3;
    private static final double DEFAULT_STOP_SPEED_THRESHOLD = 2.0;

    private static final Duration MIN_GAP_TAIL_STOP_DURATION = Duration.ofSeconds(30);
    private static final Duration MAX_GAP_TAIL_STOP_DURATION = Duration.ofSeconds(60);

    TripStopDetection detectTripStopFromRecentWindow(List<GPSPoint> activePoints, TimelineConfig config) {
        int minPoints = getTripArrivalMinPoints(config);
        if (activePoints == null || activePoints.size() < minPoints) {
            return TripStopDetection.noStop();
        }

        double stopSpeedThreshold = getStopSpeedThreshold(config);
        double stayRadius = getStayRadiusMeters(config);

        int recentPointsToCheck = Math.min(minPoints, activePoints.size());
        int stoppedClusterStartIndex = activePoints.size() - recentPointsToCheck;
        List<GPSPoint> recentPoints = activePoints.subList(stoppedClusterStartIndex, activePoints.size());
        GPSPoint lastPoint = recentPoints.get(recentPoints.size() - 1);

        boolean spatiallyClusteredAndSlow = recentPoints.stream()
                .allMatch(p -> p.distanceTo(lastPoint) <= stayRadius && p.getSpeed() <= stopSpeedThreshold);

        if (spatiallyClusteredAndSlow) {
            Duration clusterDuration = Duration.between(recentPoints.get(0).getTimestamp(), lastPoint.getTimestamp());
            if (clusterDuration.compareTo(getArrivalDetectionDuration(config)) >= 0) {
                return TripStopDetection.stopDetected(stoppedClusterStartIndex);
            }
        }

        if (recentPoints.size() >= 2) {
            boolean allRecentSlow = recentPoints.stream()
                    .allMatch(p -> p.getSpeed() < stopSpeedThreshold);

            if (allRecentSlow) {
                Duration slowDuration = Duration.between(recentPoints.get(0).getTimestamp(), lastPoint.getTimestamp());
                if (slowDuration.compareTo(getSustainedStopDuration(config)) >= 0) {
                    return TripStopDetection.stopDetected(stoppedClusterStartIndex);
                }
            }
        }

        return TripStopDetection.noStop();
    }

    TailArrivalClusterMatch findGapTailArrivalClusterMatch(List<GPSPoint> activeTripPoints,
                                                           GPSPoint postGapPoint,
                                                           TimelineConfig config) {
        if (activeTripPoints == null || activeTripPoints.isEmpty()) {
            return TailArrivalClusterMatch.noMatch();
        }

        int stayRadiusMeters = getStayRadiusMeters(config);
        double stopSpeedThreshold = getStopSpeedThreshold(config);
        GPSPoint lastTripPoint = activeTripPoints.get(activeTripPoints.size() - 1);

        if (lastTripPoint.getSpeed() > stopSpeedThreshold) {
            return TailArrivalClusterMatch.noMatch();
        }
        if (postGapPoint.getSpeed() > stopSpeedThreshold) {
            return TailArrivalClusterMatch.noMatch();
        }

        double resumeDistance = lastTripPoint.distanceTo(postGapPoint);
        if (resumeDistance > stayRadiusMeters) {
            return TailArrivalClusterMatch.noMatch();
        }

        int startIndex = activeTripPoints.size() - 1;
        while (startIndex > 0) {
            GPSPoint candidate = activeTripPoints.get(startIndex - 1);
            if (candidate.distanceTo(lastTripPoint) > stayRadiusMeters || candidate.getSpeed() > stopSpeedThreshold) {
                break;
            }
            startIndex--;
        }

        int tailPointCount = activeTripPoints.size() - startIndex;
        if (tailPointCount < getTripArrivalMinPoints(config)) {
            return TailArrivalClusterMatch.noMatch();
        }

        GPSPoint firstTailPoint = activeTripPoints.get(startIndex);
        if (firstTailPoint.getTimestamp() == null || lastTripPoint.getTimestamp() == null) {
            return TailArrivalClusterMatch.noMatch();
        }

        Duration tailDuration = Duration.between(firstTailPoint.getTimestamp(), lastTripPoint.getTimestamp());
        Duration requiredTailDuration = getGapTailStopMinDuration(config);
        if (tailDuration.compareTo(requiredTailDuration) < 0) {
            return TailArrivalClusterMatch.noMatch();
        }

        return TailArrivalClusterMatch.match(startIndex, stayRadiusMeters, resumeDistance, tailDuration);
    }

    int getStayRadiusMeters(TimelineConfig config) {
        Integer radiusMeters = config.getStaypointRadiusMeters();
        return radiusMeters != null ? radiusMeters : DEFAULT_STAY_RADIUS_METERS;
    }

    double getStopSpeedThreshold(TimelineConfig config) {
        Double threshold = config.getStaypointVelocityThreshold();
        return threshold != null ? threshold : DEFAULT_STOP_SPEED_THRESHOLD;
    }

    Duration getArrivalDetectionDuration(TimelineConfig config) {
        Integer seconds = config.getTripArrivalDetectionMinDurationSeconds();
        return seconds != null ? Duration.ofSeconds(seconds) : DEFAULT_ARRIVAL_DETECTION_DURATION;
    }

    Duration getSustainedStopDuration(TimelineConfig config) {
        Integer seconds = config.getTripSustainedStopMinDurationSeconds();
        return seconds != null ? Duration.ofSeconds(seconds) : DEFAULT_SUSTAINED_STOP_DURATION;
    }

    int getTripArrivalMinPoints(TimelineConfig config) {
        Integer minPoints = config.getTripArrivalMinPoints();
        return minPoints != null ? minPoints : DEFAULT_TRIP_ARRIVAL_MIN_POINTS;
    }

    Duration getGapTailStopMinDuration(TimelineConfig config) {
        Duration relaxed = getArrivalDetectionDuration(config).dividedBy(2);
        if (relaxed.compareTo(MIN_GAP_TAIL_STOP_DURATION) < 0) {
            return MIN_GAP_TAIL_STOP_DURATION;
        }
        if (relaxed.compareTo(MAX_GAP_TAIL_STOP_DURATION) > 0) {
            return MAX_GAP_TAIL_STOP_DURATION;
        }
        return relaxed;
    }

    static final class TripStopDetection {
        private final boolean stopDetected;
        private final int stoppedClusterStartIndex;

        private TripStopDetection(boolean stopDetected, int stoppedClusterStartIndex) {
            this.stopDetected = stopDetected;
            this.stoppedClusterStartIndex = stoppedClusterStartIndex;
        }

        static TripStopDetection noStop() {
            return new TripStopDetection(false, -1);
        }

        static TripStopDetection stopDetected(int startIndex) {
            return new TripStopDetection(true, startIndex);
        }

        boolean isStopDetected() {
            return stopDetected;
        }

        int getStoppedClusterStartIndex() {
            return stoppedClusterStartIndex;
        }
    }

    static final class TailArrivalClusterMatch {
        private static final TailArrivalClusterMatch NONE =
                new TailArrivalClusterMatch(false, -1, 0, 0.0, Duration.ZERO);

        private final boolean matched;
        private final int startIndex;
        private final int stayRadiusMeters;
        private final double resumeDistanceMeters;
        private final Duration tailDuration;

        private TailArrivalClusterMatch(boolean matched, int startIndex, int stayRadiusMeters,
                                        double resumeDistanceMeters, Duration tailDuration) {
            this.matched = matched;
            this.startIndex = startIndex;
            this.stayRadiusMeters = stayRadiusMeters;
            this.resumeDistanceMeters = resumeDistanceMeters;
            this.tailDuration = tailDuration;
        }

        static TailArrivalClusterMatch noMatch() {
            return NONE;
        }

        static TailArrivalClusterMatch match(int startIndex, int stayRadiusMeters,
                                             double resumeDistanceMeters, Duration tailDuration) {
            return new TailArrivalClusterMatch(true, startIndex, stayRadiusMeters, resumeDistanceMeters, tailDuration);
        }

        boolean isMatched() {
            return matched;
        }

        int getStartIndex() {
            return startIndex;
        }

        int getStayRadiusMeters() {
            return stayRadiusMeters;
        }

        double getResumeDistanceMeters() {
            return resumeDistanceMeters;
        }

        Duration getTailDuration() {
            return tailDuration;
        }
    }
}

package org.github.tess1o.geopulse.streaming.engine;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;

import java.time.Duration;
import java.util.List;

/**
 * Shared stop/arrival heuristics used by trip processing and gap stay inference.
 * Centralizes threshold defaults and cluster checks to avoid drift between code paths.
 */
@ApplicationScoped
@Slf4j
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
            log.debug("Trip stop rejected - insufficient points: available={}, required={}",
                    activePoints == null ? 0 : activePoints.size(), minPoints);
            return TripStopDetection.noStop();
        }

        double stopSpeedThreshold = getStopSpeedThreshold(config);
        double stayRadius = getStayRadiusMeters(config);

        TripStopDetection clusteredStop = detectClusteredStop(activePoints, minPoints, stopSpeedThreshold, stayRadius, config);
        if (clusteredStop.isStopDetected()) {
            return clusteredStop;
        }

        return detectSustainedSlowStop(activePoints, minPoints, stopSpeedThreshold, config);
    }

    private TripStopDetection detectClusteredStop(List<GPSPoint> activePoints,
                                                  int minPoints,
                                                  double stopSpeedThreshold,
                                                  double stayRadius,
                                                  TimelineConfig config) {
        if (activePoints.size() < minPoints + 1) {
            log.debug("Full-cluster stop rejected - insufficient points before cluster: available={}, required={}",
                    activePoints.size(), minPoints + 1);
            return TripStopDetection.noStop();
        }

        GPSPoint lastPoint = activePoints.get(activePoints.size() - 1);
        if (lastPoint.getSpeed() > stopSpeedThreshold) {
            log.debug("Full-cluster stop rejected - last point speed {} exceeds threshold {} at {}",
                    lastPoint.getSpeed(), stopSpeedThreshold, lastPoint.getTimestamp());
            return TripStopDetection.noStop();
        }

        int stoppedClusterStartIndex = activePoints.size() - 1;
        while (stoppedClusterStartIndex > 0) {
            GPSPoint candidate = activePoints.get(stoppedClusterStartIndex - 1);
            if (candidate.getSpeed() > stopSpeedThreshold || candidate.distanceTo(lastPoint) > stayRadius) {
                break;
            }
            stoppedClusterStartIndex--;
        }

        int clusterPointCount = activePoints.size() - stoppedClusterStartIndex;
        if (clusterPointCount < minPoints || stoppedClusterStartIndex == 0) {
            log.debug("Full-cluster stop rejected - cluster points={}, required={}, startIndex={}",
                    clusterPointCount, minPoints, stoppedClusterStartIndex);
            return TripStopDetection.noStop();
        }

        boolean hasMovingPointBeforeCluster = activePoints.subList(0, stoppedClusterStartIndex).stream()
                .anyMatch(p -> p.getSpeed() > stopSpeedThreshold);
        if (!hasMovingPointBeforeCluster) {
            log.debug("Full-cluster stop rejected - no pre-cluster point exceeds speed threshold {} before cluster at {}",
                    stopSpeedThreshold, activePoints.get(stoppedClusterStartIndex).getTimestamp());
            return TripStopDetection.noStop();
        }

        GPSPoint firstStoppedPoint = activePoints.get(stoppedClusterStartIndex);
        if (firstStoppedPoint.getTimestamp() == null || lastPoint.getTimestamp() == null) {
            log.debug("Full-cluster stop rejected - missing timestamps for cluster start/end");
            return TripStopDetection.noStop();
        }

        Duration clusterDuration = Duration.between(firstStoppedPoint.getTimestamp(), lastPoint.getTimestamp());
        Duration sustainedStopDuration = getSustainedStopDuration(config);
        Duration arrivalDetectionDuration = getArrivalDetectionDuration(config);
        if (clusterDuration.compareTo(sustainedStopDuration) >= 0 ||
                clusterDuration.compareTo(arrivalDetectionDuration) >= 0) {
            log.debug("Full-cluster stop accepted - start={}, end={}, duration={}, points={}, speedThreshold={}, radius={}",
                    firstStoppedPoint.getTimestamp(), lastPoint.getTimestamp(), clusterDuration,
                    clusterPointCount, stopSpeedThreshold, stayRadius);
            return TripStopDetection.stopDetected(stoppedClusterStartIndex);
        }

        log.debug("Full-cluster stop rejected - duration {} below sustained={} and arrival={} for start={}, end={}, points={}",
                clusterDuration, sustainedStopDuration, arrivalDetectionDuration,
                firstStoppedPoint.getTimestamp(), lastPoint.getTimestamp(), clusterPointCount);
        return TripStopDetection.noStop();
    }

    private TripStopDetection detectSustainedSlowStop(List<GPSPoint> activePoints,
                                                      int minPoints,
                                                      double stopSpeedThreshold,
                                                      TimelineConfig config) {
        int stoppedClusterStartIndex = activePoints.size() - minPoints;
        List<GPSPoint> recentPoints = activePoints.subList(stoppedClusterStartIndex, activePoints.size());

        boolean allRecentSlow = recentPoints.stream()
                .allMatch(p -> p.getSpeed() <= stopSpeedThreshold);
        if (!allRecentSlow) {
            log.debug("Sustained-stop fallback rejected - not all last {} points are <= speed threshold {}",
                    minPoints, stopSpeedThreshold);
            return TripStopDetection.noStop();
        }

        GPSPoint firstStoppedPoint = recentPoints.getFirst();
        GPSPoint lastPoint = recentPoints.getLast();
        if (firstStoppedPoint.getTimestamp() == null || lastPoint.getTimestamp() == null) {
            log.debug("Sustained-stop fallback rejected - missing timestamps for recent window");
            return TripStopDetection.noStop();
        }

        Duration slowDuration = Duration.between(firstStoppedPoint.getTimestamp(), lastPoint.getTimestamp());
        Duration requiredDuration = getSustainedStopDuration(config);
        if (slowDuration.compareTo(requiredDuration) >= 0) {
            log.debug("Sustained-stop fallback accepted - start={}, end={}, duration={}, points={}, speedThreshold={}",
                    firstStoppedPoint.getTimestamp(), lastPoint.getTimestamp(), slowDuration,
                    recentPoints.size(), stopSpeedThreshold);
            return TripStopDetection.stopDetected(stoppedClusterStartIndex);
        }

        log.debug("Sustained-stop fallback rejected - duration {} below required {} for start={}, end={}, points={}",
                slowDuration, requiredDuration, firstStoppedPoint.getTimestamp(),
                lastPoint.getTimestamp(), recentPoints.size());
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

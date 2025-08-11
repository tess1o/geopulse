package org.github.tess1o.geopulse.timeline.detection.stays;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.timeline.model.TimelineStayPoint;
import org.github.tess1o.geopulse.timeline.model.TrackPoint;
import org.github.tess1o.geopulse.timeline.core.SpatialCalculationService;
import org.github.tess1o.geopulse.timeline.core.TimelineValidationService;
import org.github.tess1o.geopulse.timeline.core.VelocityAnalysisService;
import org.github.tess1o.geopulse.timeline.util.TimelineConstants;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
@Slf4j
public class StayPointDetectorEnhanced implements StayPointDetector {

    private final SpatialCalculationService spatialCalculationService;
    private final TimelineValidationService validationService;
    private final VelocityAnalysisService velocityAnalysisService;

    @Inject
    public StayPointDetectorEnhanced(SpatialCalculationService spatialCalculationService,
                                   TimelineValidationService validationService,
                                   VelocityAnalysisService velocityAnalysisService) {
        this.spatialCalculationService = spatialCalculationService;
        this.validationService = validationService;
        this.velocityAnalysisService = velocityAnalysisService;
    }

    public List<TimelineStayPoint> detectStayPoints(TimelineConfig timelineConfig, List<TrackPoint> points) {
        if (points == null || points.size() < 2) {
            return new ArrayList<>();
        }

        Duration tripDurationThreshold = Duration.ofMinutes(timelineConfig.getTripMinDurationMinutes());
        List<TimelineStayPoint> rawStayPoints = detectRawStayPoints(timelineConfig, points, tripDurationThreshold);

        //return rawStayPoints;
        // Merge nearby stay points that are likely GPS drift
         return mergeNearbyStayPoints(rawStayPoints, timelineConfig);
    }

    private List<TimelineStayPoint> detectRawStayPoints(TimelineConfig timelineConfig, List<TrackPoint> points, Duration tripDurationThreshold) {
        List<TimelineStayPoint> timelineStayPoints = new ArrayList<>();

        int i = 0;
        while (i < points.size()) {
            // Find the end index of potential cluster using spatial calculation service
            int j = spatialCalculationService.findClusterEndIndex(points, i, timelineConfig);

            if (j - 1 > i) {
                List<TrackPoint> cluster = points.subList(i, j);

                // Calculate core cluster duration (existing logic)
                Instant coreStart = cluster.getFirst().getTimestamp();
                Instant coreEnd = cluster.getLast().getTimestamp();
                Duration coreDuration = Duration.between(coreStart, coreEnd);

                if (coreDuration.compareTo(tripDurationThreshold) >= 0) {
                    // Apply accuracy and velocity validation using validation service
                    if (!validationService.passesAccuracyAndVelocityChecks(cluster, timelineConfig, velocityAnalysisService)) {
                        i++;
                        continue;
                    }

                    // Enhanced: Find actual arrival and departure times
                    TransitionTimes transitionTimes = findTransitionTimes(points, i, j - 1, timelineConfig);

                    // Calculate weighted centroid
                    double[] centroid = spatialCalculationService.calculateWeightedCentroid(cluster);

                    Duration actualDuration = Duration.between(transitionTimes.arrival, transitionTimes.departure);

                    timelineStayPoints.add(new TimelineStayPoint(
                            centroid[1], centroid[0], // lon, lat
                            transitionTimes.arrival,
                            transitionTimes.departure,
                            actualDuration
                    ));
                    i = j;
                    continue;
                }
            }
            i++;
        }
        return timelineStayPoints;
    }

    private List<TimelineStayPoint> mergeNearbyStayPoints(List<TimelineStayPoint> rawStayPoints, TimelineConfig config) {
        if (rawStayPoints.isEmpty()) {
            return rawStayPoints;
        }

        List<TimelineStayPoint> merged = new ArrayList<>();
        List<TimelineStayPoint> sortedPoints = new ArrayList<>(rawStayPoints);
        sortedPoints.sort(Comparator.comparing(TimelineStayPoint::startTime));

        TimelineStayPoint current = sortedPoints.getFirst();

        for (int i = 1; i < sortedPoints.size(); i++) {
            TimelineStayPoint next = sortedPoints.get(i);

            // Check if stay points should be merged
            if (shouldMergeStayPoints(current, next, config)) {
                // Merge the stay points
                current = mergeStayPoints(current, next);
            } else {
                // Keep current and move to next
                merged.add(current);
                current = next;
            }
        }

        // Add the last stay point
        merged.add(current);

        return merged;
    }

    private boolean shouldMergeStayPoints(TimelineStayPoint stay1, TimelineStayPoint stay2, TimelineConfig config) {
        // Calculate distance between stay points
        double distance = spatialCalculationService.calculateDistance(
                stay1.latitude(), stay1.longitude(),
                stay2.latitude(), stay2.longitude()
        );

        // Define merge criteria
        double maxMergeDistance = config.getMergeMaxDistanceMeters();
        // Check distance criterion
        if (distance > maxMergeDistance) {
            return false;
        }

        // Check time gap criterion
        Duration maxTimeBetween = Duration.of(config.getMergeMaxTimeGapMinutes(), ChronoUnit.MINUTES);
        Duration timeBetween = Duration.between(stay1.endTime(), stay2.startTime());
        if (timeBetween.compareTo(maxTimeBetween) > 0) {
            return false;
        }

        // Additional criterion: if time between is very short (< 2 minutes), definitely merge
        if (timeBetween.compareTo(Duration.ofMinutes(TimelineConstants.MERGE_SHORT_TIME_GAP_MINUTES)) <= 0) {
            return true;
        }

        // For slightly longer gaps, only merge if distance is very small (GPS drift range)
        return distance <= TimelineConstants.GPS_DRIFT_DISTANCE_METERS;
    }

    private TimelineStayPoint mergeStayPoints(TimelineStayPoint stay1, TimelineStayPoint stay2) {
        // Calculate weighted centroid based on duration
        long duration1 = stay1.duration().toSeconds();
        long duration2 = stay2.duration().toSeconds();
        long totalDuration = duration1 + duration2;

        double weight1 = (double) duration1 / totalDuration;
        double weight2 = (double) duration2 / totalDuration;

        double mergedLat = stay1.latitude() * weight1 + stay2.latitude() * weight2;
        double mergedLon = stay1.longitude() * weight1 + stay2.longitude() * weight2;

        // Use earliest start and latest end time
        Instant mergedStart = stay1.startTime().isBefore(stay2.startTime()) ?
                stay1.startTime() : stay2.startTime();
        Instant mergedEnd = stay1.endTime().isAfter(stay2.endTime()) ?
                stay1.endTime() : stay2.endTime();

        Duration mergedDuration = Duration.between(mergedStart, mergedEnd);

        return new TimelineStayPoint(mergedLon, mergedLat, mergedStart, mergedEnd, mergedDuration);
    }


    private TransitionTimes findTransitionTimes(List<TrackPoint> allPoints, int clusterStart, int clusterEnd, TimelineConfig config) {
        // Find arrival time by looking backwards for velocity transition
        Instant arrivalTime = findArrivalTransition(allPoints, clusterStart, config);

        // Find departure time by looking forwards for velocity transition
        Instant departureTime = findDepartureTransition(allPoints, clusterEnd, config);

        return new TransitionTimes(arrivalTime, departureTime);
    }

    private Instant findArrivalTransition(List<TrackPoint> points, int clusterStart, TimelineConfig config) {
        double velocityThreshold = config.getStaypointVelocityThreshold();
        int lookbackWindow = Math.min(TimelineConstants.VELOCITY_LOOKBACK_WINDOW, clusterStart);

        // Start from cluster beginning and look backwards
        for (int i = clusterStart; i >= Math.max(0, clusterStart - lookbackWindow); i--) {
            TrackPoint point = points.get(i);

            if (point.getVelocity() != null && point.getVelocity() > velocityThreshold) {
                // Found transition point - return timestamp of next point
                int transitionIndex = Math.min(i + 1, clusterStart);
                return points.get(transitionIndex).getTimestamp();
            }
        }

        // Enhanced fallback: look for the first point in cluster with low velocity
        for (int i = Math.max(0, clusterStart - lookbackWindow); i <= clusterStart; i++) {
            TrackPoint point = points.get(i);
            if (point.getVelocity() != null && point.getVelocity() <= velocityThreshold) {
                return point.getTimestamp();
            }
        }

        // Final fallback to cluster start
        return points.get(clusterStart).getTimestamp();
    }

    private Instant findDepartureTransition(List<TrackPoint> points, int clusterEnd, TimelineConfig config) {
        double velocityThreshold = config.getStaypointVelocityThreshold();
        int lookforwardWindow = Math.min(TimelineConstants.VELOCITY_LOOKFORWARD_WINDOW, points.size() - clusterEnd - 1);

        // Start from cluster end and look forwards
        for (int i = clusterEnd + 1; i < Math.min(points.size(), clusterEnd + 1 + lookforwardWindow); i++) {
            TrackPoint point = points.get(i);

            if (point.getVelocity() != null && point.getVelocity() > velocityThreshold) {
                return point.getTimestamp();
            }
        }

        // Enhanced fallback: look for the last point in cluster with low velocity
        for (int i = Math.min(points.size() - 1, clusterEnd + lookforwardWindow); i >= clusterEnd; i--) {
            TrackPoint point = points.get(i);
            if (point.getVelocity() != null && point.getVelocity() <= velocityThreshold) {
                return point.getTimestamp();
            }
        }

        // Final fallback to cluster end
        return points.get(clusterEnd).getTimestamp();
    }


    private record TransitionTimes(Instant arrival, Instant departure) {
    }
}

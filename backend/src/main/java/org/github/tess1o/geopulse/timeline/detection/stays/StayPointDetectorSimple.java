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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * StayPointDetectorOriginal detects stay points from a sequence of GPS track points.
 * <p>
 * A stay point is defined as a location where the user remains within a certain distance
 * threshold for a minimum amount of time. The detection logic also incorporates
 * accuracy and velocity to improve robustness against GPS noise or cell tower-based location drift.
 * <p>
 * Algorithm overview:
 * <ul>
 *   <li>Iterate through the list of GPS points using a sliding window approach.</li>
 *   <li>For each candidate cluster of points:
 *     <ul>
 *       <li>Check if the spatial distance between the first and last point is within the distance threshold.</li>
 *       <li>Ensure the duration between them is greater than or equal to the time threshold.</li>
 *       <li>Skip points with bad accuracy (above accuracyThreshold).</li>
 *       <li>If the majority of the cluster is accurate (based on minAccuracyRatio), continue.</li>
 *       <li>Calculate average velocity. If below velocityThreshold, it qualifies as a stay point.</li>
 *     </ul>
 *   </li>
 *   <li>Calculate the weighted average of latitude and longitude (inversely weighted by accuracy) to determine the final stay point location.</li>
 * </ul>
 * <p>
 * This implementation ensures that occasional inaccurate or fast-moving points do not invalidate otherwise valid stay clusters,
 * improving detection in noisy urban environments or when switching between GPS and cellular sources.
 */
@ApplicationScoped
@Slf4j
public class StayPointDetectorSimple implements StayPointDetector {

    private final SpatialCalculationService spatialCalculationService;
    private final TimelineValidationService validationService;
    private final VelocityAnalysisService velocityAnalysisService;

    @Inject
    public StayPointDetectorSimple(SpatialCalculationService spatialCalculationService,
                                 TimelineValidationService validationService,
                                 VelocityAnalysisService velocityAnalysisService) {
        this.spatialCalculationService = spatialCalculationService;
        this.validationService = validationService;
        this.velocityAnalysisService = velocityAnalysisService;
    }

    /**
     * Detects stay points from the given list of track points.
     *
     * @param points A time-ordered list of GPS track points.
     * @return A list of {@link TimelineStayPoint} representing periods of significant location dwelling.
     */
    @Override
    public List<TimelineStayPoint> detectStayPoints(TimelineConfig timelineConfig, List<TrackPoint> points) {
        Duration tripDurationThreshold = Duration.ofMinutes(timelineConfig.getTripMinDurationMinutes());

        List<TimelineStayPoint> timelineStayPoints = new ArrayList<>();
        int i = 0;
        while (i < points.size()) {
            // Find the end index of potential cluster using spatial calculation service
            int j = spatialCalculationService.findClusterEndIndex(points, i, timelineConfig);

            if (j - 1 > i) {
                Instant start = points.get(i).getTimestamp();
                Instant end = points.get(j - 1).getTimestamp();
                Duration duration = Duration.between(start, end);

                if (duration.compareTo(tripDurationThreshold) >= 0) {
                    List<TrackPoint> cluster = points.subList(i, j);

                    // Apply accuracy and velocity validation using validation service
                    if (!validationService.passesAccuracyAndVelocityChecks(cluster, timelineConfig, velocityAnalysisService)) {
                        log.debug("Skipping cluster due to failed accuracy/velocity checks");
                        i++;
                        continue;
                    }

                    double[] centroid = spatialCalculationService.calculateWeightedCentroid(cluster);
                    double meanLat = centroid[0];
                    double meanLon = centroid[1];

                    timelineStayPoints.add(new TimelineStayPoint(meanLon, meanLat, start, end, duration));
                    i = j;
                    continue;
                }
            }
            i++;
        }
        return timelineStayPoints;
    }
}

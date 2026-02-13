package org.github.tess1o.geopulse.gps.service.simplification;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Service for applying GPS path simplification to timeline trips.
 * Integrates with the timeline configuration system to provide configurable
 * path simplification that reduces data size while preserving route accuracy.
 */
@ApplicationScoped
@Slf4j
public class PathSimplificationService {

    @Inject
    TimelineTripRepository timelineTripRepository;

    @Inject
    TimelineStayRepository timelineStayRepository;

    /**
     * Apply path simplification to a timeline trip based on configuration.
     *
     * @param trip The timeline trip to simplify
     * @param config Timeline configuration containing simplification settings
     * @return New trip with simplified path, or original trip if simplification is disabled
     */

    public List<? extends GpsPoint> simplify(List<? extends GpsPoint> points, TimelineConfig config) {
        if (!isSimplificationEnabled(config)) {
            log.debug("Path simplification is disabled, returning original trip");
            return points;
        }

        if (points == null || points.size() <= 2) {
            log.debug("Trip path has {} points, skipping simplification",
                    points != null ? points.size() : 0);
            return points;
        }

        double tripDistanceKm = calculateTripDistance(points);
        return simplifyPathWithFlexibleTolerance(points, tripDistanceKm, config);
    }

    /**
     * Simplify a GPS path with flexible tolerance based on trip characteristics.
     * This implements the context-aware simplification strategy mentioned in the proposal.
     *
     * @param originalPath Original GPS points
     * @param tripDistanceKm Trip distance in kilometers
     * @param config Timeline configuration
     * @return Simplified GPS points
     */
    private List<? extends GpsPoint> simplifyPathWithFlexibleTolerance(List<? extends GpsPoint> originalPath, 
                                                                       double tripDistanceKm, 
                                                                       TimelineConfig config) {
        double baseTolerance = config.getPathSimplificationTolerance();
        int maxPoints = config.getPathMaxPoints() != null ? config.getPathMaxPoints() : 0;
        boolean adaptive = config.getPathAdaptiveSimplification() != null ? 
                          config.getPathAdaptiveSimplification() : false;

        // Calculate flexible tolerance based on trip characteristics
        double tolerance = calculateFlexibleTolerance(tripDistanceKm, baseTolerance, adaptive);

        if (adaptive) {
            return GpsPathSimplifier.simplifyPathAdaptive(originalPath, tripDistanceKm, tolerance, maxPoints);
        } else {
            List<? extends GpsPoint> simplified = GpsPathSimplifier.simplifyPath(originalPath, tolerance);
            
            // Apply max points limit if specified
            if (maxPoints > 0 && simplified.size() > maxPoints) {
                // Increase tolerance iteratively until we meet the point limit
                double currentTolerance = tolerance;
                while (simplified.size() > maxPoints && currentTolerance < tolerance * 10) {
                    currentTolerance *= 1.5;
                    simplified = GpsPathSimplifier.simplifyPath(originalPath, currentTolerance);
                }
                log.debug("Applied max points limit ({}), final tolerance: {}m", maxPoints, String.format("%.1f", currentTolerance));
            }
            
            return simplified;
        }
    }

    /**
     * Calculate flexible tolerance based on trip distance and context.
     * Implements the intelligent tolerance calculation from the proposal:
     * - Short trips (<2km): Lower tolerance (higher accuracy) 
     * - Medium trips (2-10km): Base tolerance
     * - Long trips (>10km): Higher tolerance
     *
     * @param tripDistanceKm Trip distance in kilometers
     * @param baseTolerance Base tolerance from configuration
     * @param adaptive Whether adaptive mode is enabled
     * @return Calculated tolerance in meters
     */
    private double calculateFlexibleTolerance(double tripDistanceKm, double baseTolerance, boolean adaptive) {
        if (!adaptive) {
            return baseTolerance; // Use base tolerance if adaptive mode is disabled
        }

        // Implement flexible tolerance based on trip distance
        if (tripDistanceKm < 1.0) {
            // Very short trips: use much lower tolerance for better accuracy
            return Math.max(baseTolerance * 0.4, 5.0); // Minimum 5m tolerance
        } else if (tripDistanceKm < 2.0) {
            // Short trips: use lower tolerance for better accuracy  
            return baseTolerance * 0.6;
        } else if (tripDistanceKm < 5.0) {
            // Medium short trips: use slightly lower tolerance
            return baseTolerance * 0.8;
        } else if (tripDistanceKm < 10.0) {
            // Medium trips: use base tolerance
            return baseTolerance;
        } else if (tripDistanceKm < 25.0) {
            // Long trips: increase tolerance moderately
            return baseTolerance * 1.4;
        } else if (tripDistanceKm < 50.0) {
            // Very long trips: increase tolerance significantly  
            return baseTolerance * 2.0;
        } else {
            // Extremely long trips: maximum tolerance for highways/long routes
            return baseTolerance * 2.5;
        }
    }

    /**
     * Calculate the total distance of a trip path.
     *
     * @param path GPS points forming the path
     * @return Distance in kilometers
     */
    private double calculateTripDistance(List<? extends GpsPoint> path) {
        if (path == null || path.size() < 2) {
            return 0.0;
        }

        double totalDistance = 0.0;
        for (int i = 1; i < path.size(); i++) {
            GpsPoint p1 = path.get(i - 1);
            GpsPoint p2 = path.get(i);
            totalDistance += GeoUtils.haversine(p1.getLatitude(), p1.getLongitude(), 
                                               p2.getLatitude(), p2.getLongitude());
        }

        return totalDistance / 1000.0; // Convert meters to kilometers
    }

    /**
     * Check if path simplification is enabled in the configuration.
     *
     * @param config Timeline configuration
     * @return true if simplification is enabled
     */
    private boolean isSimplificationEnabled(TimelineConfig config) {
        return config.getPathSimplificationEnabled() != null &&
               config.getPathSimplificationEnabled() &&
               config.getPathSimplificationTolerance() != null &&
               config.getPathSimplificationTolerance() > 0;
    }

    /**
     * Get timeline segment boundaries (trips and stays) for a time range.
     * Returns lightweight DTOs containing only timestamps and durations.
     *
     * @param userId user ID
     * @param start start timestamp
     * @param end end timestamp
     * @return list of segment boundaries ordered by start time
     */
    public List<TimelineSegmentBoundary> getTimelineSegments(UUID userId, Instant start, Instant end) {
        List<TimelineSegmentBoundary> segments = new ArrayList<>();

        // Fetch trip boundaries
        segments.addAll(timelineTripRepository.findTripSegmentBoundaries(userId, start, end));

        // Fetch stay boundaries
        segments.addAll(timelineStayRepository.findStaySegmentBoundaries(userId, start, end));

        // Sort by start time
        segments.sort(Comparator.comparing(TimelineSegmentBoundary::startTime));

        return segments;
    }

    /**
     * Apply segment-aware GPS path simplification.
     * Simplifies each timeline segment (trip or stay) independently with guaranteed minimum points.
     *
     * @param allPoints all GPS points in the time range
     * @param segments timeline segment boundaries
     * @param config timeline configuration
     * @return simplified GPS points with per-segment guarantees
     */
    public List<? extends GpsPoint> simplifyWithSegments(
            List<? extends GpsPoint> allPoints,
            List<TimelineSegmentBoundary> segments,
            TimelineConfig config) {

        if (allPoints == null || allPoints.isEmpty()) {
            return allPoints;
        }

        if (segments == null || segments.isEmpty()) {
            log.debug("No timeline segments found, falling back to legacy simplification");
            return simplify(allPoints, config);
        }

        List<GpsPoint> result = new ArrayList<>();
        log.debug("Applying segment-aware simplification to {} points across {} segments",
                 allPoints.size(), segments.size());

        for (TimelineSegmentBoundary segment : segments) {
            // Extract GPS points for this segment
            List<? extends GpsPoint> segmentPoints = extractPointsInTimeRange(
                    allPoints, segment.startTime(), segment.getEndTime()
            );

            if (segmentPoints.isEmpty()) {
                log.debug("No GPS points for segment {} at {}", segment.type(), segment.startTime());
                continue;
            }

            // Calculate distance for this segment
            double distanceKm = calculateTripDistance(segmentPoints);

            // Determine minimum points for segment
            int minPoints = calculateMinimumPointsForSegment(distanceKm, segment.type());

            // Apply simplification to this segment
            List<? extends GpsPoint> simplified = simplifySegment(segmentPoints, minPoints, distanceKm, config);

            log.debug("Segment {} at {}: {} points -> {} points (distance: {}km, min: {})",
                     segment.type(), segment.startTime(),
                     segmentPoints.size(), simplified.size(), distanceKm, minPoints);

            result.addAll(simplified);
        }

        log.debug("Segment-aware simplification complete: {} total simplified points", result.size());
        return result;
    }

    /**
     * Extract GPS points within a specific time range.
     *
     * @param allPoints all GPS points
     * @param startTime start of time range (inclusive)
     * @param endTime end of time range (inclusive)
     * @return GPS points within the time range
     */
    private List<? extends GpsPoint> extractPointsInTimeRange(
            List<? extends GpsPoint> allPoints,
            Instant startTime,
            Instant endTime) {

        return allPoints.stream()
                .filter(point -> {
                    Instant pointTime = point.getTimestamp();
                    return !pointTime.isBefore(startTime) && !pointTime.isAfter(endTime);
                })
                .toList();
    }

    /**
     * Calculate minimum points for a segment based on distance and type.
     * Ensures each segment has enough points to be visible on the map.
     *
     * @param distanceKm segment distance in kilometers
     * @param type segment type (TRIP or STAY)
     * @return minimum number of points for this segment
     */
    private int calculateMinimumPointsForSegment(double distanceKm, TimelineSegmentBoundary.SegmentType type) {
        if (type == TimelineSegmentBoundary.SegmentType.STAY) {
            // Stays need minimal points (start, middle, end)
            return 3;
        }

        // For trips, scale minimum points based on distance
        if (distanceKm < 0.5) {
            // Very short trips (< 500m)
            return 5;
        } else if (distanceKm < 2.0) {
            // Short trips (< 2km)
            return 10;
        } else if (distanceKm < 10.0) {
            // Medium trips (2-10km)
            return 15;
        } else if (distanceKm < 50.0) {
            // Long trips (10-50km)
            return 20;
        } else {
            // Very long trips (> 50km)
            return 30;
        }
    }

    /**
     * Simplify a single segment with minimum point guarantees.
     *
     * @param segmentPoints GPS points for this segment
     * @param minPoints minimum points to preserve
     * @param distanceKm segment distance in kilometers
     * @param config timeline configuration
     * @return simplified GPS points for this segment
     */
    private List<? extends GpsPoint> simplifySegment(
            List<? extends GpsPoint> segmentPoints,
            int minPoints,
            double distanceKm,
            TimelineConfig config) {

        // If segment already has fewer points than minimum, return as-is
        if (segmentPoints.size() <= minPoints) {
            return segmentPoints;
        }

        // Apply normal simplification
        List<? extends GpsPoint> simplified = simplifyPathWithFlexibleTolerance(
                segmentPoints, distanceKm, config
        );

        // Ensure we meet minimum point requirement
        if (simplified.size() >= minPoints) {
            return simplified;
        }

        // If simplified result is below minimum, reduce tolerance and try again
        double baseTolerance = config.getPathSimplificationTolerance();
        double reducedTolerance = baseTolerance * 0.5;

        while (simplified.size() < minPoints && reducedTolerance > 1.0) {
            simplified = GpsPathSimplifier.simplifyPath(segmentPoints, reducedTolerance);
            if (simplified.size() >= minPoints) {
                break;
            }
            reducedTolerance *= 0.7; // Further reduce tolerance
        }

        // If still below minimum, return original points (safeguard)
        if (simplified.size() < minPoints) {
            log.warn("Could not achieve minimum {} points for segment (got {}), using original {} points",
                    minPoints, simplified.size(), segmentPoints.size());
            return segmentPoints;
        }

        return simplified;
    }
}
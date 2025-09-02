package org.github.tess1o.geopulse.gps.service.simplification;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;

import java.util.List;

/**
 * Service for applying GPS path simplification to timeline trips.
 * Integrates with the timeline configuration system to provide configurable
 * path simplification that reduces data size while preserving route accuracy.
 */
@ApplicationScoped
@Slf4j
public class PathSimplificationService {

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
     * Apply path simplification to a timeline trip DTO.
     *
     * @param tripDTO The timeline trip DTO to simplify
     * @param config Timeline configuration containing simplification settings
     * @return New trip DTO with simplified path, or original if simplification is disabled
     */
    public TimelineTripDTO simplifyTripPath(TimelineTripDTO tripDTO, TimelineConfig config) {
        List<? extends GpsPoint> simplified = simplify(tripDTO.getPath(), config);

        if (simplified.size() < tripDTO.getPath().size()) {
            return TimelineTripDTO.builder()
                    .timestamp(tripDTO.getTimestamp())
                    .latitude(tripDTO.getLatitude())
                    .longitude(tripDTO.getLongitude())
                    .tripDuration(tripDTO.getTripDuration())
                    .distanceKm(tripDTO.getDistanceKm())
                    .movementType(tripDTO.getMovementType())
                    .path(simplified)
                    .build();
        }
        return tripDTO;
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
}
package org.github.tess1o.geopulse.timeline.core;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.timeline.model.TrackPoint;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.Optional;

/**
 * Service for spatial calculations and geographic operations.
 * Provides centralized geographic computation methods for timeline processing.
 */
@ApplicationScoped
@Slf4j
public class SpatialCalculationService {

    /**
     * Calculate weighted centroid of a cluster of track points.
     * Points are weighted inversely by their accuracy (more accurate points have higher weight).
     * 
     * @param cluster list of track points
     * @return array containing [latitude, longitude] of the weighted centroid
     * @throws IllegalArgumentException if cluster is null or empty
     */
    public double[] calculateWeightedCentroid(List<TrackPoint> cluster) {
        if (cluster == null || cluster.isEmpty()) {
            throw new IllegalArgumentException("Cluster cannot be null or empty");
        }

        double sumWeights = cluster.stream()
                .mapToDouble(p -> 1.0 / Math.max(1.0, Optional.ofNullable(p.getAccuracy()).orElse(10.0)))
                .sum();

        double meanLat = cluster.stream()
                .mapToDouble(p -> p.getLatitude() / Math.max(1.0, Optional.ofNullable(p.getAccuracy()).orElse(10.0)))
                .sum() / sumWeights;

        double meanLon = cluster.stream()
                .mapToDouble(p -> p.getLongitude() / Math.max(1.0, Optional.ofNullable(p.getAccuracy()).orElse(10.0)))
                .sum() / sumWeights;

        return new double[]{meanLat, meanLon};
    }
    
    /**
     * Calculate distance between two track points using Haversine formula.
     * 
     * @param point1 first track point
     * @param point2 second track point
     * @return distance in meters
     */
    public double calculateDistance(TrackPoint point1, TrackPoint point2) {
        if (point1 == null || point2 == null) {
            throw new IllegalArgumentException("Points cannot be null");
        }
        
        return GeoUtils.haversine(
            point1.getLatitude(), point1.getLongitude(),
            point2.getLatitude(), point2.getLongitude()
        );
    }
    
    /**
     * Calculate distance between two coordinate pairs using Haversine formula.
     * 
     * @param lat1 latitude of first point
     * @param lon1 longitude of first point
     * @param lat2 latitude of second point
     * @param lon2 longitude of second point
     * @return distance in meters
     */
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        return GeoUtils.haversine(lat1, lon1, lat2, lon2);
    }
    
    /**
     * Check if two points are within a specified distance of each other.
     * 
     * @param point1 first track point
     * @param point2 second track point
     * @param maxDistanceMeters maximum distance threshold in meters
     * @return true if points are within the specified distance
     */
    public boolean arePointsWithinDistance(TrackPoint point1, TrackPoint point2, double maxDistanceMeters) {
        if (point1 == null || point2 == null) {
            return false;
        }
        
        double distance = calculateDistance(point1, point2);
        return distance <= maxDistanceMeters;
    }
    
    /**
     * Check if coordinates are within a specified distance of each other.
     * 
     * @param lat1 latitude of first point
     * @param lon1 longitude of first point
     * @param lat2 latitude of second point
     * @param lon2 longitude of second point
     * @param maxDistanceMeters maximum distance threshold in meters
     * @return true if coordinates are within the specified distance
     */
    public boolean areCoordinatesWithinDistance(double lat1, double lon1, double lat2, double lon2, double maxDistanceMeters) {
        double distance = calculateDistance(lat1, lon1, lat2, lon2);
        return distance <= maxDistanceMeters;
    }
    
    /**
     * Calculate the center point (simple average) of a list of track points.
     * This is faster than weighted centroid but doesn't consider accuracy.
     * 
     * @param points list of track points
     * @return array containing [latitude, longitude] of the center point
     * @throws IllegalArgumentException if points list is null or empty
     */
    public double[] calculateCenterPoint(List<TrackPoint> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("Points list cannot be null or empty");
        }
        
        double avgLat = points.stream()
                .mapToDouble(TrackPoint::getLatitude)
                .average()
                .orElse(0.0);
        
        double avgLon = points.stream()
                .mapToDouble(TrackPoint::getLongitude)
                .average()
                .orElse(0.0);
        
        return new double[]{avgLat, avgLon};
    }
    
    /**
     * Create a JTS Point geometry from longitude and latitude coordinates.
     * 
     * @param longitude longitude coordinate
     * @param latitude latitude coordinate
     * @return JTS Point geometry
     */
    public Point createPoint(double longitude, double latitude) {
        return GeoUtils.createPoint(longitude, latitude);
    }
    
    /**
     * Calculate the total distance of a trip path by summing distances between consecutive GPS points.
     * 
     * @param path list of GPS points representing the trip path
     * @return total distance in kilometers
     * @throws IllegalArgumentException if path is null
     */
    public double calculateTripDistance(List<? extends GpsPoint> path) {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }
        
        if (path.size() < 2) {
            return 0.0;
        }

        double totalDistance = 0.0;
        for (int i = 1; i < path.size(); i++) {
            GpsPoint prev = path.get(i - 1);
            GpsPoint curr = path.get(i);

            double distance = calculateDistance(
                    prev.getLatitude(), prev.getLongitude(),
                    curr.getLatitude(), curr.getLongitude()
            );

            // Convert from meters to kilometers
            totalDistance += distance / 1000.0;
        }

        return totalDistance;
    }
    
    /**
     * Find the end index of a potential stay point cluster starting from the given index.
     * This method implements the common cluster detection logic used by stay point detectors.
     * Clusters are bounded by both distance and velocity thresholds to prevent including moving points.
     * 
     * @param points list of track points
     * @param startIndex starting index to search from
     * @param config timeline configuration with distance, velocity and accuracy thresholds
     * @return the end index (exclusive) of the cluster
     */
    public int findClusterEndIndex(List<TrackPoint> points, int startIndex, org.github.tess1o.geopulse.timeline.model.TimelineConfig config) {
        if (points == null || startIndex >= points.size() || config == null) {
            return startIndex;
        }

        int j = startIndex + 1;
        double velocityThreshold = config.getStaypointVelocityThreshold();
        
        while (j < points.size()) {
            TrackPoint pointI = points.get(startIndex);
            TrackPoint pointJ = points.get(j);

            // Skip points with poor accuracy if accuracy filtering is enabled
            if (config.getUseVelocityAccuracy()) {
                if ((pointJ.getAccuracy() != null && pointJ.getAccuracy() > config.getStaypointMaxAccuracyThreshold()) ||
                        (pointI.getAccuracy() != null && pointI.getAccuracy() > config.getStaypointMaxAccuracyThreshold())) {
                    j++;
                    continue;
                }
            }

            double distance = calculateDistance(pointI, pointJ);

            // If distance exceeds threshold, cluster ends here
            if (distance > config.getTripMinDistanceMeters()) {
                break;
            }
            
            // If velocity exceeds threshold, cluster ends here (stop including moving points)
            if (pointJ.getVelocity() != null && pointJ.getVelocity() > velocityThreshold) {
                break;
            }

            j++;
        }
        return j;
    }

    /**
     * Calculate total distance along a path of GPS points.
     * 
     * @param path list of GPS points forming a path
     * @return total distance in meters
     */
    public double calculateTotalPathDistance(List<? extends GpsPoint> path) {
        if (path == null || path.size() < 2) {
            return 0.0;
        }
        
        double totalDistance = 0.0;
        for (int i = 1; i < path.size(); i++) {
            GpsPoint p1 = path.get(i - 1);
            GpsPoint p2 = path.get(i);
            totalDistance += calculateDistance(p1.getLatitude(), p1.getLongitude(), 
                                             p2.getLatitude(), p2.getLongitude());
        }
        return totalDistance;
    }
}
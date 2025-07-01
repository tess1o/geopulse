package org.github.tess1o.geopulse.timeline.simplification;

import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for simplifying GPS paths using the Douglas-Peucker algorithm.
 * 
 * The Douglas-Peucker algorithm recursively divides a line segment and removes
 * points that are less significant based on their distance from the line connecting
 * the endpoints. This preserves the overall shape while reducing the number of points.
 */
//TODO: should be service, not static methods!
public class GpsPathSimplifier {

    /**
     * Simplify a GPS path using the Douglas-Peucker algorithm.
     *
     * @param points Original GPS points forming the path
     * @param toleranceMeters Distance tolerance in meters - points closer to the line than this will be removed
     * @return Simplified list of GPS points maintaining the essential shape
     */
    public static <T extends GpsPoint> List<T> simplifyPath(List<T> points, double toleranceMeters) {
        if (points == null || points.size() <= 2) {
            return points; // Cannot simplify paths with 2 or fewer points
        }

        List<T> result = new ArrayList<>();
        result.add(points.get(0)); // Always keep the first point
        
        // Apply Douglas-Peucker algorithm
        douglasPeucker(points, 0, points.size() - 1, toleranceMeters, result);
        
        result.add(points.get(points.size() - 1)); // Always keep the last point
        
        return result;
    }

    /**
     * Simplify a GPS path with intelligent tolerance based on trip characteristics.
     *
     * @param points Original GPS points forming the path
     * @param tripDistanceKm Total trip distance in kilometers
     * @param baseTolerance Base tolerance in meters
     * @param maxPoints Maximum number of points to retain (0 = no limit)
     * @return Simplified list of GPS points
     */
    public static List<? extends GpsPoint> simplifyPathAdaptive(List<? extends GpsPoint> points, 
                                                                double tripDistanceKm, 
                                                                double baseTolerance,
                                                                int maxPoints) {
        if (points == null || points.size() <= 2) {
            return points;
        }

        // Calculate adaptive tolerance based on trip distance
        double tolerance = calculateAdaptiveTolerance(tripDistanceKm, baseTolerance);
        
        List<? extends GpsPoint> simplified = simplifyPath(points, tolerance);
        
        // If still too many points and max limit is specified, increase tolerance iteratively
        if (maxPoints > 0 && simplified.size() > maxPoints) {
            double currentTolerance = tolerance;
            while (simplified.size() > maxPoints && currentTolerance < baseTolerance * 10) {
                currentTolerance *= 1.5; // Increase tolerance by 50% each iteration
                simplified = simplifyPath(points, currentTolerance);
            }
        }
        
        return simplified;
    }

    /**
     * Calculate adaptive tolerance based on trip distance.
     * Longer trips can tolerate higher simplification without losing essential shape.
     *
     * @param tripDistanceKm Trip distance in kilometers
     * @param baseTolerance Base tolerance in meters
     * @return Calculated tolerance in meters
     */
    private static double calculateAdaptiveTolerance(double tripDistanceKm, double baseTolerance) {
        if (tripDistanceKm < 1.0) {
            // Short trips: use lower tolerance for better accuracy
            return baseTolerance * 0.5;
        } else if (tripDistanceKm < 5.0) {
            // Medium trips: use base tolerance
            return baseTolerance;
        } else if (tripDistanceKm < 20.0) {
            // Long trips: increase tolerance moderately
            return baseTolerance * 1.5;
        } else {
            // Very long trips: allow higher tolerance
            return baseTolerance * 2.0;
        }
    }

    /**
     * Recursive Douglas-Peucker algorithm implementation.
     *
     * @param points All points in the path
     * @param startIndex Index of the start point of current segment
     * @param endIndex Index of the end point of current segment
     * @param tolerance Distance tolerance in meters
     * @param result List to accumulate kept points
     */
    private static <T extends GpsPoint> void douglasPeucker(List<T> points,
                                       int startIndex, 
                                       int endIndex, 
                                       double tolerance, 
                                       List<T> result) {
        if (endIndex <= startIndex + 1) {
            return; // No points between start and end
        }

        // Find the point with maximum distance from the line segment
        double maxDistance = 0.0;
        int maxIndex = -1;
        
        GpsPoint start = points.get(startIndex);
        GpsPoint end = points.get(endIndex);

        for (int i = startIndex + 1; i < endIndex; i++) {
            GpsPoint point = points.get(i);
            double distance = calculatePerpendicularDistance(point, start, end);
            
            if (distance > maxDistance) {
                maxDistance = distance;
                maxIndex = i;
            }
        }

        // If max distance is greater than tolerance, keep the point and recurse
        if (maxDistance > tolerance && maxIndex != -1) {
            // Recursively simplify the path before the max point
            douglasPeucker(points, startIndex, maxIndex, tolerance, result);
            
            // Add the significant point
            result.add(points.get(maxIndex));
            
            // Recursively simplify the path after the max point
            douglasPeucker(points, maxIndex, endIndex, tolerance, result);
        }
    }

    /**
     * Calculate the perpendicular distance from a point to a line segment.
     * Uses the cross product method to find the shortest distance from point to line.
     *
     * @param point The point to measure distance from
     * @param lineStart Start point of the line segment
     * @param lineEnd End point of the line segment
     * @return Distance in meters
     */
    @SuppressWarnings("LocalVariableNamingConventions")
    private static double calculatePerpendicularDistance(GpsPoint point, GpsPoint lineStart, GpsPoint lineEnd) {
        // Convert GPS coordinates to approximate Cartesian coordinates for calculation
        // This is an approximation that works well for relatively short distances
        
        // If line start and end are the same point, return distance to that point
        double lineLength = GeoUtils.haversine(lineStart.getLatitude(), lineStart.getLongitude(),
                                              lineEnd.getLatitude(), lineEnd.getLongitude());
        if (lineLength < 1.0) {
            return GeoUtils.haversine(point.getLatitude(), point.getLongitude(),
                                     lineStart.getLatitude(), lineStart.getLongitude());
        }

        // Convert to approximate meters using a local coordinate system
        double earthRadius = 6371000; // Earth radius in meters
        double lat1Rad = Math.toRadians(lineStart.getLatitude());
        double lat2Rad = Math.toRadians(lineEnd.getLatitude());
        double latPRad = Math.toRadians(point.getLatitude());
        
        // Use equirectangular approximation for local coordinates
        double cosLat = Math.cos((lat1Rad + lat2Rad) / 2);
        
        // Convert to local Cartesian coordinates (meters)
        double x1 = Math.toRadians(lineStart.getLongitude()) * earthRadius * cosLat;
        double y1 = lat1Rad * earthRadius;
        
        double x2 = Math.toRadians(lineEnd.getLongitude()) * earthRadius * cosLat;
        double y2 = lat2Rad * earthRadius;
        
        double xP = Math.toRadians(point.getLongitude()) * earthRadius * cosLat;
        double yP = latPRad * earthRadius;

        // Calculate perpendicular distance using cross product formula
        double A = x2 - x1;
        double B = y2 - y1;
        double C = xP - x1;
        double D = yP - y1;

        double dot = A * C + B * D;
        double lenSq = A * A + B * B;
        
        if (lenSq == 0) {
            // Line start and end are the same point
            return Math.sqrt((xP - x1) * (xP - x1) + (yP - y1) * (yP - y1));
        }

        double param = dot / lenSq;

        double xx;
        double yy;
        if (param < 0) {
            // Closest point is before the line segment
            xx = x1;
            yy = y1;
        } else if (param > 1) {
            // Closest point is after the line segment
            xx = x2;
            yy = y2;
        } else {
            // Closest point is on the line segment
            xx = x1 + param * A;
            yy = y1 + param * B;
        }

        double dx = xP - xx;
        double dy = yP - yy;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Calculate compression statistics for path simplification.
     *
     * @param originalPoints Original GPS points
     * @param simplifiedPoints Simplified GPS points
     * @return Simplification statistics
     */
    public static SimplificationStats calculateStats(List<? extends GpsPoint> originalPoints, 
                                                     List<? extends GpsPoint> simplifiedPoints) {
        if (originalPoints == null || simplifiedPoints == null) {
            return new SimplificationStats(0, 0, 0.0, 0.0);
        }

        int originalCount = originalPoints.size();
        int simplifiedCount = simplifiedPoints.size();
        double compressionRatio = originalCount > 0 ? (double) simplifiedCount / originalCount : 0.0;
        double reductionPercentage = originalCount > 0 ? (1.0 - compressionRatio) * 100.0 : 0.0;

        return new SimplificationStats(originalCount, simplifiedCount, compressionRatio, reductionPercentage);
    }

    /**
     * Statistics about path simplification results.
     */
    public record SimplificationStats(
        int originalPointCount,
        int simplifiedPointCount,
        double compressionRatio,
        double reductionPercentage
    ) {
        @Override
        public String toString() {
            return String.format("SimplificationStats{original=%d, simplified=%d, compression=%.2f, reduction=%.1f%%}",
                originalPointCount, simplifiedPointCount, compressionRatio, reductionPercentage);
        }
    }
}
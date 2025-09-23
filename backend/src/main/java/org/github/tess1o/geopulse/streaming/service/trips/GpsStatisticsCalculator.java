package org.github.tess1o.geopulse.streaming.service.trips;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;

import java.util.List;

/**
 * Service for calculating GPS-based statistics from GPS point sequences.
 * These statistics are used for more accurate travel classification.
 */
@ApplicationScoped
@Slf4j
public class GpsStatisticsCalculator {

    private static final double ACCURACY_THRESHOLD_METERS = 50.0; // GPS points with accuracy > 50m are considered low quality

    /**
     * Calculate comprehensive GPS statistics from a list of GPS points.
     *
     * @param gpsPoints list of GPS points with speed and accuracy data
     * @return GPS statistics object
     */
    public GpsStatistics calculateStatistics(List<GPSPoint> gpsPoints) {
        if (gpsPoints == null || gpsPoints.isEmpty()) {
            return GpsStatistics.empty();
        }

        // Filter out points without speed data
        List<GPSPoint> validSpeedPoints = gpsPoints.stream()
                .filter(point -> point.getSpeed() >= 0) // GPS speed should be non-negative
                .toList();

        if (validSpeedPoints.isEmpty()) {
            log.debug("No valid GPS speed data found in {} points", gpsPoints.size());
            return GpsStatistics.empty();
        }

        // Calculate speed statistics
        double sumSpeed = 0.0;
        double maxSpeed = 0.0;
        int lowAccuracyCount = 0;

        for (GPSPoint point : validSpeedPoints) {
            double speed = point.getSpeed(); // Speed in m/s
            sumSpeed += speed;
            maxSpeed = Math.max(maxSpeed, speed);

            // Count low accuracy points
            if (point.getAccuracy() > ACCURACY_THRESHOLD_METERS) {
                lowAccuracyCount++;
            }
        }

        double avgSpeed = sumSpeed / validSpeedPoints.size();

        // Calculate speed variance
        double sumSquaredDifferences = 0.0;
        for (GPSPoint point : validSpeedPoints) {
            double diff = point.getSpeed() - avgSpeed;
            sumSquaredDifferences += diff * diff;
        }
        double speedVariance = sumSquaredDifferences / validSpeedPoints.size();

        log.debug("Calculated GPS statistics from {} points: avgSpeed={} m/s, maxSpeed={} m/s, " +
                "variance={}, lowAccuracy={}", 
                validSpeedPoints.size(), String.format("%.2f", avgSpeed), 
                String.format("%.2f", maxSpeed), String.format("%.2f", speedVariance), lowAccuracyCount);

        return new GpsStatistics(avgSpeed, maxSpeed, speedVariance, lowAccuracyCount);
    }

    /**
     * Container for GPS-derived statistics.
     */
    public record GpsStatistics(
            Double avgGpsSpeed,      // Average GPS speed in m/s
            Double maxGpsSpeed,      // Maximum GPS speed in m/s  
            Double speedVariance,    // Speed variance (consistency indicator)
            Integer lowAccuracyPointsCount // Count of low-accuracy GPS points
    ) {
        /**
         * Create empty statistics for cases with no valid GPS data.
         */
        public static GpsStatistics empty() {
            return new GpsStatistics(null, null, null, null);
        }

        /**
         * Check if statistics contain valid data.
         */
        public boolean hasValidData() {
            return avgGpsSpeed != null && maxGpsSpeed != null;
        }
    }
}
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
    public TripGpsStatistics calculateStatistics(List<GPSPoint> gpsPoints) {
        if (gpsPoints == null || gpsPoints.isEmpty()) {
            return TripGpsStatistics.empty();
        }

        // Filter out points without speed data
        List<GPSPoint> validSpeedPoints = gpsPoints.stream()
                .filter(point -> point.getSpeed() >= 0) // GPS speed should be non-negative
                .toList();

        if (validSpeedPoints.isEmpty()) {
            log.debug("No valid GPS speed data found in {} points", gpsPoints.size());
            return TripGpsStatistics.empty();
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
        return new TripGpsStatistics(avgSpeed, maxSpeed, speedVariance, lowAccuracyCount);
    }
}
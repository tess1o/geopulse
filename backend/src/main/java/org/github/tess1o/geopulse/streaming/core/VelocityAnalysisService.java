package org.github.tess1o.geopulse.streaming.core;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for velocity analysis and speed-related calculations.
 * Provides centralized velocity computation methods for timeline processing.
 */
@ApplicationScoped
@Slf4j
public class VelocityAnalysisService {

    /**
     * Apply simple moving average smoothing to a list of speeds.
     * 
     * @param speeds input speed values
     * @param windowSize size of the moving average window
     * @return smoothed speed values
     */
    public List<Double> applyMovingAverage(List<Double> speeds, int windowSize) {
        if (speeds == null || speeds.isEmpty() || windowSize <= 0) {
            return new ArrayList<>(speeds != null ? speeds : List.of());
        }

        List<Double> smoothed = new ArrayList<>();
        
        for (int i = 0; i < speeds.size(); i++) {
            int start = Math.max(0, i - windowSize / 2);
            int end = Math.min(speeds.size(), start + windowSize);
            
            double sum = 0;
            int count = 0;
            for (int j = start; j < end; j++) {
                if (speeds.get(j) != null) {
                    sum += speeds.get(j);
                    count++;
                }
            }
            
            smoothed.add(count > 0 ? sum / count : speeds.get(i));
        }
        
        return smoothed;
    }
    
    /**
     * Calculate instantaneous speed between two GPS points.
     * 
     * @param point1 first GPS point
     * @param point2 second GPS point
     * @param spatialCalculationService service for distance calculations
     * @return speed in km/h, or 0 if calculation is not possible
     */
    public double calculateInstantSpeed(GpsPoint point1, GpsPoint point2) {
        if (point1 == null || point2 == null || point1.getTimestamp() == null || point2.getTimestamp() == null) {
            return 0.0;
        }
        
        Duration duration = Duration.between(point1.getTimestamp(), point2.getTimestamp());
        if (duration.isZero() || duration.isNegative()) {
            return 0.0;
        }
        
        double distanceMeters = GeoUtils.haversine(
            point1.getLatitude(), point1.getLongitude(),
            point2.getLatitude(), point2.getLongitude()
        );
        
        double speedMps = distanceMeters / duration.getSeconds();
        return speedMps * 3.6; // Convert m/s to km/h
    }
    
    /**
     * Check if a speed value is considered suspicious (likely GPS error).
     * 
     * @param speedKmh speed in km/h
     * @param suspiciousThreshold threshold for suspicious speeds
     * @return true if speed is suspicious
     */
    public boolean isSuspiciousSpeed(double speedKmh, double suspiciousThreshold) {
        return speedKmh > suspiciousThreshold;
    }
}
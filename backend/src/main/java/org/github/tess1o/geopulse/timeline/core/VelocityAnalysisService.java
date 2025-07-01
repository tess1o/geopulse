package org.github.tess1o.geopulse.timeline.core;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.timeline.model.TrackPoint;
import org.github.tess1o.geopulse.timeline.util.TimelineConstants;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Service for velocity analysis and speed-related calculations.
 * Provides centralized velocity computation methods for timeline processing.
 */
@ApplicationScoped
@Slf4j
public class VelocityAnalysisService {

    /**
     * Analyze velocity characteristics of a window of GPS points.
     * 
     * @param windowPoints list of GPS points in the window
     * @param startIndex starting index of the window in the original list
     * @return VelocityWindow containing velocity statistics
     */
    public VelocityWindow analyzeVelocityWindow(List<GpsPointPathPointDTO> windowPoints, int startIndex) {
        if (windowPoints == null || windowPoints.isEmpty()) {
            return new VelocityWindow(startIndex, 0, 0, 0, 0);
        }

        List<Double> velocities = windowPoints.stream()
                .map(GpsPointPathPointDTO::getVelocity)
                .filter(Objects::nonNull)
                .toList();

        if (velocities.isEmpty()) {
            return new VelocityWindow(startIndex, 0, 0, 0, 0);
        }

        double[] velocityArray = velocities.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(velocityArray);

        double median = velocityArray[velocityArray.length / 2];
        double p95 = velocityArray[(int) (velocityArray.length * 0.95)];
        double max = velocityArray[velocityArray.length - 1];
        double avg = velocities.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        return new VelocityWindow(startIndex, median, p95, max, avg);
    }
    
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
    public double calculateInstantSpeed(GpsPoint point1, GpsPoint point2, SpatialCalculationService spatialCalculationService) {
        if (point1 == null || point2 == null || point1.getTimestamp() == null || point2.getTimestamp() == null) {
            return 0.0;
        }
        
        Duration duration = Duration.between(point1.getTimestamp(), point2.getTimestamp());
        if (duration.isZero() || duration.isNegative()) {
            return 0.0;
        }
        
        double distanceMeters = spatialCalculationService.calculateDistance(
            point1.getLatitude(), point1.getLongitude(),
            point2.getLatitude(), point2.getLongitude()
        );
        
        double speedMps = distanceMeters / duration.getSeconds();
        return speedMps * 3.6; // Convert m/s to km/h
    }
    
    /**
     * Calculate speed for a sequence of track points.
     * 
     * @param trackPoints list of track points
     * @param spatialCalculationService service for distance calculations
     * @return list of speeds in km/h
     */
    public List<Double> calculateSpeeds(List<TrackPoint> trackPoints, SpatialCalculationService spatialCalculationService) {
        if (trackPoints == null || trackPoints.size() < 2) {
            return new ArrayList<>();
        }
        
        log.debug("Calculating speeds for {} track points", trackPoints.size());
        
        List<Double> speeds = new ArrayList<>();
        
        for (int i = 1; i < trackPoints.size(); i++) {
            TrackPoint prev = trackPoints.get(i - 1);
            TrackPoint curr = trackPoints.get(i);
            
            double speed = calculateInstantSpeed(prev, curr, spatialCalculationService);
            speeds.add(speed);
        }
        
        return speeds;
    }
    
    /**
     * Check if a speed value is considered realistic for the given context.
     * 
     * @param speedKmh speed in km/h
     * @param maxReasonableSpeed maximum reasonable speed for the context
     * @return true if speed is reasonable
     */
    public boolean isSpeedReasonable(double speedKmh, double maxReasonableSpeed) {
        return speedKmh >= 0 && speedKmh <= maxReasonableSpeed;
    }
    
    /**
     * Filter out unrealistic speed values from a list.
     * 
     * @param speeds input speeds in km/h
     * @param maxReasonableSpeed maximum reasonable speed
     * @return filtered speeds with unrealistic values replaced by reasonable estimates
     */
    public List<Double> filterUnrealisticSpeeds(List<Double> speeds, double maxReasonableSpeed) {
        if (speeds == null || speeds.isEmpty()) {
            return new ArrayList<>();
        }

        List<Double> filtered = new ArrayList<>();
        
        for (int i = 0; i < speeds.size(); i++) {
            Double speed = speeds.get(i);
            
            if (speed == null || !isSpeedReasonable(speed, maxReasonableSpeed)) {
                // Replace with interpolated value from neighbors or reasonable default
                double replacement = findReasonableReplacement(speeds, i, maxReasonableSpeed);
                filtered.add(replacement);
            } else {
                filtered.add(speed);
            }
        }
        
        return filtered;
    }
    
    private double findReasonableReplacement(List<Double> speeds, int index, double maxReasonableSpeed) {
        // Look for reasonable neighbors
        Double prevReasonable = null;
        Double nextReasonable = null;
        
        // Look backwards
        for (int i = index - 1; i >= 0; i--) {
            if (speeds.get(i) != null && isSpeedReasonable(speeds.get(i), maxReasonableSpeed)) {
                prevReasonable = speeds.get(i);
                break;
            }
        }
        
        // Look forwards
        for (int i = index + 1; i < speeds.size(); i++) {
            if (speeds.get(i) != null && isSpeedReasonable(speeds.get(i), maxReasonableSpeed)) {
                nextReasonable = speeds.get(i);
                break;
            }
        }
        
        // Interpolate or use single neighbor
        if (prevReasonable != null && nextReasonable != null) {
            return (prevReasonable + nextReasonable) / 2.0;
        } else if (prevReasonable != null) {
            return prevReasonable;
        } else if (nextReasonable != null) {
            return nextReasonable;
        } else {
            return TimelineConstants.WALKING_MAX_SPEED_KMH; // Reasonable default
        }
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
    
    /**
     * Calculate median velocity from a list of track points.
     * Filters out null velocities before calculation.
     * 
     * @param points list of track points
     * @return median velocity in km/h, or 0.0 if no valid velocities found
     */
    public double calculateMedianVelocity(List<TrackPoint> points) {
        if (points == null || points.isEmpty()) {
            return 0.0;
        }
        
        List<Double> validVelocities = points.stream()
                .filter(p -> p.getVelocity() != null)
                .map(TrackPoint::getVelocity)
                .sorted()
                .toList();
                
        if (validVelocities.isEmpty()) {
            return 0.0;
        }
        
        int size = validVelocities.size();
        if (size % 2 == 0) {
            // Even number of elements - average middle two
            return (validVelocities.get(size / 2 - 1) + validVelocities.get(size / 2)) / 2.0;
        } else {
            // Odd number of elements - return middle element
            return validVelocities.get(size / 2);
        }
    }
    
    /**
     * Velocity window statistics record.
     */
    public record VelocityWindow(
        int startIndex,
        double median,
        double p95,
        double max,
        double average
    ) {}
}
package org.github.tess1o.geopulse.streaming.service.trips;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.core.VelocityAnalysisService;
import org.github.tess1o.geopulse.streaming.util.TimelineConstants;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.github.tess1o.geopulse.streaming.model.shared.TripType.*;

@ApplicationScoped
@Slf4j
public class TravelClassification {

    private final VelocityAnalysisService velocityAnalysisService;

    @Inject
    public TravelClassification(VelocityAnalysisService velocityAnalysisService) {
        this.velocityAnalysisService = velocityAnalysisService;
    }

    /**
     * Classify travel type using pre-calculated GPS statistics from TimelineTripEntity.
     * This method provides more accurate classification using real GPS data.
     *
     * @param trip the timeline trip with pre-calculated GPS statistics
     * @param config timeline configuration containing classification thresholds
     * @return classified trip type
     */
    public TripType classifyTravelType(TimelineTripEntity trip, TimelineConfig config) {
        // If GPS statistics are available, use enhanced classification
        if (trip.getAvgGpsSpeed() != null && trip.getMaxGpsSpeed() != null) {
            return classifyWithGpsStatistics(trip, config);
        }
        
        // Fallback to path-based classification for legacy trips
        log.debug("GPS statistics not available for trip {}, falling back to path-based classification", trip.getId());
        
        // Convert distance and duration for fallback calculation
        double distanceKm = trip.getDistanceMeters() / 1000.0;
        double hours = trip.getTripDuration() / 3600.0;
        double avgSpeedKmh = hours > 0 ? distanceKm / hours : 0.0;
        
        return classify(avgSpeedKmh, avgSpeedKmh, distanceKm, config);
    }

    /**
     * Enhanced classification using pre-calculated GPS statistics.
     * Provides more accurate results by using real GPS speed data and variance analysis.
     */
    private TripType classifyWithGpsStatistics(TimelineTripEntity trip, TimelineConfig config) {
        // Convert speeds from m/s to km/h
        double avgSpeedKmh = trip.getAvgGpsSpeed() * 3.6;
        double maxSpeedKmh = trip.getMaxGpsSpeed() * 3.6;
        double distanceKm = trip.getDistanceMeters() / 1000.0;
        
        // Enhanced classification using speed variance
        Double speedVariance = trip.getSpeedVariance();
        Integer lowAccuracyCount = trip.getLowAccuracyPointsCount();
        
        // Log for debugging misclassifications
        if (trip.getTripDuration() > 1800 && log.isDebugEnabled()) { // > 30 minutes
            log.debug("Classifying long trip {}: avgSpeed={}km/h, maxSpeed={}km/h, distance={}km, " +
                    "variance={}, lowAccuracy={}", 
                    trip.getId(), String.format("%.1f", avgSpeedKmh), String.format("%.1f", maxSpeedKmh), 
                    String.format("%.1f", distanceKm), speedVariance, lowAccuracyCount);
        }
        
        TripType result = classifyEnhanced(avgSpeedKmh, maxSpeedKmh, distanceKm, speedVariance, 
                                         lowAccuracyCount, config);
        
        // Log potential misclassifications for analysis
        if (result == WALK && (avgSpeedKmh > 15.0 || maxSpeedKmh > 20.0)) {
            log.warn("Potential walking misclassification for trip {}: avgSpeed={}km/h, maxSpeed={}km/h", 
                    trip.getId(), String.format("%.1f", avgSpeedKmh), String.format("%.1f", maxSpeedKmh));
        }
        
        return result;
    }

    /**
     * Enhanced classification algorithm using GPS statistics and speed variance analysis.
     */
    private TripType classifyEnhanced(double avgSpeedKmh, double maxSpeedKmh, double distanceKm, 
                                    Double speedVariance, Integer lowAccuracyCount, TimelineConfig config) {
        
        // Get thresholds from config
        double walkingMaxAvg = config.getWalkingMaxAvgSpeed();
        double walkingMaxMax = config.getWalkingMaxMaxSpeed();
        double carMinAvg = config.getCarMinAvgSpeed();
        double carMinMax = config.getCarMinMaxSpeed();
        double shortDistanceKm = config.getShortDistanceKm();
        
        // Data quality assessment
        boolean hasReliableData = lowAccuracyCount == null || lowAccuracyCount < 5; // Fewer than 5 low-accuracy points
        
        // Speed variance analysis (if available)
        boolean steadyMovement = speedVariance != null && speedVariance < 10.0; // Low variance = steady movement
        boolean variableMovement = speedVariance != null && speedVariance > 25.0; // High variance = stop-and-go
        
        // Short trip handling with enhanced logic
        boolean shortTrip = distanceKm <= shortDistanceKm;
        
        // Enhanced walking detection
        boolean avgSpeedWithinWalking = avgSpeedKmh <= walkingMaxAvg;
        boolean maxSpeedWithinWalking = maxSpeedKmh <= walkingMaxMax;
        boolean avgSpeedSlightlyAboveWalking = avgSpeedKmh <= (walkingMaxAvg + (shortTrip ? 2.0 : 1.0));
        
        // Enhanced car detection
        boolean avgSpeedIndicatesCar = avgSpeedKmh >= carMinAvg;
        boolean maxSpeedIndicatesCar = maxSpeedKmh >= carMinMax;
        
        // Classification logic with variance analysis
        if (avgSpeedWithinWalking && maxSpeedWithinWalking) {
            // Clear walking case
            return WALK;
        } else if (avgSpeedIndicatesCar || maxSpeedIndicatesCar) {
            // Clear driving case
            return CAR;
        } else if (shortTrip && avgSpeedSlightlyAboveWalking && maxSpeedWithinWalking) {
            // Short trip with slightly elevated speed - likely walking
            return WALK;
        } else if (hasReliableData && steadyMovement && avgSpeedKmh < (walkingMaxAvg + 2.0)) {
            // Steady movement at moderate speed with good data quality - likely walking
            return WALK;
        } else if (variableMovement && avgSpeedKmh > (walkingMaxAvg - 1.0)) {
            // Variable speed (stop-and-go) suggests driving
            return CAR;
        } else {
            // Fallback to original classification logic
            return classify(avgSpeedKmh, maxSpeedKmh, distanceKm, config);
        }
    }

    /**
     * Original method for classifying travel type from GPS point list.
     * Used during timeline generation when processing raw GPS data.
     */
    public TripType classifyTravelType(List<? extends GpsPoint> path, Duration duration, TimelineConfig config) {
        if (path == null || path.size() < 2) {
            return UNKNOWN;
        }

        List<Double> speeds = new ArrayList<>();
        double totalDistanceKm = 0.0;

        for (int i = 1; i < path.size(); i++) {
            GpsPoint p1 = path.get(i - 1);
            GpsPoint p2 = path.get(i);

            long timeSeconds = Duration.between(p1.getTimestamp(), p2.getTimestamp()).getSeconds();

            if (timeSeconds <= 0) {
                continue; // Skip invalid or zero intervals
            }

            double instantSpeedKmh = velocityAnalysisService.calculateInstantSpeed(p1, p2);

            // Skip suspicious GPS jumps
            if (velocityAnalysisService.isSuspiciousSpeed(instantSpeedKmh, TimelineConstants.SUSPICIOUS_SPEED_THRESHOLD_KMH)) {
                continue;
            }

            double distKm = GeoUtils.haversine(p1.getLatitude(), p1.getLongitude(), p2.getLatitude(), p2.getLongitude()) / 1000.0;

            speeds.add(instantSpeedKmh);
            totalDistanceKm += distKm;
        }

        if (speeds.isEmpty()) {
            return UNKNOWN;
        }

        // For poor GPS data, use fallback calculation based on stay points
        if (speeds.isEmpty() || totalDistanceKm < config.getStaypointRadiusMeters()) {
            // Use straight-line distance and duration for poor GPS cases
            if (path.size() >= 2) {
                GpsPoint start = path.getFirst();
                GpsPoint end = path.getLast();
                double straightLineDistance = GeoUtils.haversine(
                    start.getLatitude(), start.getLongitude(), 
                    end.getLatitude(), end.getLongitude()) / 1000.0;
                double hours = duration.toMillis() / 3600000.0;
                
                // If extremely short distance or duration, return UNKNOWN
                if (straightLineDistance < 0.001 || hours < 0.0003) { // < 1m or < 1 second
                    return UNKNOWN;
                }
                
                double fallbackAvgSpeed = hours > 0 ? straightLineDistance / hours : 0.0;
                return classify(fallbackAvgSpeed, fallbackAvgSpeed, straightLineDistance, config);
            }
            return UNKNOWN;
        }

        // Smooth speeds with a simple moving average
        List<Double> smoothedSpeeds = velocityAnalysisService.applyMovingAverage(speeds, (int) TimelineConstants.MOVING_AVERAGE_WINDOW_SIZE);

        double hours = duration.toMillis() / 3600000.0;
        double avgSpeedKmh = totalDistanceKm / hours;
        double maxSpeedKmh = smoothedSpeeds.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        return classify(avgSpeedKmh, maxSpeedKmh, totalDistanceKm, config);
    }

    /**
     * Classify trip type based on speed and distance metrics.
     *
     * @param avgSpeedKmh average speed in km/h
     * @param maxSpeedKmh maximum speed in km/h
     * @param totalDistanceKm total distance in km
     * @param config timeline configuration containing classification thresholds
     * @return classified trip type
     */
    private TripType classify(double avgSpeedKmh, double maxSpeedKmh, double totalDistanceKm, TimelineConfig config) {
        // Get thresholds from config
        double walkingMaxAvg = config.getWalkingMaxAvgSpeed();
        double walkingMaxMax = config.getWalkingMaxMaxSpeed();
        double carMinAvg = config.getCarMinAvgSpeed();
        double carMinMax = config.getCarMinMaxSpeed();
        double shortDistanceKm = config.getShortDistanceKm();
        
        // Short trip walking tolerance
        boolean shortTrip = totalDistanceKm <= shortDistanceKm;
        boolean avgSpeedWithinWalking = avgSpeedKmh < walkingMaxAvg;
        boolean maxSpeedWithinWalking = maxSpeedKmh < walkingMaxMax;
        boolean avgSpeedSlightlyAboveWalking = avgSpeedKmh < (walkingMaxAvg + 1.0); // 1 km/h delta

        if ((avgSpeedWithinWalking && maxSpeedWithinWalking) ||
                (shortTrip && avgSpeedSlightlyAboveWalking && maxSpeedWithinWalking)) {
            return WALK;
        } else if (avgSpeedKmh > carMinAvg || maxSpeedKmh > carMinMax) {
            return CAR;
        } else {
            return UNKNOWN;
        }
    }
}
package org.github.tess1o.geopulse.streaming.service.trips;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.core.VelocityAnalysisService;
import org.github.tess1o.geopulse.streaming.util.TimelineConstants;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.github.tess1o.geopulse.streaming.model.shared.TripType.*;

@ApplicationScoped
public class TravelClassification {

    // Thresholds (can be adjusted)
    private static final double WALKING_MAX_AVG_SPEED = 6.0;
    private static final double WALKING_MAX_MAX_SPEED = 8.0;

    private static final double CAR_MIN_AVG_SPEED = 8.0;
    private static final double CAR_MIN_MAX_SPEED = 15.0;
    public static final double SHORT_DISTANCE_KM = 1.0;

    private final VelocityAnalysisService velocityAnalysisService;

    @Inject
    public TravelClassification(VelocityAnalysisService velocityAnalysisService) {
        this.velocityAnalysisService = velocityAnalysisService;
    }

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
                return classify(fallbackAvgSpeed, fallbackAvgSpeed, straightLineDistance);
            }
            return UNKNOWN;
        }

        // Smooth speeds with a simple moving average
        List<Double> smoothedSpeeds = velocityAnalysisService.applyMovingAverage(speeds, (int) TimelineConstants.MOVING_AVERAGE_WINDOW_SIZE);

        double hours = duration.toMillis() / 3600000.0;
        double avgSpeedKmh = totalDistanceKm / hours;
        double maxSpeedKmh = smoothedSpeeds.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        return classify(avgSpeedKmh, maxSpeedKmh, totalDistanceKm);
    }

    /**
     * Classify trip type based on speed and distance metrics.
     *
     * @param avgSpeedKmh average speed in km/h
     * @param maxSpeedKmh maximum speed in km/h
     * @param totalDistanceKm total distance in km
     * @return classified trip type
     */
    private TripType classify(double avgSpeedKmh, double maxSpeedKmh, double totalDistanceKm) {
        // Short trip walking tolerance
        boolean shortTrip = totalDistanceKm <= SHORT_DISTANCE_KM;
        boolean avgSpeedWithinWalking = avgSpeedKmh < WALKING_MAX_AVG_SPEED;
        boolean maxSpeedWithinWalking = maxSpeedKmh < WALKING_MAX_MAX_SPEED;
        boolean avgSpeedSlightlyAboveWalking = avgSpeedKmh < (WALKING_MAX_AVG_SPEED + 1.0); // 1 km/h delta

        if ((avgSpeedWithinWalking && maxSpeedWithinWalking) ||
                (shortTrip && avgSpeedSlightlyAboveWalking && maxSpeedWithinWalking)) {
            return WALK;
        } else if (avgSpeedKmh > CAR_MIN_AVG_SPEED || maxSpeedKmh > CAR_MIN_MAX_SPEED) {
            return CAR;
        } else {
            return UNKNOWN;
        }
    }
}
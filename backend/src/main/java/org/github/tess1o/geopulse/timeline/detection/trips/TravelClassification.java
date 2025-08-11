package org.github.tess1o.geopulse.timeline.detection.trips;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.timeline.model.TravelMode;
import org.github.tess1o.geopulse.timeline.core.SpatialCalculationService;
import org.github.tess1o.geopulse.timeline.core.VelocityAnalysisService;
import org.github.tess1o.geopulse.timeline.util.TimelineConstants;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class TravelClassification {

    private final SpatialCalculationService spatialCalculationService;
    private final VelocityAnalysisService velocityAnalysisService;

    @Inject
    public TravelClassification(SpatialCalculationService spatialCalculationService,
                              VelocityAnalysisService velocityAnalysisService) {
        this.spatialCalculationService = spatialCalculationService;
        this.velocityAnalysisService = velocityAnalysisService;
    }

    public TravelMode classifyTravelType(List<? extends GpsPoint> path, Duration duration) {
        if (path == null || path.size() < 2) {
            return TravelMode.UNKNOWN;
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

            double instantSpeedKmh = velocityAnalysisService.calculateInstantSpeed(p1, p2, spatialCalculationService);

            // Skip suspicious GPS jumps
            if (velocityAnalysisService.isSuspiciousSpeed(instantSpeedKmh, TimelineConstants.SUSPICIOUS_SPEED_THRESHOLD_KMH)) {
                continue;
            }

            double distKm = spatialCalculationService.calculateDistance(p1.getLatitude(), p1.getLongitude(), p2.getLatitude(), p2.getLongitude()) / 1000.0;

            speeds.add(instantSpeedKmh);
            totalDistanceKm += distKm;
        }

        if (speeds.isEmpty()) {
            return TravelMode.UNKNOWN;
        }

        // For poor GPS data, use fallback calculation based on stay points
        if (speeds.isEmpty() || totalDistanceKm < TimelineConstants.MIN_TRIP_DISTANCE_KM) {
            // Use straight-line distance and duration for poor GPS cases
            if (path.size() >= 2) {
                GpsPoint start = path.get(0);
                GpsPoint end = path.get(path.size() - 1);
                double straightLineDistance = spatialCalculationService.calculateDistance(
                    start.getLatitude(), start.getLongitude(), 
                    end.getLatitude(), end.getLongitude()) / 1000.0;
                double hours = duration.toMillis() / 3600000.0;
                
                // If extremely short distance or duration, return UNKNOWN
                if (straightLineDistance < 0.001 || hours < 0.0003) { // < 1m or < 1 second
                    return TravelMode.UNKNOWN;
                }
                
                double fallbackAvgSpeed = hours > 0 ? straightLineDistance / hours : 0.0;
                return TravelMode.classify(fallbackAvgSpeed, fallbackAvgSpeed, straightLineDistance);
            }
            return TravelMode.UNKNOWN;
        }

        // Smooth speeds with a simple moving average
        List<Double> smoothedSpeeds = velocityAnalysisService.applyMovingAverage(speeds, (int) TimelineConstants.MOVING_AVERAGE_WINDOW_SIZE);

        double hours = duration.toMillis() / 3600000.0;
        double avgSpeedKmh = totalDistanceKm / hours;
        double maxSpeedKmh = smoothedSpeeds.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        return TravelMode.classify(avgSpeedKmh, maxSpeedKmh, totalDistanceKm);
    }

}
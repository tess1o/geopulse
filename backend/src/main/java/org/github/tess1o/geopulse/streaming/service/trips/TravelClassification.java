package org.github.tess1o.geopulse.streaming.service.trips;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;


import java.time.Duration;

import static org.github.tess1o.geopulse.streaming.model.shared.TripType.*;

@ApplicationScoped
@Slf4j
public class TravelClassification {

    /**
     * Classify travel type using pre-calculated GPS statistics from TimelineTripEntity.
     * This method provides more accurate classification using real GPS data.
     *
     * @param trip   the timeline trip with pre-calculated GPS statistics
     * @param config timeline configuration containing classification thresholds
     * @return classified trip type
     */
    public TripType classifyTravelType(TimelineTripEntity trip, TimelineConfig config) {
        TripType initialClassification;
        // If GPS statistics are available, use enhanced classification
        if (trip.getAvgGpsSpeed() != null && trip.getMaxGpsSpeed() != null) {
            TripGpsStatistics statistics = new TripGpsStatistics(trip.getAvgGpsSpeed(), trip.getMaxGpsSpeed(), trip.getSpeedVariance(), trip.getLowAccuracyPointsCount());
            initialClassification = classifyWithGpsStatistics(statistics, Duration.ofSeconds(trip.getTripDuration()), trip.getDistanceMeters(), config);
        } else {
            // Fallback to path-based classification for legacy trips
            log.debug("GPS statistics not available for trip {}, falling back to path-based classification", trip.getId());
            initialClassification = classifyWithoutSpeed(config, Duration.ofSeconds(trip.getTripDuration()), trip.getDistanceMeters());
        }

        // Final verification to correct unrealistic classifications
        return verifyAndCorrectClassification(initialClassification, trip.getDistanceMeters(), trip.getTripDuration(), config);
    }

    public TripType classifyTravelType(TripGpsStatistics statistics, Duration tripDuration, long distanceMeters, TimelineConfig config) {
        TripType initialClassification = classifyWithGpsStatistics(statistics, tripDuration, distanceMeters, config);
        return verifyAndCorrectClassification(initialClassification, distanceMeters, tripDuration.getSeconds(), config);
    }

    /**
     * Enhanced classification using pre-calculated GPS statistics.
     * Provides more accurate results by using real GPS speed data and variance analysis.
     */
    private TripType classifyWithGpsStatistics(TripGpsStatistics statistics, Duration tripDuration, long distanceMeters, TimelineConfig config) {
        if (statistics == null || !statistics.hasValidData()) {
            log.warn("Trip statistics is null or has no valid data, falling back to calculate based on distance and duration");
            return classifyWithoutSpeed(config, tripDuration, distanceMeters);
        }
        // Convert speeds from m/s to km/h
        double avgSpeedKmh = statistics.avgGpsSpeed() * 3.6;
        double maxSpeedKmh = statistics.maxGpsSpeed() * 3.6;
        double distanceKm = distanceMeters / 1000.0;

        // Enhanced classification using speed variance
        Double speedVariance = statistics.speedVariance();
        Integer lowAccuracyCount = statistics.lowAccuracyPointsCount();

        return classifyEnhanced(avgSpeedKmh, maxSpeedKmh, distanceKm, speedVariance, lowAccuracyCount, config);
    }

    private TripType classifyWithoutSpeed(TimelineConfig config, Duration tripDuration, long distanceMeters) {
        double distanceKm = distanceMeters / 1000.0;
        double hours = tripDuration.getSeconds() / 3600.0;
        double avgSpeedKmh = hours > 0 ? distanceKm / hours : 0.0;
        if (hours > 0 && distanceKm > 0) {
            return classify(avgSpeedKmh, avgSpeedKmh, distanceKm, config);
        } else {
            return UNKNOWN;
        }
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
     * Classify trip type based on speed and distance metrics.
     *
     * @param avgSpeedKmh     average speed in km/h
     * @param maxSpeedKmh     maximum speed in km/h
     * @param totalDistanceKm total distance in km
     * @param config          timeline configuration containing classification thresholds
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

    /**
     * Verifies and corrects the trip classification based on realistic speed constraints.
     * If a trip is classified as WALK but the speed is unrealistically high, it's corrected to CAR.
     *
     * @param tripType       the initial trip classification
     * @param distanceMeters the total distance of the trip in meters
     * @param tripDuration   the duration of the trip in seconds
     * @param config         the timeline configuration
     * @return the corrected trip type
     */
    private TripType verifyAndCorrectClassification(TripType tripType, long distanceMeters, long tripDuration, TimelineConfig config) {
        if (tripType == WALK) {
            final double distanceKm = distanceMeters / 1000.0;
            final double hours = tripDuration / 3600.0;
            final double avgSpeedKmh = hours > 0 ? distanceKm / hours : 0.0;
            final double coefficient = 1.2;

            // If speed is unrealistically high for walking, re-classify as CAR.
            if (avgSpeedKmh > config.getWalkingMaxMaxSpeed() * coefficient) {
                log.debug("Correcting WALK to CAR due to unrealistic speed: {} km/h", avgSpeedKmh);
                return CAR;
            }
        }
        return tripType;
    }
}
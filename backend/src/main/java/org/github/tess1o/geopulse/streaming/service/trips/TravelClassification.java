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
     * Now supports optional trip types: BICYCLE, TRAIN, FLIGHT.
     */
    private TripType classifyEnhanced(double avgSpeedKmh, double maxSpeedKmh, double distanceKm,
                                      Double speedVariance, Integer lowAccuracyCount, TimelineConfig config) {

        // Use new classification method with optional types
        return classifyWithOptionalTypes(avgSpeedKmh, maxSpeedKmh, speedVariance, config);
    }

    /**
     * Classification with optional trip types (BICYCLE, TRAIN, FLIGHT).
     *
     * CRITICAL PRIORITY ORDER:
     * 1. FLIGHT - highest priority (unambiguous speeds 400+ km/h avg OR 500+ km/h peak)
     * 2. TRAIN - high speed with low variance (30-150 km/h avg, variance < 15, peak >= 80 km/h)
     * 3. BICYCLE - medium speeds (8-25 km/h avg, peak <= 35 km/h) - MUST be before CAR!
     * 4. CAR - motorized transport (10+ km/h avg OR 15+ km/h peak)
     * 5. WALK - low speeds (<= 6 km/h avg, <= 8 km/h peak)
     * 6. UNKNOWN - fallback
     *
     * @param avgSpeedKmh   Average speed in km/h
     * @param maxSpeedKmh   Maximum speed in km/h
     * @param speedVariance Speed variance (null if not available)
     * @param config        Timeline configuration
     * @return Classified trip type
     */
    private TripType classifyWithOptionalTypes(double avgSpeedKmh, double maxSpeedKmh,
                                               Double speedVariance, TimelineConfig config) {

        // 1. FLIGHT - highest priority (unambiguous speeds)
        //    Uses OR logic to handle long taxi/ground time
        if (Boolean.TRUE.equals(config.getFlightEnabled()) &&
                (avgSpeedKmh >= config.getFlightMinAvgSpeed() ||
                        maxSpeedKmh >= config.getFlightMinMaxSpeed())) {
            return FLIGHT;
        }

        // 2. TRAIN - high speed with low variance and minimum peak speed
        //    Must be checked BEFORE CAR to distinguish based on variance
        //    trainMinMaxSpeed filters out station-only trips
        if (Boolean.TRUE.equals(config.getTrainEnabled()) &&
                avgSpeedKmh >= config.getTrainMinAvgSpeed() &&
                avgSpeedKmh <= config.getTrainMaxAvgSpeed() &&
                maxSpeedKmh >= config.getTrainMinMaxSpeed() &&
                maxSpeedKmh <= config.getTrainMaxMaxSpeed() &&
                speedVariance != null &&
                speedVariance < config.getTrainMaxSpeedVariance()) {
            return TRAIN;
        }

        // 3. BICYCLE - medium speeds (only if enabled)
        //    CRITICAL: Must be checked BEFORE CAR due to overlapping speed ranges
        //    Also captures running/jogging (consider displaying as "Cycling/Running")
        if (Boolean.TRUE.equals(config.getBicycleEnabled()) &&
                avgSpeedKmh >= config.getBicycleMinAvgSpeed() &&
                avgSpeedKmh <= config.getBicycleMaxAvgSpeed() &&
                maxSpeedKmh <= config.getBicycleMaxMaxSpeed()) {
            return BICYCLE;
        }

        // 4. CAR - motorized transport (mandatory)
        //    CRITICAL: Must be checked AFTER BICYCLE to avoid capturing bicycle trips
        if (avgSpeedKmh >= config.getCarMinAvgSpeed() ||
                maxSpeedKmh >= config.getCarMinMaxSpeed()) {
            return CAR;
        }

        // 5. WALK - low speeds (mandatory)
        if (avgSpeedKmh <= config.getWalkingMaxAvgSpeed() &&
                maxSpeedKmh <= config.getWalkingMaxMaxSpeed()) {
            return WALK;
        }

        // 6. UNKNOWN - fallback for edge cases
        return UNKNOWN;
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
package org.github.tess1o.geopulse.streaming.service.trips;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;


import java.time.Duration;

import static org.github.tess1o.geopulse.streaming.model.shared.TripType.*;

/**
 * Service for classifying trip types based on GPS movement patterns.
 *
 * Supports both mandatory types (WALK, CAR) and optional types (BICYCLE, TRAIN, FLIGHT).
 * Classification is based on speed analysis and movement characteristics.
 *
 * CRITICAL CLASSIFICATION ORDER:
 * 1. FLIGHT - highest priority (400+ km/h avg OR 500+ km/h peak)
 * 2. TRAIN - high speed with low variance (30-150 km/h, variance < 15)
 * 3. BICYCLE - medium speeds (8-25 km/h) - MUST be before CAR!
 * 4. CAR - motorized transport (10+ km/h avg OR 15+ km/h peak)
 * 5. WALK - low speeds (<= 6 km/h avg, <= 8 km/h peak)
 * 6. UNKNOWN - fallback
 */
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
            TripGpsStatistics statistics = new TripGpsStatistics(
                    trip.getAvgGpsSpeed(),
                    trip.getMaxGpsSpeed(),
                    trip.getSpeedVariance(),
                    trip.getLowAccuracyPointsCount()
            );
            initialClassification = classifyWithGpsStatistics(statistics,
                    Duration.ofSeconds(trip.getTripDuration()),
                    trip.getDistanceMeters(),
                    config);
        } else {
            // Fallback to path-based classification for legacy trips
            log.debug("GPS statistics not available for trip {}, falling back to path-based classification",
                    trip.getId());
            initialClassification = classifyWithoutGpsStatistics(config,
                    Duration.ofSeconds(trip.getTripDuration()),
                    trip.getDistanceMeters());
        }

        // Final verification to correct unrealistic classifications
        return verifyAndCorrectClassification(initialClassification,
                trip.getDistanceMeters(),
                trip.getTripDuration(),
                config);
    }

    /**
     * Classify travel type using provided GPS statistics.
     *
     * @param statistics  GPS statistics (avg speed, max speed, variance)
     * @param tripDuration duration of the trip
     * @param distanceMeters total distance in meters
     * @param config timeline configuration
     * @return classified trip type
     */
    public TripType classifyTravelType(TripGpsStatistics statistics,
                                      Duration tripDuration,
                                      long distanceMeters,
                                      TimelineConfig config) {
        TripType initialClassification = classifyWithGpsStatistics(statistics,
                tripDuration,
                distanceMeters,
                config);
        return verifyAndCorrectClassification(initialClassification,
                distanceMeters,
                tripDuration.getSeconds(),
                config);
    }

    /**
     * Classification using GPS statistics.
     * Converts speeds to km/h and delegates to the main classification algorithm.
     */
    private TripType classifyWithGpsStatistics(TripGpsStatistics statistics,
                                              Duration tripDuration,
                                              long distanceMeters,
                                              TimelineConfig config) {
        if (statistics == null || !statistics.hasValidData()) {
            log.warn("Trip statistics is null or has no valid data, falling back to calculate based on distance and duration");
            return classifyWithoutGpsStatistics(config, tripDuration, distanceMeters);
        }

        // Convert speeds from m/s to km/h
        double avgSpeedKmh = statistics.avgGpsSpeed() * 3.6;
        double maxSpeedKmh = statistics.maxGpsSpeed() * 3.6;

        return classifyBySpeed(avgSpeedKmh, maxSpeedKmh, statistics.speedVariance(), config);
    }

    /**
     * Fallback classification when GPS statistics are not available.
     * Calculates average speed from distance and duration.
     */
    private TripType classifyWithoutGpsStatistics(TimelineConfig config,
                                                 Duration tripDuration,
                                                 long distanceMeters) {
        double distanceKm = distanceMeters / 1000.0;
        double hours = tripDuration.getSeconds() / 3600.0;
        double avgSpeedKmh = hours > 0 ? distanceKm / hours : 0.0;

        if (hours > 0 && distanceKm > 0) {
            // When no max speed available, use avg speed for both
            return classifyBySpeed(avgSpeedKmh, avgSpeedKmh, null, config);
        } else {
            return UNKNOWN;
        }
    }

    /**
     * Main classification algorithm supporting optional trip types.
     *
     * CRITICAL PRIORITY ORDER:
     * 1. FLIGHT - highest priority (unambiguous speeds)
     * 2. TRAIN - before CAR (uses variance discriminator)
     * 3. BICYCLE - BEFORE CAR (overlapping speeds)
     * 4. CAR - mandatory type
     * 5. WALK - mandatory type
     * 6. UNKNOWN - fallback
     *
     * @param avgSpeedKmh   average speed in km/h
     * @param maxSpeedKmh   maximum speed in km/h
     * @param speedVariance speed variance (null if not available)
     * @param config        timeline configuration
     * @return classified trip type
     */
    private TripType classifyBySpeed(double avgSpeedKmh,
                                    double maxSpeedKmh,
                                    Double speedVariance,
                                    TimelineConfig config) {

        // 1. FLIGHT - highest priority (unambiguous speeds)
        //    Uses OR logic: avg >= 400 OR max >= 500
        //    Handles flights with long taxi/ground time
        if (Boolean.TRUE.equals(config.getFlightEnabled()) &&
                (avgSpeedKmh >= config.getFlightMinAvgSpeed() ||
                 maxSpeedKmh >= config.getFlightMinMaxSpeed())) {
            return FLIGHT;
        }

        // 2. TRAIN - high speed with low variance
        //    MUST be checked BEFORE CAR to use variance as discriminator
        //    Requires: speed in range + low variance + high peak (filters station stops)
        if (Boolean.TRUE.equals(config.getTrainEnabled()) &&
                avgSpeedKmh >= config.getTrainMinAvgSpeed() &&
                avgSpeedKmh <= config.getTrainMaxAvgSpeed() &&
                maxSpeedKmh >= config.getTrainMinMaxSpeed() &&  // Filters station-only trips
                maxSpeedKmh <= config.getTrainMaxMaxSpeed() &&
                speedVariance != null &&
                speedVariance < config.getTrainMaxSpeedVariance()) {
            return TRAIN;
        }

        // 3. BICYCLE - medium speeds
        //    CRITICAL: MUST be checked BEFORE CAR due to overlapping speeds (8-25 km/h)
        //    Also captures running/jogging (8-15 km/h)
        if (Boolean.TRUE.equals(config.getBicycleEnabled()) &&
                avgSpeedKmh >= config.getBicycleMinAvgSpeed() &&
                avgSpeedKmh <= config.getBicycleMaxAvgSpeed() &&
                maxSpeedKmh <= config.getBicycleMaxMaxSpeed()) {
            return BICYCLE;
        }

        // 4. CAR - motorized transport (mandatory)
        //    CRITICAL: Checked AFTER BICYCLE to avoid capturing bicycle trips
        //    Uses OR logic: avg >= 10 OR max >= 15
        if (avgSpeedKmh >= config.getCarMinAvgSpeed() ||
                maxSpeedKmh >= config.getCarMinMaxSpeed()) {
            return CAR;
        }

        // 5. WALK - low speeds (mandatory)
        //    Both avg AND max must be within walking range
        if (avgSpeedKmh <= config.getWalkingMaxAvgSpeed() &&
                maxSpeedKmh <= config.getWalkingMaxMaxSpeed()) {
            return WALK;
        }

        // 6. UNKNOWN - fallback for edge cases
        //    Example: 7 km/h avg (above walk, below bicycle if disabled, below car min)
        return UNKNOWN;
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
    private TripType verifyAndCorrectClassification(TripType tripType,
                                                   long distanceMeters,
                                                   long tripDuration,
                                                   TimelineConfig config) {
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

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
 * <p>
 * Supports both mandatory types (WALK, CAR) and optional types (BICYCLE, TRAIN, FLIGHT).
 * Classification is based on speed analysis and movement characteristics.
 * <p>
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
     * @param statistics     GPS statistics (avg speed, max speed, variance)
     * @param tripDuration   duration of the trip
     * @param distanceMeters total distance in meters
     * @param config         timeline configuration
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
     * Includes sanity check to detect unreliable GPS speeds.
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

        // Sanity check: Compare GPS-based speed with distance/duration-based speed
        // GPS speeds can be unreliable due to noise, especially for short trips
        double distanceKm = distanceMeters / 1000.0;
        double hours = tripDuration.getSeconds() / 3600.0;
        double calculatedAvgSpeedKmh = hours > 0 ? distanceKm / hours : 0.0;

        // CRITICAL: Detect GPS noise where calculated speed is impossibly high AND GPS disagrees
        // Example: 1068 km in 270 seconds = 14,238 km/h (supersonic!) but GPS shows 1.2 km/h (walking)
        // This is GPS noise with two inaccurate points far apart
        final double MAX_REALISTIC_SPEED = 1200.0; // Nothing exceeds this on Earth
        if (calculatedAvgSpeedKmh > MAX_REALISTIC_SPEED) {
            // If calculated is impossible BUT GPS speeds are reasonable (within realistic range),
            // trust GPS - this happens when distance calculation is wrong but GPS sampling is good
            // Example: Flight with good GPS (750 km/h) but bad distance calc (1500 km/h) due to route vs straight-line
            if (avgSpeedKmh <= MAX_REALISTIC_SPEED && maxSpeedKmh <= MAX_REALISTIC_SPEED) {
                // GPS is realistic - use it instead of calculated
                log.debug("Calculated speed ({} km/h) is unrealistic, but GPS speeds are reasonable ({} km/h avg, {} km/h max). Using GPS.",
                        String.format("%f", calculatedAvgSpeedKmh),
                        String.format("%f", avgSpeedKmh),
                        String.format("%f", maxSpeedKmh));
                // Continue with GPS speeds (don't return UNKNOWN)
            } else {
                // Both calculated AND GPS are unrealistic - this is noise
                log.warn("Impossible calculated speed ({} km/h) detected. GPS shows {} km/h avg, {} km/h max. " +
                                "Distance: {} m, Duration: {} s. This is GPS noise - using UNKNOWN.",
                        String.format("%f", calculatedAvgSpeedKmh),
                        String.format("%f", avgSpeedKmh),
                        String.format("%f", maxSpeedKmh),
                        distanceMeters,
                        tripDuration.getSeconds());
                // Don't try to classify this - it's pure noise
                return UNKNOWN;
            }
        }

        // Adaptive threshold: for low speeds (< 20 km/h), use 2x threshold
        // For higher speeds, use absolute difference to avoid false positives on flights/trains
        boolean isUnreliable = false;
        if (calculatedAvgSpeedKmh > 0) {
            if (calculatedAvgSpeedKmh < 20.0) {
                // Low speed: GPS should not be more than 2x calculated
                isUnreliable = avgSpeedKmh > calculatedAvgSpeedKmh * 2.0;
            } else if (avgSpeedKmh > 200.0) {
                // Very high GPS speed (likely flight/high-speed train): Trust GPS over calculated
                // Calculated speed from distance/duration can be wrong due to route vs straight-line
                // If GPS shows flight speeds (200+ km/h), it's very likely correct
                isUnreliable = false;  // Trust GPS
            } else {
                // Medium-high speed (20-200 km/h): GPS should not differ by more than 50%
                double difference = Math.abs(avgSpeedKmh - calculatedAvgSpeedKmh);
                double percentDiff = difference / calculatedAvgSpeedKmh;
                isUnreliable = percentDiff > 0.5;  // 50% difference
            }
        }

        if (isUnreliable) {
            // GPS seems unreliable compared to calculated speed
            // BUT: If calculated speed is unrealistically LOW (< 1 km/h), it means distance calc is wrong
            // Example: Test data with imprecise coordinates showing 3m distance but GPS shows 63 km/h
            // In this case, trust GPS instead
            if (calculatedAvgSpeedKmh < 1.0 && avgSpeedKmh > 5.0) {
                log.debug("Calculated speed is unrealistically low ({} km/h) but GPS shows movement ({} km/h). " +
                                "Distance calculation likely wrong ({}m in {}s). Trusting GPS.",
                        String.format("%f", calculatedAvgSpeedKmh),
                        String.format("%f", avgSpeedKmh),
                        distanceMeters,
                        tripDuration.getSeconds());
                // Trust GPS - calculated distance is clearly wrong
                return classifyBySpeed(avgSpeedKmh, maxSpeedKmh, statistics.speedVariance(), config);
            }

            // Check if GPS max speed is also unreliable
            // If max speed is more than 5x calculated avg, it's likely GPS noise
            double estimatedReasonableMax = calculatedAvgSpeedKmh * 1.5;
            double adjustedMaxSpeed;

            if (maxSpeedKmh > calculatedAvgSpeedKmh * 5.0) {
                // GPS max is too high - likely noise (e.g., 29 km/h for a 3.6 km/h walking trip)
                // Use a reasonable estimate instead
                adjustedMaxSpeed = estimatedReasonableMax;
                log.warn("GPS avg speed ({} km/h) and max speed ({} km/h) are unreliable (calculated: {} km/h). " +
                                "Distance: {} m, Duration: {} s. Using calculated speeds.",
                        String.format("%f", avgSpeedKmh),
                        String.format("%f", maxSpeedKmh),
                        String.format("%f", calculatedAvgSpeedKmh),
                        distanceMeters,
                        tripDuration.getSeconds());
            } else {
                // GPS max seems reasonable relative to calculated avg - keep it
                // This handles: car trip with traffic stops (10 km/h avg, 49 km/h max - realistic)
                adjustedMaxSpeed = maxSpeedKmh;
                log.warn("GPS avg speed ({} km/h) is unreliable but max speed ({} km/h) seems reasonable (calculated avg: {} km/h). " +
                                "Distance: {} m, Duration: {} s. Using calculated avg but keeping GPS max.",
                        String.format("%f", avgSpeedKmh),
                        String.format("%f", maxSpeedKmh),
                        String.format("%f", calculatedAvgSpeedKmh),
                        distanceMeters,
                        tripDuration.getSeconds());
            }

            return classifyBySpeed(calculatedAvgSpeedKmh, adjustedMaxSpeed, statistics.speedVariance(), config);
        }

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
     * <p>
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

        // 0. SANITY CHECK: Reject impossible speeds (GPS noise detection)
        //    Nothing on Earth exceeds 1200 km/h sustained (commercial jets max ~900 km/h)
        //    This catches GPS noise creating phantom supersonic trips
        final double MAX_REALISTIC_SPEED_KMH = 1200.0;
        if (avgSpeedKmh > MAX_REALISTIC_SPEED_KMH || maxSpeedKmh > MAX_REALISTIC_SPEED_KMH) {
            log.warn("Impossible speed detected (avg: {} km/h, max: {} km/h). Likely GPS noise. Classifying as UNKNOWN.",
                    String.format("%f", avgSpeedKmh),
                    String.format("%f", maxSpeedKmh));
            return UNKNOWN;
        }

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

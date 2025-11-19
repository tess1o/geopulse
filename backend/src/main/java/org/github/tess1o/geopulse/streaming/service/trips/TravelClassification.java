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

    // ============================================
    // GPS NOISE DETECTION THRESHOLDS
    // ============================================

    /**
     * Maximum realistic speed for any trip on Earth (km/h).
     * Commercial jets cruise at ~900 km/h, so 1200 km/h is a safe upper bound.
     * Any speed exceeding this is considered GPS noise.
     */
    private static final double MAX_REALISTIC_SPEED_KMH = 1200.0;

    /**
     * Speed threshold for detecting unrealistically low calculated speeds (km/h).
     * If calculated speed is below this but GPS shows movement, calculated distance is likely wrong.
     * Example: 3m distance over 3 minutes = 0.06 km/h (clearly wrong if GPS shows 60 km/h).
     */
    private static final double MIN_REALISTIC_CALCULATED_SPEED_KMH = 1.0;

    /**
     * Minimum GPS speed to indicate actual movement (km/h).
     * Used to distinguish between stopped/very slow movement vs GPS noise.
     */
    private static final double MIN_GPS_MOVEMENT_SPEED_KMH = 3.0;

    /**
     * Speed threshold for distinguishing between low and high speed trips (km/h).
     * Below this: use stricter GPS validation (2x threshold).
     * Above this: use percentage-based validation (50% tolerance).
     */
    private static final double LOW_SPEED_THRESHOLD_KMH = 20.0;

    /**
     * Speed threshold for very high speed trips (km/h).
     * Above this, always trust GPS over calculated speed.
     * Rationale: Distance calculation (straight-line) is unreliable for flights/high-speed trains.
     */
    private static final double HIGH_SPEED_GPS_TRUST_THRESHOLD_KMH = 200.0;

    /**
     * Maximum allowed ratio of GPS avg speed to calculated avg speed for low-speed trips.
     * If GPS avg > calculated * this ratio, GPS is considered unreliable.
     * Example: GPS 12 km/h vs calculated 5 km/h = 2.4x ratio (exceeds threshold, use calculated).
     */
    private static final double LOW_SPEED_GPS_RELIABILITY_RATIO = 2.0;

    /**
     * Maximum allowed percentage difference between GPS and calculated speeds for medium-high speed trips.
     * If difference exceeds this percentage, GPS is considered unreliable.
     * Example: GPS 80 km/h vs calculated 60 km/h = 33% difference (within 50% tolerance, GPS is reliable).
     */
    private static final double HIGH_SPEED_GPS_RELIABILITY_PERCENT = 0.5; // 50%

    /**
     * Maximum allowed ratio of GPS max speed to calculated avg speed.
     * If GPS max > calculated avg * this ratio, GPS max is considered unreliable noise.
     * Example: GPS max 29 km/h vs calculated avg 5 km/h = 5.8x ratio (exceeds 5x, use estimated max).
     */
    private static final double GPS_MAX_SPEED_NOISE_RATIO = 5.0;

    /**
     * Multiplier for estimating reasonable max speed from calculated avg speed.
     * Used when GPS max speed is deemed unreliable.
     * Example: Calculated avg 10 km/h → estimated max = 10 * 1.5 = 15 km/h.
     */
    private static final double ESTIMATED_MAX_SPEED_MULTIPLIER = 1.5;

    /**
     * Coefficient for walk speed verification (multiplier).
     * If calculated speed exceeds walkingMaxMaxSpeed * this coefficient, reclassify as CAR.
     */
    private static final double WALK_VERIFICATION_COEFFICIENT = 1.2;

    // ============================================
    // CLASSIFICATION METHODS
    // ============================================

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
        if (calculatedAvgSpeedKmh > MAX_REALISTIC_SPEED_KMH) {
            // If calculated is impossible BUT GPS speeds are reasonable (within realistic range),
            // trust GPS - this happens when distance calculation is wrong but GPS sampling is good
            // Example: Flight with good GPS (750 km/h) but bad distance calc (1500 km/h) due to route vs straight-line
            if (avgSpeedKmh <= MAX_REALISTIC_SPEED_KMH && maxSpeedKmh <= MAX_REALISTIC_SPEED_KMH) {
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

        // Adaptive GPS reliability check based on speed range
        boolean isUnreliable = false;
        if (calculatedAvgSpeedKmh > 0) {
            if (calculatedAvgSpeedKmh < LOW_SPEED_THRESHOLD_KMH) {
                // Low speed trips: Use strict ratio-based validation
                // GPS should not exceed calculated by more than 2x
                isUnreliable = avgSpeedKmh > calculatedAvgSpeedKmh * LOW_SPEED_GPS_RELIABILITY_RATIO;
            } else if (avgSpeedKmh > HIGH_SPEED_GPS_TRUST_THRESHOLD_KMH) {
                // Very high GPS speed (likely flight/high-speed train): Always trust GPS
                // Calculated speed from distance/duration is unreliable due to route vs straight-line distance
                // If GPS shows flight speeds (200+ km/h), it's very likely correct
                isUnreliable = false;  // Trust GPS
            } else {
                // Medium-high speed trips: Use percentage-based validation
                // GPS should not differ from calculated by more than 50%
                double difference = Math.abs(avgSpeedKmh - calculatedAvgSpeedKmh);
                double percentDiff = difference / calculatedAvgSpeedKmh;
                isUnreliable = percentDiff > HIGH_SPEED_GPS_RELIABILITY_PERCENT;
            }
        }

        if (isUnreliable) {
            // GPS seems unreliable compared to calculated speed
            // BUT: If calculated speed is unrealistically LOW, it means distance calculation is wrong
            // Example: Test data with imprecise coordinates showing 3m distance but GPS shows 63 km/h
            // In this case, trust GPS instead
            if (calculatedAvgSpeedKmh < MIN_REALISTIC_CALCULATED_SPEED_KMH && avgSpeedKmh > MIN_GPS_MOVEMENT_SPEED_KMH) {
                log.debug("Calculated speed is unrealistically low ({} km/h) but GPS shows movement ({} km/h). " +
                                "Distance calculation likely wrong ({}m in {}s). Trusting GPS.",
                        String.format("%f", calculatedAvgSpeedKmh),
                        String.format("%f", avgSpeedKmh),
                        distanceMeters,
                        tripDuration.getSeconds());
                // Trust GPS - calculated distance is clearly wrong
                return classifyBySpeed(avgSpeedKmh, maxSpeedKmh, statistics.speedVariance(), config);
            }

            // Check if GPS max speed is also unreliable (noise spike detection)
            // If max speed exceeds calculated avg by a large ratio, it's likely a GPS noise spike
            double estimatedReasonableMax = calculatedAvgSpeedKmh * ESTIMATED_MAX_SPEED_MULTIPLIER;
            double adjustedMaxSpeed;

            if (maxSpeedKmh > calculatedAvgSpeedKmh * GPS_MAX_SPEED_NOISE_RATIO) {
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

            // If calculated speed exceeds walking threshold with tolerance, re-classify as CAR
            // Uses WALK_VERIFICATION_COEFFICIENT (1.2x) to allow for GPS inaccuracies in short trips
            if (avgSpeedKmh > config.getWalkingMaxMaxSpeed() * WALK_VERIFICATION_COEFFICIENT) {
                log.debug("Correcting WALK to CAR due to unrealistic speed: {} km/h (exceeds {} km/h threshold)",
                        avgSpeedKmh,
                        config.getWalkingMaxMaxSpeed() * WALK_VERIFICATION_COEFFICIENT);
                return CAR;
            }
        }
        return tripType;
    }
}

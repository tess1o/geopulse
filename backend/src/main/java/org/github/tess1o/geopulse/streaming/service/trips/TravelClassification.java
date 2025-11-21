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
    // HELPER RECORD
    // ============================================

    /**
     * Result of GPS reliability validation.
     * Contains the final speeds to use for classification after validation.
     *
     * @param finalAvgSpeed The average speed to use (either GPS or calculated)
     * @param finalMaxSpeed The max speed to use (either GPS, calculated, or estimated)
     */
    private record GpsReliabilityResult(double finalAvgSpeed, double finalMaxSpeed) {}

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
     * Includes comprehensive GPS noise detection and reliability validation.
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

        // Calculate speed from distance and duration for comparison
        double calculatedAvgSpeedKmh = calculateAverageSpeed(distanceMeters, tripDuration);

        // Step 1: Check for impossible supersonic speeds (GPS noise)
        TripType supersonicCheck = detectSupersonicNoise(avgSpeedKmh, maxSpeedKmh, calculatedAvgSpeedKmh,
                                                         distanceMeters, tripDuration);
        if (supersonicCheck != null) {
            return supersonicCheck;
        }

        // Step 2: Validate GPS reliability against calculated speed
        GpsReliabilityResult reliabilityResult = validateGpsReliability(
                avgSpeedKmh, maxSpeedKmh, calculatedAvgSpeedKmh,
                distanceMeters, tripDuration, statistics, config
        );

        return classifyBySpeed(reliabilityResult.finalAvgSpeed(), reliabilityResult.finalMaxSpeed(),
                statistics.speedVariance(), config);
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

    // ============================================
    // GPS NOISE DETECTION & VALIDATION HELPERS
    // ============================================

    /**
     * Calculate average speed from distance and duration.
     *
     * @param distanceMeters total distance in meters
     * @param tripDuration   trip duration
     * @return average speed in km/h
     */
    private double calculateAverageSpeed(long distanceMeters, Duration tripDuration) {
        double distanceKm = distanceMeters / 1000.0;
        double hours = tripDuration.getSeconds() / 3600.0;
        return hours > 0 ? distanceKm / hours : 0.0;
    }

    /**
     * Detects impossible supersonic speeds indicating GPS noise.
     * Returns UNKNOWN if both calculated and GPS speeds are unrealistic.
     * Returns null if speeds are reasonable and classification should continue.
     *
     * @param avgSpeedKmh        GPS average speed
     * @param maxSpeedKmh        GPS max speed
     * @param calculatedAvgSpeedKmh Calculated average speed from distance/duration
     * @param distanceMeters     trip distance
     * @param tripDuration       trip duration
     * @return UNKNOWN if GPS noise detected, null otherwise
     */
    private TripType detectSupersonicNoise(double avgSpeedKmh, double maxSpeedKmh,
                                          double calculatedAvgSpeedKmh,
                                          long distanceMeters, Duration tripDuration) {
        if (calculatedAvgSpeedKmh > MAX_REALISTIC_SPEED_KMH) {
            // If calculated is impossible BUT GPS speeds are reasonable, trust GPS
            // Example: Flight with good GPS (750 km/h) but bad distance calc (1500 km/h)
            if (avgSpeedKmh <= MAX_REALISTIC_SPEED_KMH && maxSpeedKmh <= MAX_REALISTIC_SPEED_KMH) {
                log.debug("Calculated speed ({} km/h) is unrealistic, but GPS speeds are reasonable ({} km/h avg, {} km/h max). Using GPS.",
                        String.format("%f", calculatedAvgSpeedKmh),
                        String.format("%f", avgSpeedKmh),
                        String.format("%f", maxSpeedKmh));
                return null; // Continue with GPS speeds
            } else {
                // Both calculated AND GPS are unrealistic - this is pure noise
                log.warn("Impossible calculated speed ({} km/h) detected. GPS shows {} km/h avg, {} km/h max. " +
                                "Distance: {} m, Duration: {} s. This is GPS noise - using UNKNOWN.",
                        String.format("%f", calculatedAvgSpeedKmh),
                        String.format("%f", avgSpeedKmh),
                        String.format("%f", maxSpeedKmh),
                        distanceMeters,
                        tripDuration.getSeconds());
                return UNKNOWN;
            }
        }
        return null; // No supersonic noise detected
    }

    /**
     * Validates GPS reliability by comparing GPS speeds against calculated speeds.
     * Determines which speeds to use for final classification.
     *
     * @param avgSpeedKmh        GPS average speed
     * @param maxSpeedKmh        GPS max speed
     * @param calculatedAvgSpeedKmh Calculated average speed
     * @param distanceMeters     trip distance
     * @param tripDuration       trip duration
     * @param statistics         GPS statistics
     * @param config            timeline configuration
     * @return GpsReliabilityResult containing final speeds to use
     */
    private GpsReliabilityResult validateGpsReliability(double avgSpeedKmh, double maxSpeedKmh,
                                                        double calculatedAvgSpeedKmh,
                                                        long distanceMeters, Duration tripDuration,
                                                        TripGpsStatistics statistics, TimelineConfig config) {
        // Check if GPS is unreliable compared to calculated
        boolean isUnreliable = isGpsUnreliable(avgSpeedKmh, calculatedAvgSpeedKmh);

        if (!isUnreliable) {
            // GPS avg is reliable - but still check for max speed noise spikes
            double adjustedMaxSpeed = maxSpeedKmh;
            if (maxSpeedKmh > avgSpeedKmh * GPS_MAX_SPEED_NOISE_RATIO) {
                // Max speed spike is unrealistic compared to avg (e.g., 39 km/h max with 3.5 km/h avg)
                adjustedMaxSpeed = avgSpeedKmh * ESTIMATED_MAX_SPEED_MULTIPLIER;
                log.debug("GPS max speed ({} km/h) is noise spike ({}x avg {} km/h). Using estimated max: {} km/h",
                        String.format("%f", maxSpeedKmh),
                        String.format("%f", maxSpeedKmh / avgSpeedKmh),
                        String.format("%f", avgSpeedKmh),
                        String.format("%f", adjustedMaxSpeed));
            }
            return new GpsReliabilityResult(avgSpeedKmh, adjustedMaxSpeed);
        }

        // GPS seems unreliable - but check for special cases

        // Special case 1: Calculated speed is unrealistically LOW
        // If calculated < 1 km/h but GPS shows movement > 3 km/h, trust GPS
        if (calculatedAvgSpeedKmh < MIN_REALISTIC_CALCULATED_SPEED_KMH && avgSpeedKmh > MIN_GPS_MOVEMENT_SPEED_KMH) {
            log.debug("Calculated speed is unrealistically low ({} km/h) but GPS shows movement ({} km/h). " +
                            "Distance calculation likely wrong ({}m in {}s). Trusting GPS.",
                    String.format("%f", calculatedAvgSpeedKmh),
                    String.format("%f", avgSpeedKmh),
                    distanceMeters,
                    tripDuration.getSeconds());
            return new GpsReliabilityResult(avgSpeedKmh, maxSpeedKmh);
        }

        // GPS avg is unreliable - use calculated avg, but decide about max speed
        double adjustedMaxSpeed = adjustMaxSpeed(maxSpeedKmh, calculatedAvgSpeedKmh,
                avgSpeedKmh, distanceMeters, tripDuration);

        return new GpsReliabilityResult(calculatedAvgSpeedKmh, adjustedMaxSpeed);
    }

    /**
     * Checks if GPS speed is unreliable compared to calculated speed.
     * Uses adaptive thresholds based on speed range.
     *
     * @param avgSpeedKmh        GPS average speed
     * @param calculatedAvgSpeedKmh Calculated average speed
     * @return true if GPS is unreliable, false otherwise
     */
    private boolean isGpsUnreliable(double avgSpeedKmh, double calculatedAvgSpeedKmh) {
        if (calculatedAvgSpeedKmh <= 0) {
            return false; // Can't validate if calculated is zero
        }

        if (calculatedAvgSpeedKmh < LOW_SPEED_THRESHOLD_KMH) {
            // Low speed trips: Use strict ratio-based validation
            // GPS should not exceed calculated by more than 2x
            return avgSpeedKmh > calculatedAvgSpeedKmh * LOW_SPEED_GPS_RELIABILITY_RATIO;
        } else if (avgSpeedKmh > HIGH_SPEED_GPS_TRUST_THRESHOLD_KMH) {
            // Very high GPS speed (likely flight/high-speed train): Always trust GPS
            // Calculated speed from distance/duration is unreliable due to route vs straight-line
            return false;
        } else {
            // Medium-high speed trips: Use percentage-based validation
            // GPS should not differ from calculated by more than 50%
            double difference = Math.abs(avgSpeedKmh - calculatedAvgSpeedKmh);
            double percentDiff = difference / calculatedAvgSpeedKmh;
            return percentDiff > HIGH_SPEED_GPS_RELIABILITY_PERCENT;
        }
    }

    /**
     * Adjusts GPS max speed based on reliability checks.
     * If GPS max is too high compared to calculated, estimates a reasonable max.
     *
     * @param maxSpeedKmh        GPS max speed
     * @param calculatedAvgSpeedKmh Calculated average speed
     * @param avgSpeedKmh        GPS average speed
     * @param distanceMeters     trip distance
     * @param tripDuration       trip duration
     * @return adjusted max speed
     */
    private double adjustMaxSpeed(double maxSpeedKmh, double calculatedAvgSpeedKmh,
                                  double avgSpeedKmh, long distanceMeters, Duration tripDuration) {
        if (maxSpeedKmh > calculatedAvgSpeedKmh * GPS_MAX_SPEED_NOISE_RATIO) {
            // GPS max is too high - likely noise spike
            // Use reasonable estimate instead
            double estimatedMax = calculatedAvgSpeedKmh * ESTIMATED_MAX_SPEED_MULTIPLIER;
            log.warn("GPS avg speed ({} km/h) and max speed ({} km/h) are unreliable (calculated: {} km/h). " +
                            "Distance: {} m, Duration: {} s. Using calculated speeds.",
                    String.format("%f", avgSpeedKmh),
                    String.format("%f", maxSpeedKmh),
                    String.format("%f", calculatedAvgSpeedKmh),
                    distanceMeters,
                    tripDuration.getSeconds());
            return estimatedMax;
        } else {
            // GPS max seems reasonable - keep it
            // This handles: car trip with traffic stops (10 km/h avg, 49 km/h max)
            log.warn("GPS avg speed ({} km/h) is unreliable but max speed ({} km/h) seems reasonable (calculated avg: {} km/h). " +
                            "Distance: {} m, Duration: {} s. Using calculated avg but keeping GPS max.",
                    String.format("%f", avgSpeedKmh),
                    String.format("%f", maxSpeedKmh),
                    String.format("%f", calculatedAvgSpeedKmh),
                    distanceMeters,
                    tripDuration.getSeconds());
            return maxSpeedKmh;
        }
    }
}

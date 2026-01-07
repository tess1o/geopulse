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
 * GPS RELIABILITY VALIDATION (BIDIRECTIONAL):
 * - Compares GPS-reported speeds against calculated speeds (distance/duration)
 * - LOW SPEED trips (&lt; 20 km/h): Uses strict ratio-based validation
 *   - GPS too HIGH: Rejects if GPS &gt; calculated × 2.0 (GPS noise/errors)
 *   - GPS too LOW: Rejects if GPS &lt; calculated / 1.3 (GPS underreporting, averaging stops)
 * - MEDIUM SPEED trips (20-200 km/h): Uses percentage-based validation (50% tolerance)
 * - HIGH SPEED trips (&gt; 200 km/h): Always trusts GPS (straight-line distance unreliable)
 * <p>
 * CRITICAL CLASSIFICATION ORDER:
 * 1. FLIGHT - highest priority (400+ km/h avg OR 500+ km/h peak)
 * 2. TRAIN - high speed with low variance (30-150 km/h, variance &lt; 15)
 * 3. BICYCLE - medium speeds (8-25 km/h) - checked before RUNNING
 * 4. RUNNING - medium-low speeds (7-14 km/h) - MUST be before CAR!
 * 5. CAR - motorized transport (10+ km/h avg OR 15+ km/h peak)
 * 6. WALK - low speeds (&lt;= 6 km/h avg, &lt;= 8 km/h peak)
 * 7. UNKNOWN - fallback
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
     * Maximum allowed ratio of GPS avg speed to calculated avg speed for low-speed trips (GPS too high).
     * If GPS avg > calculated * this ratio, GPS is considered unreliable.
     * Example: GPS 12 km/h vs calculated 5 km/h = 2.4x ratio (exceeds threshold, use calculated).
     */
    private static final double LOW_SPEED_GPS_RELIABILITY_RATIO = 2.0;

    /**
     * Minimum allowed ratio of GPS avg speed to calculated avg speed for low-speed trips (GPS too low).
     * If GPS avg < calculated / this ratio, GPS is considered unreliable.
     * Example: GPS 5.5 km/h vs calculated 7.5 km/h: 5.5 < 7.5/1.3 = 5.77 (GPS too low, use calculated).
     * This catches cases where GPS underreports speed due to averaging stops or poor accuracy.
     * Value of 1.3 allows ~23% variance, which is reasonable for low-speed GPS measurements.
     */
    private static final double LOW_SPEED_GPS_MIN_RELIABILITY_RATIO = 1.3;

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
    // DISTANCE-BASED CLASSIFICATION THRESHOLDS
    // ============================================

    /**
     * Extreme distance threshold (km).
     * Distances exceeding this are almost certainly flights (no realistic ground transport alternative).
     * Used as primary discriminator for long-haul flights.
     */
    private static final double EXTREME_FLIGHT_DISTANCE_KM = 1000.0;

    /**
     * Minimum average speed for extreme distance classification (km/h).
     * Prevents GPS drift/errors and high-speed trains from triggering flight classification.
     * Example: 1000 km traveled at 50 km/h over 20h is likely GPS error, not a flight.
     * Set to 350 km/h to distinguish from high-speed rail (200-350 km/h operational max).
     * This ensures that long-distance high-speed rail trips are classified as TRAIN, not FLIGHT.
     */
    private static final double EXTREME_FLIGHT_MIN_SPEED_KMH = 350.0;

    /**
     * Long distance threshold for flight detection (km).
     * Combined with high speed, indicates likely flight.
     */
    private static final double LONG_FLIGHT_DISTANCE_KM = 300.0;

    /**
     * High speed threshold for long distance flights (km/h).
     * Sustained speeds above this with long distances indicate air travel.
     * Set to 280 km/h to avoid classifying high-speed trains as flights (max operational 200-300 km/h).
     */
    private static final double LONG_FLIGHT_MIN_SPEED_KMH = 280.0;

    /**
     * Very long distance threshold for flight detection (km).
     * Even with modest speeds (accounting for ground time), this distance suggests flight.
     */
    private static final double VERY_LONG_FLIGHT_DISTANCE_KM = 600.0;

    /**
     * Modest speed threshold for very long distance flights (km/h).
     * Accounts for flights with significant ground time, taxi, delays.
     * Example: 600 km flight with 2h ground time at 90 km/h average.
     * Set to 150 km/h to avoid classifying high-speed trains (200+ km/h) as flights.
     */
    private static final double VERY_LONG_FLIGHT_MIN_SPEED_KMH = 150.0;

    /**
     * Short-haul flight minimum distance (km).
     * Catches domestic/regional flights with moderate ground time.
     */
    private static final double SHORT_FLIGHT_MIN_DISTANCE_KM = 350.0;

    /**
     * Short-haul flight maximum distance (km).
     * Upper bound to avoid overlap with very long distance heuristics.
     */
    private static final double SHORT_FLIGHT_MAX_DISTANCE_KM = 600.0;

    /**
     * Short-haul flight minimum average speed (km/h).
     * Higher threshold to exclude car/bus trips in this distance range.
     */
    private static final double SHORT_FLIGHT_MIN_SPEED_KMH = 110.0;

    /**
     * Train minimum distance threshold (km).
     * Filters out very short trips that are unlikely to be train journeys.
     */
    private static final double TRAIN_MIN_DISTANCE_KM = 100.0;

    /**
     * Train maximum distance threshold (km).
     * Extended to support long-distance high-speed rail routes.
     * Examples: Beijing-Shanghai (1318 km), Paris-Marseille (750 km), Tokyo-Hakata (1069 km).
     */
    private static final double TRAIN_MAX_DISTANCE_KM = 1500.0;

    /**
     * Train minimum average speed (km/h).
     * Lower bound for regional/commuter trains.
     */
    private static final double TRAIN_MIN_SPEED_KMH = 50.0;

    /**
     * Train maximum average speed (km/h).
     * Extended to support modern high-speed rail (200-350 km/h operational).
     * Examples: China HSR (300-350 km/h), Shinkansen (260-320 km/h), TGV (300-320 km/h).
     */
    private static final double TRAIN_MAX_SPEED_KMH = 200.0;

    /**
     * Train minimum duration threshold (hours).
     * Filters out very short trips that are better classified by speed alone.
     */
    private static final double TRAIN_MIN_DURATION_HOURS = 1.0;

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
     *
     * This method is typically used for:
     * 1. Inferred trips from data gaps (2 GPS points only)
     * 2. Legacy trips without pre-calculated GPS statistics
     *
     * For such sparse data, we use distance + duration heuristics
     * in addition to calculated speed, as speed alone can be misleading
     * (e.g., international flights with taxi/ground time show ~150-200 km/h avg).
     */
    private TripType classifyWithoutGpsStatistics(TimelineConfig config,
                                                  Duration tripDuration,
                                                  long distanceMeters) {
        double distanceKm = distanceMeters / 1000.0;
        double hours = tripDuration.getSeconds() / 3600.0;
        double avgSpeedKmh = hours > 0 ? distanceKm / hours : 0.0;

        if (hours <= 0 || distanceKm <= 0) {
            return UNKNOWN;
        }

        // ========================================================================
        // DISTANCE-BASED HEURISTICS FOR SPARSE GPS DATA
        // ========================================================================
        // When GPS statistics are unavailable (e.g., inferred trips from data gaps),
        // calculated speed can be misleading due to ground time, taxi, waiting, etc.
        // Use distance + speed combination to detect realistic travel modes.
        // ========================================================================

        // FLIGHT detection for sparse data:
        // - Very long distance (>300km) + reasonable speed (>100 km/h)
        //   → Almost certainly a flight (no ground transport sustains this)
        // - Medium-long distance (>500km) + modest speed (>80 km/h)
        //   → Likely flight with significant ground time
        // - Extremely long distance (>1000km)
        //   → Definitely a flight (no other realistic option)
        if (Boolean.TRUE.equals(config.getFlightEnabled())) {
            // Extreme distance: almost certainly a flight (with minimum speed requirement)
            if (distanceKm > EXTREME_FLIGHT_DISTANCE_KM && avgSpeedKmh > EXTREME_FLIGHT_MIN_SPEED_KMH) {
                log.debug("Distance {}km > {}km AND speed {}km/h > {}km/h → FLIGHT (high confidence: extreme distance)",
                         String.format("%.0f", distanceKm),
                         String.format("%.0f", EXTREME_FLIGHT_DISTANCE_KM),
                         String.format("%.1f", avgSpeedKmh),
                         String.format("%.1f", EXTREME_FLIGHT_MIN_SPEED_KMH));
                return FLIGHT;
            }

            // Long distance with high calculated speed: clear flight signature
            if (distanceKm > LONG_FLIGHT_DISTANCE_KM && avgSpeedKmh > LONG_FLIGHT_MIN_SPEED_KMH) {
                log.debug("Distance {}km > {}km AND speed {}km/h > {}km/h → FLIGHT (medium-high confidence: long distance + high speed)",
                         String.format("%.0f", distanceKm),
                         String.format("%.0f", LONG_FLIGHT_DISTANCE_KM),
                         String.format("%.1f", avgSpeedKmh),
                         String.format("%.1f", LONG_FLIGHT_MIN_SPEED_KMH));
                return FLIGHT;
            }

            // Short-to-medium flights with moderate delays
            // Catches: 400km flight with 3h total time → 133 km/h average
            // Higher speed threshold excludes most car/bus trips in this distance range
            if (distanceKm > SHORT_FLIGHT_MIN_DISTANCE_KM &&
                distanceKm <= SHORT_FLIGHT_MAX_DISTANCE_KM &&
                avgSpeedKmh > SHORT_FLIGHT_MIN_SPEED_KMH) {
                log.debug("Distance {}km ({}-{}km range) AND speed {}km/h > {}km/h → FLIGHT (medium confidence: short-haul)",
                         String.format("%.0f", distanceKm),
                         String.format("%.0f", SHORT_FLIGHT_MIN_DISTANCE_KM),
                         String.format("%.0f", SHORT_FLIGHT_MAX_DISTANCE_KM),
                         String.format("%.1f", avgSpeedKmh),
                         String.format("%.1f", SHORT_FLIGHT_MIN_SPEED_KMH));
                return FLIGHT;
            }

            // Very long distance with modest speed: likely flight with taxi/ground time
            if (distanceKm > VERY_LONG_FLIGHT_DISTANCE_KM && avgSpeedKmh > VERY_LONG_FLIGHT_MIN_SPEED_KMH) {
                log.debug("Distance {}km > {}km AND speed {}km/h > {}km/h → FLIGHT (medium confidence: very long distance)",
                         String.format("%.0f", distanceKm),
                         String.format("%.0f", VERY_LONG_FLIGHT_DISTANCE_KM),
                         String.format("%.1f", avgSpeedKmh),
                         String.format("%.1f", VERY_LONG_FLIGHT_MIN_SPEED_KMH));
                return FLIGHT;
            }
        }

        // TRAIN detection for sparse data:
        // Medium-long distance (100-1200km) with train-like speeds (50-200 km/h)
        // Extended ranges support modern high-speed rail (China HSR, Shinkansen, TGV)
        // Reasonable duration requirement filters out very short trips
        if (Boolean.TRUE.equals(config.getTrainEnabled())) {
            if (distanceKm > TRAIN_MIN_DISTANCE_KM && distanceKm < TRAIN_MAX_DISTANCE_KM &&
                avgSpeedKmh >= TRAIN_MIN_SPEED_KMH && avgSpeedKmh <= TRAIN_MAX_SPEED_KMH &&
                hours >= TRAIN_MIN_DURATION_HOURS) {

                // Add confidence logging based on speed range
                if (avgSpeedKmh > 150) {
                    log.debug("Distance {}km, speed {}km/h → TRAIN (high-speed rail)",
                             String.format("%.0f", distanceKm), String.format("%.1f", avgSpeedKmh));
                } else {
                    log.debug("Distance {}km, speed {}km/h → TRAIN (regional/intercity)",
                             String.format("%.0f", distanceKm), String.format("%.1f", avgSpeedKmh));
                }
                return TRAIN;
            }
        }

        // For other modes, use standard speed-based classification
        // When no max speed available, use avg speed for both
        return classifyBySpeed(avgSpeedKmh, avgSpeedKmh, null, config);
    }

    /**
     * Main classification algorithm supporting optional trip types.
     * <p>
     * CRITICAL PRIORITY ORDER:
     * 1. FLIGHT - highest priority (unambiguous speeds)
     * 2. TRAIN - before CAR (uses variance discriminator)
     * 3. BICYCLE - checked before RUNNING (higher speeds)
     * 4. RUNNING - BEFORE CAR (overlapping speeds)
     * 5. CAR - mandatory type
     * 6. WALK - mandatory type
     * 7. UNKNOWN - fallback
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
        //    CRITICAL: MUST be checked BEFORE RUNNING due to higher speeds (8-25 km/h)
        if (Boolean.TRUE.equals(config.getBicycleEnabled()) &&
                avgSpeedKmh >= config.getBicycleMinAvgSpeed() &&
                avgSpeedKmh <= config.getBicycleMaxAvgSpeed() &&
                maxSpeedKmh <= config.getBicycleMaxMaxSpeed()) {
            return BICYCLE;
        }

        // 4. RUNNING - medium-low speeds
        //    CRITICAL: MUST be checked BEFORE CAR due to overlapping speeds (7-14 km/h)
        //    Conservative thresholds to distinguish from fast walking and slow cycling
        if (Boolean.TRUE.equals(config.getRunningEnabled()) &&
                avgSpeedKmh >= config.getRunningMinAvgSpeed() &&
                avgSpeedKmh <= config.getRunningMaxAvgSpeed() &&
                maxSpeedKmh <= config.getRunningMaxMaxSpeed()) {
            return RUNNING;
        }

        // 5. CAR - motorized transport (mandatory)
        //    CRITICAL: Checked AFTER BICYCLE and RUNNING to avoid capturing human-powered trips
        //    Uses OR logic: avg >= 10 OR max >= 15
        if (avgSpeedKmh >= config.getCarMinAvgSpeed() ||
                maxSpeedKmh >= config.getCarMinMaxSpeed()) {
            return CAR;
        }

        // 6. WALK - low speeds (mandatory)
        //    Both avg AND max must be within walking range
        if (avgSpeedKmh <= config.getWalkingMaxAvgSpeed() &&
                maxSpeedKmh <= config.getWalkingMaxMaxSpeed()) {
            return WALK;
        }

        // 7. UNKNOWN - fallback for edge cases
        //    Example: 7 km/h avg (above walk, below running if disabled, below car min)
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
     * <p>
     * IMPORTANT: This is a BIDIRECTIONAL check that validates GPS in both directions:
     * - GPS too HIGH: catches GPS noise/errors that inflate speed
     * - GPS too LOW: catches GPS averaging stops, poor accuracy that deflates speed
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
            // Low speed trips: Use strict bidirectional ratio-based validation

            // Check 1: GPS too HIGH (original check)
            // GPS should not exceed calculated by more than 2x
            if (avgSpeedKmh > calculatedAvgSpeedKmh * LOW_SPEED_GPS_RELIABILITY_RATIO) {
                log.debug("GPS unreliable (too high): GPS {} km/h > calculated {} km/h * {}",
                        String.format("%.2f", avgSpeedKmh),
                        String.format("%.2f", calculatedAvgSpeedKmh),
                        LOW_SPEED_GPS_RELIABILITY_RATIO);
                return true; // GPS unreliable - too high
            }

            // Check 2: GPS too LOW (new check for underreporting)
            // GPS should not be less than calculated / 1.3 (i.e., calculated should not exceed GPS by more than ~23%)
            // Example: Calculated 7.5 km/h, GPS must be >= 7.5/1.3 = 5.77 km/h
            // If GPS is 5.5 km/h, it's too low (underreporting by ~27%)
            if (avgSpeedKmh < calculatedAvgSpeedKmh / LOW_SPEED_GPS_MIN_RELIABILITY_RATIO) {
                log.debug("GPS unreliable (too low): GPS {} km/h < calculated {} km/h / {} (= {} km/h)",
                        String.format("%.2f", avgSpeedKmh),
                        String.format("%.2f", calculatedAvgSpeedKmh),
                        LOW_SPEED_GPS_MIN_RELIABILITY_RATIO,
                        String.format("%.2f", calculatedAvgSpeedKmh / LOW_SPEED_GPS_MIN_RELIABILITY_RATIO));
                return true; // GPS unreliable - too low
            }

            return false; // GPS is reliable (within acceptable range)

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

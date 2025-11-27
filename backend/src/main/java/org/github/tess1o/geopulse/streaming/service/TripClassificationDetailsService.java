package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.model.dto.TripClassificationDetailsDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TripClassificationDetailsDTO.*;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for generating detailed trip classification explanations.
 * Helps users understand why their trip was classified as a specific transport type.
 */
@ApplicationScoped
@Slf4j
public class TripClassificationDetailsService {

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    TimelineConfigurationProvider configProvider;

    // ============================================
    // GPS VALIDATION CONSTANTS
    // These MUST match TravelClassification constants to ensure consistency
    // ============================================

    /**
     * Minimum GPS speed to indicate actual movement (km/h).
     * Used to distinguish between stopped/very slow movement vs GPS noise.
     */
    private static final double MIN_GPS_MOVEMENT_SPEED_KMH = 3.0;

    /**
     * Speed threshold for unrealistically low calculated speeds (km/h).
     * If calculated speed is below this but GPS shows movement, calculated distance is likely wrong.
     */
    private static final double MIN_REALISTIC_CALCULATED_SPEED_KMH = 1.0;

    /**
     * Maximum allowed ratio of GPS max speed to calculated avg speed.
     * If GPS max > calculated avg * this ratio, GPS max is considered unreliable noise.
     */
    private static final double GPS_MAX_SPEED_NOISE_RATIO = 5.0;

    /**
     * Multiplier for estimating reasonable max speed from avg speed.
     * Used when GPS max speed is deemed unreliable.
     */
    private static final double ESTIMATED_MAX_SPEED_MULTIPLIER = 1.5;

    /**
     * Coefficient for walk speed verification (multiplier).
     * If calculated speed exceeds walkingMaxMaxSpeed * this coefficient, reclassify as CAR.
     */
    private static final double WALK_VERIFICATION_COEFFICIENT = 1.2;

    /**
     * Get comprehensive classification details for a trip.
     *
     * @param tripId ID of the trip
     * @param userId ID of the requesting user (for ownership validation)
     * @return classification details if trip exists and user owns it
     */
    public Optional<TripClassificationDetailsDTO> getTripClassificationDetails(Long tripId, UUID userId) {
        // Fetch trip by ID
        Optional<TimelineTripEntity> tripOpt = tripRepository.findByIdOptional(tripId);
        if (tripOpt.isEmpty()) {
            log.warn("Trip with ID {} not found", tripId);
            return Optional.empty();
        }

        TimelineTripEntity trip = tripOpt.get();

        // Validate ownership
        if (!trip.getUser().getId().equals(userId)) {
            log.warn("User {} attempted to access trip {} owned by {}",
                    userId, tripId, trip.getUser().getId());
            return Optional.empty();
        }

        // Get user's current configuration
        TimelineConfig config = configProvider.getConfigurationForUser(userId);

        // Build statistics
        TripStatistics statistics = buildTripStatistics(trip);

        // Build thresholds from config
        ClassificationThresholds thresholds = buildThresholds(config);

        // Generate classification steps explanation
        List<ClassificationStep> steps = explainClassification(trip, config, statistics);

        // Generate final reason
        String finalReason = generateFinalReason(trip.getMovementType(), statistics, config);

        TripClassificationDetailsDTO dto = new TripClassificationDetailsDTO(
                trip.getId(),
                trip.getTimestamp(),
                trip.getTripDuration(),
                trip.getDistanceMeters(),
                trip.getMovementType(),
                statistics,
                thresholds,
                steps,
                finalReason
        );

        return Optional.of(dto);
    }

    /**
     * Build trip statistics from entity, converting speeds to km/h.
     */
    private TripStatistics buildTripStatistics(TimelineTripEntity trip) {
        Double avgGpsSpeedKmh = trip.getAvgGpsSpeed() != null ? trip.getAvgGpsSpeed() * 3.6 : null;
        Double maxGpsSpeedKmh = trip.getMaxGpsSpeed() != null ? trip.getMaxGpsSpeed() * 3.6 : null;
        Double speedVarianceKmh = trip.getSpeedVariance();

        // Calculate average speed from distance and duration
        double distanceKm = trip.getDistanceMeters() / 1000.0;
        double hours = trip.getTripDuration() / 3600.0;
        Double calculatedAvgSpeedKmh = hours > 0 ? distanceKm / hours : 0.0;

        // Determine GPS reliability (simplified logic from TravelClassification)
        boolean gpsReliable = isGpsReliable(avgGpsSpeedKmh, calculatedAvgSpeedKmh);

        return new TripStatistics(
                avgGpsSpeedKmh,
                maxGpsSpeedKmh,
                calculatedAvgSpeedKmh,
                speedVarianceKmh,
                trip.getLowAccuracyPointsCount(),
                gpsReliable
        );
    }

    /**
     * GPS reliability check - matches TravelClassification.isGpsUnreliable() logic.
     * Returns TRUE if GPS is reliable, FALSE if unreliable.
     * <p>
     * IMPORTANT: This must stay in sync with TravelClassification.isGpsUnreliable()
     * to ensure the classification dialog explanation matches actual classification.
     */
    private boolean isGpsReliable(Double avgGpsSpeedKmh, Double calculatedAvgSpeedKmh) {
        if (avgGpsSpeedKmh == null || calculatedAvgSpeedKmh == null || calculatedAvgSpeedKmh <= 0) {
            return avgGpsSpeedKmh != null;
        }

        // Low speed trips: BIDIRECTIONAL validation
        if (calculatedAvgSpeedKmh < 20.0) {
            // Check 1: GPS too HIGH (exceeds calculated by more than 2x)
            if (avgGpsSpeedKmh > calculatedAvgSpeedKmh * 2.0) {
                return false; // GPS unreliable - too high
            }

            // Check 2: GPS too LOW (less than calculated / 1.3)
            // This matches TravelClassification.LOW_SPEED_GPS_MIN_RELIABILITY_RATIO
            if (avgGpsSpeedKmh < calculatedAvgSpeedKmh / 1.3) {
                return false; // GPS unreliable - too low
            }

            return true; // GPS reliable (within acceptable range)
        }

        // High speed trips: trust GPS
        if (avgGpsSpeedKmh > 200.0) {
            return true;
        }

        // Medium speed: GPS should not differ by more than 50%
        double difference = Math.abs(avgGpsSpeedKmh - calculatedAvgSpeedKmh);
        double percentDiff = difference / calculatedAvgSpeedKmh;
        return percentDiff <= 0.5;
    }

    /**
     * Build configuration thresholds from user config.
     */
    private ClassificationThresholds buildThresholds(TimelineConfig config) {
        return new ClassificationThresholds(
                // WALK
                config.getWalkingMaxAvgSpeed(),
                config.getWalkingMaxMaxSpeed(),

                // CAR
                config.getCarMinAvgSpeed(),
                config.getCarMinMaxSpeed(),

                // BICYCLE
                config.getBicycleEnabled(),
                config.getBicycleEnabled() ? config.getBicycleMinAvgSpeed() : null,
                config.getBicycleEnabled() ? config.getBicycleMaxAvgSpeed() : null,
                config.getBicycleEnabled() ? config.getBicycleMaxMaxSpeed() : null,

                // RUNNING
                config.getRunningEnabled(),
                config.getRunningEnabled() ? config.getRunningMinAvgSpeed() : null,
                config.getRunningEnabled() ? config.getRunningMaxAvgSpeed() : null,
                config.getRunningEnabled() ? config.getRunningMaxMaxSpeed() : null,

                // TRAIN
                config.getTrainEnabled(),
                config.getTrainEnabled() ? config.getTrainMinAvgSpeed() : null,
                config.getTrainEnabled() ? config.getTrainMaxAvgSpeed() : null,
                config.getTrainEnabled() ? config.getTrainMinMaxSpeed() : null,
                config.getTrainEnabled() ? config.getTrainMaxMaxSpeed() : null,
                config.getTrainEnabled() ? config.getTrainMaxSpeedVariance() : null,

                // FLIGHT
                config.getFlightEnabled(),
                config.getFlightEnabled() ? config.getFlightMinAvgSpeed() : null,
                config.getFlightEnabled() ? config.getFlightMinMaxSpeed() : null
        );
    }

    /**
     * Generate step-by-step classification explanation.
     * Follows the same priority order as TravelClassification.classifyBySpeed().
     * IMPORTANT: Implements "early stopping" - once a type matches, remaining types are marked as "not checked"
     * because the algorithm stops at the first match.
     *
     * CRITICAL: This method MUST replicate the exact logic from TravelClassification.validateGpsReliability()
     * to ensure the explanation matches the actual classification.
     */
    private List<ClassificationStep> explainClassification(
            TimelineTripEntity trip,
            TimelineConfig config,
            TripStatistics statistics) {

        List<ClassificationStep> steps = new ArrayList<>();

        // Determine which speeds to use - MUST match TravelClassification.validateGpsReliability() exactly
        double avgSpeedKmh;
        double maxSpeedKmh;
        String speedSource;

        boolean gpsAvgReliable = statistics.gpsReliable() && statistics.avgGpsSpeedKmh() != null;

        if (gpsAvgReliable) {
            // GPS avg is reliable
            avgSpeedKmh = statistics.avgGpsSpeedKmh();

            // Check for max speed noise spikes (matches TravelClassification:463-471)
            if (statistics.maxGpsSpeedKmh() != null &&
                statistics.maxGpsSpeedKmh() > avgSpeedKmh * GPS_MAX_SPEED_NOISE_RATIO) {
                // Max speed spike is unrealistic compared to avg
                maxSpeedKmh = avgSpeedKmh * ESTIMATED_MAX_SPEED_MULTIPLIER;
                speedSource = String.format("Using GPS average speed (%.1f km/h). GPS max (%.1f km/h) was a noise spike, using estimated max (%.1f km/h)",
                        avgSpeedKmh, statistics.maxGpsSpeedKmh(), maxSpeedKmh);
            } else {
                maxSpeedKmh = statistics.maxGpsSpeedKmh() != null ? statistics.maxGpsSpeedKmh() : avgSpeedKmh * ESTIMATED_MAX_SPEED_MULTIPLIER;
                speedSource = String.format("Using GPS average speed (%.1f km/h) for classification", avgSpeedKmh);
            }
        } else {
            // GPS avg is unreliable - check for special case (matches TravelClassification:477-487)
            if (statistics.calculatedAvgSpeedKmh() < MIN_REALISTIC_CALCULATED_SPEED_KMH &&
                statistics.avgGpsSpeedKmh() != null &&
                statistics.avgGpsSpeedKmh() > MIN_GPS_MOVEMENT_SPEED_KMH) {
                // Calculated speed is unrealistically low but GPS shows movement - trust GPS
                avgSpeedKmh = statistics.avgGpsSpeedKmh();
                maxSpeedKmh = statistics.maxGpsSpeedKmh() != null ? statistics.maxGpsSpeedKmh() : avgSpeedKmh * ESTIMATED_MAX_SPEED_MULTIPLIER;
                speedSource = String.format("Calculated speed (%.1f km/h) is unrealistically low but GPS shows movement (%.1f km/h). Trusting GPS.",
                        statistics.calculatedAvgSpeedKmh(), avgSpeedKmh);
            } else {
                // Use calculated avg, but decide about max speed (matches TravelClassification:490-491)
                avgSpeedKmh = statistics.calculatedAvgSpeedKmh();
                maxSpeedKmh = determineMaxSpeed(statistics);
                speedSource = String.format("Using calculated average speed (%.1f km/h) because GPS (%.1f km/h) was unreliable",
                        avgSpeedKmh, statistics.avgGpsSpeedKmh());
            }
        }

        Double speedVariance = statistics.speedVarianceKmh();

        // Track if we found a match (for early stopping)
        boolean foundMatch = false;
        String matchedType = null;

        // Priority 1: FLIGHT
        ClassificationStep flightStep = checkFlight(avgSpeedKmh, maxSpeedKmh, config, speedSource);
        steps.add(flightStep);
        if (flightStep.checked() && flightStep.passed()) {
            foundMatch = true;
            matchedType = "FLIGHT";
        }

        // Priority 2: TRAIN
        if (!foundMatch) {
            ClassificationStep trainStep = checkTrain(avgSpeedKmh, maxSpeedKmh, speedVariance, config, speedSource);
            steps.add(trainStep);
            if (trainStep.checked() && trainStep.passed()) {
                foundMatch = true;
                matchedType = "TRAIN";
            }
        } else {
            steps.add(createSkippedStep("TRAIN", matchedType));
        }

        // Priority 3: BICYCLE
        if (!foundMatch) {
            ClassificationStep bicycleStep = checkBicycle(avgSpeedKmh, maxSpeedKmh, config, speedSource);
            steps.add(bicycleStep);
            if (bicycleStep.checked() && bicycleStep.passed()) {
                foundMatch = true;
                matchedType = "BICYCLE";
            }
        } else {
            steps.add(createSkippedStep("BICYCLE", matchedType));
        }

        // Priority 4: RUNNING
        if (!foundMatch) {
            ClassificationStep runningStep = checkRunning(avgSpeedKmh, maxSpeedKmh, config, speedSource);
            steps.add(runningStep);
            if (runningStep.checked() && runningStep.passed()) {
                foundMatch = true;
                matchedType = "RUNNING";
            }
        } else {
            steps.add(createSkippedStep("RUNNING", matchedType));
        }

        // Priority 5: CAR
        if (!foundMatch) {
            ClassificationStep carStep = checkCar(avgSpeedKmh, maxSpeedKmh, config, speedSource);
            steps.add(carStep);
            if (carStep.checked() && carStep.passed()) {
                foundMatch = true;
                matchedType = "CAR";
            }
        } else {
            steps.add(createSkippedStep("CAR", matchedType));
        }

        // Priority 6: WALK
        if (!foundMatch) {
            ClassificationStep walkStep = checkWalk(avgSpeedKmh, maxSpeedKmh, config, speedSource);
            steps.add(walkStep);
            if (walkStep.checked() && walkStep.passed()) {
                foundMatch = true;
                matchedType = "WALK";
            }
        } else {
            steps.add(createSkippedStep("WALK", matchedType));
        }

        // Priority 7: UNKNOWN (fallback) - only if nothing else matched
        if (!foundMatch) {
            steps.add(checkUnknown());
            matchedType = "UNKNOWN";
        } else {
            steps.add(createSkippedStep("UNKNOWN", matchedType));
        }

        // CRITICAL: Apply post-classification verification (matches TravelClassification.verifyAndCorrectClassification)
        // This can override the initial classification result
        String finalMatchedType = verifyAndCorrectClassification(
                matchedType,
                trip.getDistanceMeters(),
                trip.getTripDuration(),
                config
        );

        // If verification changed the classification, add an explanation step
        if (!finalMatchedType.equals(matchedType)) {
            ClassificationStep verificationStep = new ClassificationStep(
                    finalMatchedType,
                    true,
                    true,
                    String.format("Post-classification verification: Initial classification '%s' was corrected to '%s' " +
                                    "because calculated speed (%.1f km/h) exceeds realistic walking speed (%.1f km/h threshold)",
                            matchedType,
                            finalMatchedType,
                            statistics.calculatedAvgSpeedKmh(),
                            config.getWalkingMaxMaxSpeed() * WALK_VERIFICATION_COEFFICIENT),
                    List.of()
            );
            steps.add(verificationStep);
        }

        return steps;
    }

    /**
     * Creates a "skipped" step for transport types that weren't checked
     * because a higher priority type already matched.
     */
    private ClassificationStep createSkippedStep(String tripType, String matchedType) {
        String reason = String.format("Not checked - higher priority type '%s' already matched", matchedType);
        return new ClassificationStep(tripType, false, false, reason, List.of());
    }

    /**
     * Determines max speed to use when GPS avg is unreliable.
     * Replicates TravelClassification.adjustMaxSpeed() logic.
     *
     * @param statistics trip statistics
     * @return adjusted max speed
     */
    private double determineMaxSpeed(TripStatistics statistics) {
        double maxGpsSpeedKmh = statistics.maxGpsSpeedKmh() != null ? statistics.maxGpsSpeedKmh() : 0.0;
        double calculatedAvgSpeedKmh = statistics.calculatedAvgSpeedKmh();

        // Matches TravelClassification.adjustMaxSpeed():567
        if (maxGpsSpeedKmh > calculatedAvgSpeedKmh * GPS_MAX_SPEED_NOISE_RATIO) {
            // GPS max is too high - likely noise spike
            // Use reasonable estimate instead
            double estimatedMax = calculatedAvgSpeedKmh * ESTIMATED_MAX_SPEED_MULTIPLIER;
            log.debug("GPS max speed ({} km/h) is unreliable ({}x calculated avg {} km/h). Using estimated max: {} km/h",
                    String.format("%.2f", maxGpsSpeedKmh),
                    String.format("%.2f", maxGpsSpeedKmh / calculatedAvgSpeedKmh),
                    String.format("%.2f", calculatedAvgSpeedKmh),
                    String.format("%.2f", estimatedMax));
            return estimatedMax;
        } else {
            // GPS max seems reasonable - keep it
            // This handles: car trip with traffic stops (10 km/h avg, 49 km/h max)
            log.debug("GPS max speed ({} km/h) seems reasonable compared to calculated avg ({} km/h). Keeping GPS max.",
                    String.format("%.2f", maxGpsSpeedKmh),
                    String.format("%.2f", calculatedAvgSpeedKmh));
            return maxGpsSpeedKmh;
        }
    }

    /**
     * Verifies and corrects the trip classification based on realistic speed constraints.
     * Replicates TravelClassification.verifyAndCorrectClassification() logic.
     *
     * If a trip is classified as WALK but the speed is unrealistically high, it's corrected to CAR.
     *
     * @param tripType       the initial trip classification
     * @param distanceMeters the total distance of the trip in meters
     * @param tripDuration   the duration of the trip in seconds
     * @param config         the timeline configuration
     * @return the corrected trip type
     */
    private String verifyAndCorrectClassification(String tripType,
                                                  long distanceMeters,
                                                  long tripDuration,
                                                  TimelineConfig config) {
        if ("WALK".equals(tripType)) {
            final double distanceKm = distanceMeters / 1000.0;
            final double hours = tripDuration / 3600.0;
            final double avgSpeedKmh = hours > 0 ? distanceKm / hours : 0.0;

            // If calculated speed exceeds walking threshold with tolerance, re-classify as CAR
            // Uses WALK_VERIFICATION_COEFFICIENT (1.2x) to allow for GPS inaccuracies in short trips
            if (avgSpeedKmh > config.getWalkingMaxMaxSpeed() * WALK_VERIFICATION_COEFFICIENT) {
                log.debug("Correcting WALK to CAR due to unrealistic speed: {} km/h (exceeds {} km/h threshold)",
                        avgSpeedKmh,
                        config.getWalkingMaxMaxSpeed() * WALK_VERIFICATION_COEFFICIENT);
                return "CAR";
            }
        }
        return tripType;
    }

    private ClassificationStep checkFlight(double avgSpeedKmh, double maxSpeedKmh, TimelineConfig config, String speedSource) {
        if (!Boolean.TRUE.equals(config.getFlightEnabled())) {
            return new ClassificationStep("FLIGHT", false, false,
                    "Flight detection is not enabled in configuration", List.of());
        }

        List<ThresholdCheck> checks = new ArrayList<>();
        boolean avgCheck = avgSpeedKmh >= config.getFlightMinAvgSpeed();
        boolean maxCheck = maxSpeedKmh >= config.getFlightMinMaxSpeed();

        checks.add(new ThresholdCheck("Average Speed", ">=",
                config.getFlightMinAvgSpeed(), avgSpeedKmh, avgCheck));
        checks.add(new ThresholdCheck("Max Speed", ">=",
                config.getFlightMinMaxSpeed(), maxSpeedKmh, maxCheck));

        boolean passed = avgCheck || maxCheck; // OR logic
        String reason = passed
                ? String.format("Flight detected: average speed (%.1f km/h) OR max speed (%.1f km/h) exceeds flight thresholds. %s",
                avgSpeedKmh, maxSpeedKmh, speedSource)
                : String.format("Not a flight: average speed (%.1f km/h) < %.1f km/h AND max speed (%.1f km/h) < %.1f km/h. %s",
                avgSpeedKmh, config.getFlightMinAvgSpeed(), maxSpeedKmh, config.getFlightMinMaxSpeed(), speedSource);

        return new ClassificationStep("FLIGHT", true, passed, reason, checks);
    }

    private ClassificationStep checkTrain(double avgSpeedKmh, double maxSpeedKmh, Double speedVariance, TimelineConfig config, String speedSource) {
        if (!Boolean.TRUE.equals(config.getTrainEnabled())) {
            return new ClassificationStep("TRAIN", false, false,
                    "Train detection is not enabled in configuration", List.of());
        }

        List<ThresholdCheck> checks = new ArrayList<>();
        boolean avgMinCheck = avgSpeedKmh >= config.getTrainMinAvgSpeed();
        boolean avgMaxCheck = avgSpeedKmh <= config.getTrainMaxAvgSpeed();
        boolean maxMinCheck = maxSpeedKmh >= config.getTrainMinMaxSpeed();
        boolean maxMaxCheck = maxSpeedKmh <= config.getTrainMaxMaxSpeed();
        boolean varianceCheck = speedVariance != null && speedVariance < config.getTrainMaxSpeedVariance();

        checks.add(new ThresholdCheck("Average Speed (min)", ">=",
                config.getTrainMinAvgSpeed(), avgSpeedKmh, avgMinCheck));
        checks.add(new ThresholdCheck("Average Speed (max)", "<=",
                config.getTrainMaxAvgSpeed(), avgSpeedKmh, avgMaxCheck));
        checks.add(new ThresholdCheck("Max Speed (min)", ">=",
                config.getTrainMinMaxSpeed(), maxSpeedKmh, maxMinCheck));
        checks.add(new ThresholdCheck("Max Speed (max)", "<=",
                config.getTrainMaxMaxSpeed(), maxSpeedKmh, maxMaxCheck));
        checks.add(new ThresholdCheck("Speed Variance", "<",
                config.getTrainMaxSpeedVariance(), speedVariance, varianceCheck));

        boolean passed = avgMinCheck && avgMaxCheck && maxMinCheck && maxMaxCheck && varianceCheck;
        String reason = passed
                ? String.format("Train detected: speed in range (%.1f-%.1f km/h) with low variance (%.1f). %s",
                avgSpeedKmh, maxSpeedKmh, speedVariance, speedSource)
                : String.format("Not a train: speeds or variance outside train profile. %s", speedSource);

        return new ClassificationStep("TRAIN", true, passed, reason, checks);
    }

    private ClassificationStep checkBicycle(double avgSpeedKmh, double maxSpeedKmh, TimelineConfig config, String speedSource) {
        if (!Boolean.TRUE.equals(config.getBicycleEnabled())) {
            return new ClassificationStep("BICYCLE", false, false,
                    "Bicycle detection is not enabled in configuration", List.of());
        }

        List<ThresholdCheck> checks = new ArrayList<>();
        boolean avgMinCheck = avgSpeedKmh >= config.getBicycleMinAvgSpeed();
        boolean avgMaxCheck = avgSpeedKmh <= config.getBicycleMaxAvgSpeed();
        boolean maxCheck = maxSpeedKmh <= config.getBicycleMaxMaxSpeed();

        checks.add(new ThresholdCheck("Average Speed (min)", ">=",
                config.getBicycleMinAvgSpeed(), avgSpeedKmh, avgMinCheck));
        checks.add(new ThresholdCheck("Average Speed (max)", "<=",
                config.getBicycleMaxAvgSpeed(), avgSpeedKmh, avgMaxCheck));
        checks.add(new ThresholdCheck("Max Speed", "<=",
                config.getBicycleMaxMaxSpeed(), maxSpeedKmh, maxCheck));

        boolean passed = avgMinCheck && avgMaxCheck && maxCheck;
        String reason = passed
                ? String.format("Bicycle detected: average speed %.1f km/h within bicycle range (%.1f-%.1f km/h). %s",
                avgSpeedKmh, config.getBicycleMinAvgSpeed(), config.getBicycleMaxAvgSpeed(), speedSource)
                : String.format("Not a bicycle: speeds outside bicycle range. %s", speedSource);

        return new ClassificationStep("BICYCLE", true, passed, reason, checks);
    }

    private ClassificationStep checkRunning(double avgSpeedKmh, double maxSpeedKmh, TimelineConfig config, String speedSource) {
        if (!Boolean.TRUE.equals(config.getRunningEnabled())) {
            return new ClassificationStep("RUNNING", false, false,
                    "Running detection is not enabled in configuration", List.of());
        }

        List<ThresholdCheck> checks = new ArrayList<>();
        boolean avgMinCheck = avgSpeedKmh >= config.getRunningMinAvgSpeed();
        boolean avgMaxCheck = avgSpeedKmh <= config.getRunningMaxAvgSpeed();
        boolean maxCheck = maxSpeedKmh <= config.getRunningMaxMaxSpeed();

        checks.add(new ThresholdCheck("Average Speed (min)", ">=",
                config.getRunningMinAvgSpeed(), avgSpeedKmh, avgMinCheck));
        checks.add(new ThresholdCheck("Average Speed (max)", "<=",
                config.getRunningMaxAvgSpeed(), avgSpeedKmh, avgMaxCheck));
        checks.add(new ThresholdCheck("Max Speed", "<=",
                config.getRunningMaxMaxSpeed(), maxSpeedKmh, maxCheck));

        boolean passed = avgMinCheck && avgMaxCheck && maxCheck;
        String reason = passed
                ? String.format("Running detected: average speed %.1f km/h within running range (%.1f-%.1f km/h). %s",
                avgSpeedKmh, config.getRunningMinAvgSpeed(), config.getRunningMaxAvgSpeed(), speedSource)
                : String.format("Not running: speeds outside running range. %s", speedSource);

        return new ClassificationStep("RUNNING", true, passed, reason, checks);
    }

    private ClassificationStep checkCar(double avgSpeedKmh, double maxSpeedKmh, TimelineConfig config, String speedSource) {
        List<ThresholdCheck> checks = new ArrayList<>();
        boolean avgCheck = avgSpeedKmh >= config.getCarMinAvgSpeed();
        boolean maxCheck = maxSpeedKmh >= config.getCarMinMaxSpeed();

        checks.add(new ThresholdCheck("Average Speed", ">=",
                config.getCarMinAvgSpeed(), avgSpeedKmh, avgCheck));
        checks.add(new ThresholdCheck("Max Speed", ">=",
                config.getCarMinMaxSpeed(), maxSpeedKmh, maxCheck));

        boolean passed = avgCheck || maxCheck; // OR logic
        String reason = passed
                ? String.format("Car detected: average speed (%.1f km/h) OR max speed (%.1f km/h) exceeds car thresholds. %s",
                avgSpeedKmh, maxSpeedKmh, speedSource)
                : String.format("Not a car: average speed (%.1f km/h) < %.1f km/h AND max speed (%.1f km/h) < %.1f km/h. %s",
                avgSpeedKmh, config.getCarMinAvgSpeed(), maxSpeedKmh, config.getCarMinMaxSpeed(), speedSource);

        return new ClassificationStep("CAR", true, passed, reason, checks);
    }

    private ClassificationStep checkWalk(double avgSpeedKmh, double maxSpeedKmh, TimelineConfig config, String speedSource) {
        List<ThresholdCheck> checks = new ArrayList<>();
        boolean avgCheck = avgSpeedKmh <= config.getWalkingMaxAvgSpeed();
        boolean maxCheck = maxSpeedKmh <= config.getWalkingMaxMaxSpeed();

        checks.add(new ThresholdCheck("Average Speed", "<=",
                config.getWalkingMaxAvgSpeed(), avgSpeedKmh, avgCheck));
        checks.add(new ThresholdCheck("Max Speed", "<=",
                config.getWalkingMaxMaxSpeed(), maxSpeedKmh, maxCheck));

        boolean passed = avgCheck && maxCheck; // AND logic
        String reason = passed
                ? String.format("Walk detected: average speed %.1f km/h and max speed %.1f km/h both within walking limits. %s",
                avgSpeedKmh, maxSpeedKmh, speedSource)
                : String.format("Not a walk: speeds exceed walking limits. %s", speedSource);

        return new ClassificationStep("WALK", true, passed, reason, checks);
    }

    private ClassificationStep checkUnknown() {
        return new ClassificationStep("UNKNOWN", true, true,
                "Fallback classification when no other type matches", List.of());
    }

    /**
     * Generate a user-friendly final reason for the classification.
     */
    private String generateFinalReason(String classification, TripStatistics statistics, TimelineConfig config) {
        double avgSpeed = statistics.gpsReliable() && statistics.avgGpsSpeedKmh() != null
                ? statistics.avgGpsSpeedKmh()
                : statistics.calculatedAvgSpeedKmh();

        return switch (classification) {
            case "FLIGHT" -> String.format("This trip was classified as FLIGHT because the speed (%.1f km/h) is consistent with air travel.", avgSpeed);
            case "TRAIN" -> String.format("This trip was classified as TRAIN due to sustained high speeds (%.1f km/h) with consistent velocity.", avgSpeed);
            case "BICYCLE" -> String.format("This trip was classified as BICYCLE based on moderate speeds (%.1f km/h) typical of cycling.", avgSpeed);
            case "RUNNING" -> String.format("This trip was classified as RUNNING based on speeds (%.1f km/h) consistent with running pace.", avgSpeed);
            case "CAR" -> String.format("This trip was classified as CAR due to motorized speeds (average %.1f km/h, max %.1f km/h).", avgSpeed, statistics.maxGpsSpeedKmh());
            case "WALK" -> String.format("This trip was classified as WALK based on low speeds (%.1f km/h) typical of walking.", avgSpeed);
            default -> "This trip could not be definitively classified and was marked as UNKNOWN.";
        };
    }
}

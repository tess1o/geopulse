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
     * Simplified GPS reliability check.
     */
    private boolean isGpsReliable(Double avgGpsSpeedKmh, Double calculatedAvgSpeedKmh) {
        if (avgGpsSpeedKmh == null || calculatedAvgSpeedKmh == null || calculatedAvgSpeedKmh <= 0) {
            return avgGpsSpeedKmh != null;
        }

        // Low speed trips: GPS should not exceed calculated by more than 2x
        if (calculatedAvgSpeedKmh < 20.0) {
            return avgGpsSpeedKmh <= calculatedAvgSpeedKmh * 2.0;
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
     */
    private List<ClassificationStep> explainClassification(
            TimelineTripEntity trip,
            TimelineConfig config,
            TripStatistics statistics) {

        List<ClassificationStep> steps = new ArrayList<>();

        // Get speeds (prefer GPS if reliable, otherwise use calculated)
        double avgSpeedKmh = statistics.gpsReliable() && statistics.avgGpsSpeedKmh() != null
                ? statistics.avgGpsSpeedKmh()
                : statistics.calculatedAvgSpeedKmh();

        double maxSpeedKmh = statistics.gpsReliable() && statistics.maxGpsSpeedKmh() != null
                ? statistics.maxGpsSpeedKmh()
                : avgSpeedKmh * 1.5; // Estimated if GPS unreliable

        Double speedVariance = statistics.speedVarianceKmh();

        // Priority 1: FLIGHT
        steps.add(checkFlight(avgSpeedKmh, maxSpeedKmh, config));

        // Priority 2: TRAIN
        steps.add(checkTrain(avgSpeedKmh, maxSpeedKmh, speedVariance, config));

        // Priority 3: BICYCLE
        steps.add(checkBicycle(avgSpeedKmh, maxSpeedKmh, config));

        // Priority 4: RUNNING
        steps.add(checkRunning(avgSpeedKmh, maxSpeedKmh, config));

        // Priority 5: CAR
        steps.add(checkCar(avgSpeedKmh, maxSpeedKmh, config));

        // Priority 6: WALK
        steps.add(checkWalk(avgSpeedKmh, maxSpeedKmh, config));

        // Priority 7: UNKNOWN (fallback)
        steps.add(checkUnknown());

        return steps;
    }

    private ClassificationStep checkFlight(double avgSpeedKmh, double maxSpeedKmh, TimelineConfig config) {
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
                ? String.format("Flight detected: average speed (%.1f km/h) OR max speed (%.1f km/h) exceeds flight thresholds",
                avgSpeedKmh, maxSpeedKmh)
                : String.format("Not a flight: average speed (%.1f km/h) < %.1f km/h AND max speed (%.1f km/h) < %.1f km/h",
                avgSpeedKmh, config.getFlightMinAvgSpeed(), maxSpeedKmh, config.getFlightMinMaxSpeed());

        return new ClassificationStep("FLIGHT", true, passed, reason, checks);
    }

    private ClassificationStep checkTrain(double avgSpeedKmh, double maxSpeedKmh, Double speedVariance, TimelineConfig config) {
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
                ? String.format("Train detected: speed in range (%.1f-%.1f km/h) with low variance (%.1f)",
                avgSpeedKmh, maxSpeedKmh, speedVariance)
                : String.format("Not a train: speeds or variance outside train profile");

        return new ClassificationStep("TRAIN", true, passed, reason, checks);
    }

    private ClassificationStep checkBicycle(double avgSpeedKmh, double maxSpeedKmh, TimelineConfig config) {
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
                ? String.format("Bicycle detected: average speed %.1f km/h within bicycle range (%.1f-%.1f km/h)",
                avgSpeedKmh, config.getBicycleMinAvgSpeed(), config.getBicycleMaxAvgSpeed())
                : String.format("Not a bicycle: speeds outside bicycle range");

        return new ClassificationStep("BICYCLE", true, passed, reason, checks);
    }

    private ClassificationStep checkRunning(double avgSpeedKmh, double maxSpeedKmh, TimelineConfig config) {
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
                ? String.format("Running detected: average speed %.1f km/h within running range (%.1f-%.1f km/h)",
                avgSpeedKmh, config.getRunningMinAvgSpeed(), config.getRunningMaxAvgSpeed())
                : String.format("Not running: speeds outside running range");

        return new ClassificationStep("RUNNING", true, passed, reason, checks);
    }

    private ClassificationStep checkCar(double avgSpeedKmh, double maxSpeedKmh, TimelineConfig config) {
        List<ThresholdCheck> checks = new ArrayList<>();
        boolean avgCheck = avgSpeedKmh >= config.getCarMinAvgSpeed();
        boolean maxCheck = maxSpeedKmh >= config.getCarMinMaxSpeed();

        checks.add(new ThresholdCheck("Average Speed", ">=",
                config.getCarMinAvgSpeed(), avgSpeedKmh, avgCheck));
        checks.add(new ThresholdCheck("Max Speed", ">=",
                config.getCarMinMaxSpeed(), maxSpeedKmh, maxCheck));

        boolean passed = avgCheck || maxCheck; // OR logic
        String reason = passed
                ? String.format("Car detected: average speed (%.1f km/h) OR max speed (%.1f km/h) exceeds car thresholds",
                avgSpeedKmh, maxSpeedKmh)
                : String.format("Not a car: average speed (%.1f km/h) < %.1f km/h AND max speed (%.1f km/h) < %.1f km/h",
                avgSpeedKmh, config.getCarMinAvgSpeed(), maxSpeedKmh, config.getCarMinMaxSpeed());

        return new ClassificationStep("CAR", true, passed, reason, checks);
    }

    private ClassificationStep checkWalk(double avgSpeedKmh, double maxSpeedKmh, TimelineConfig config) {
        List<ThresholdCheck> checks = new ArrayList<>();
        boolean avgCheck = avgSpeedKmh <= config.getWalkingMaxAvgSpeed();
        boolean maxCheck = maxSpeedKmh <= config.getWalkingMaxMaxSpeed();

        checks.add(new ThresholdCheck("Average Speed", "<=",
                config.getWalkingMaxAvgSpeed(), avgSpeedKmh, avgCheck));
        checks.add(new ThresholdCheck("Max Speed", "<=",
                config.getWalkingMaxMaxSpeed(), maxSpeedKmh, maxCheck));

        boolean passed = avgCheck && maxCheck; // AND logic
        String reason = passed
                ? String.format("Walk detected: average speed %.1f km/h and max speed %.1f km/h both within walking limits",
                avgSpeedKmh, maxSpeedKmh)
                : String.format("Not a walk: speeds exceed walking limits");

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
            case "CAR" -> String.format("This trip was classified as CAR due to motorized speeds (%.1f km/h).", avgSpeed);
            case "WALK" -> String.format("This trip was classified as WALK based on low speeds (%.1f km/h) typical of walking.", avgSpeed);
            default -> "This trip could not be definitively classified and was marked as UNKNOWN.";
        };
    }
}

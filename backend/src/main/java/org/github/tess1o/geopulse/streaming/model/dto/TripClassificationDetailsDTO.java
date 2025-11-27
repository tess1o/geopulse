package org.github.tess1o.geopulse.streaming.model.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTO containing detailed trip classification information.
 * Provides comprehensive data about how a trip was classified, including:
 * - Trip basic information
 * - GPS statistics used for classification
 * - Configuration thresholds applied
 * - Step-by-step classification decision trace
 * - Final classification reasoning
 * <p>
 * This DTO is used to help users understand why their trip was classified
 * as a specific transport type (WALK, CAR, BICYCLE, RUNNING, TRAIN, FLIGHT, UNKNOWN).
 */
public record TripClassificationDetailsDTO(
        Long tripId,
        Instant timestamp,
        long tripDurationSeconds,
        long distanceMeters,
        String currentClassification,
        TripStatistics statistics,
        ClassificationThresholds thresholds,
        List<ClassificationStep> steps,
        String finalReason
) {
    /**
     * GPS and calculated statistics for the trip.
     * All speeds are in km/h for user-friendliness.
     */
    public record TripStatistics(
            Double avgGpsSpeedKmh,
            Double maxGpsSpeedKmh,
            Double calculatedAvgSpeedKmh,
            Double speedVarianceKmh,
            Integer lowAccuracyPointsCount,
            boolean gpsReliable
    ) {}

    /**
     * Configuration thresholds used for classification.
     * Optional types (bicycle, running, train, flight) may be null if disabled.
     */
    public record ClassificationThresholds(
            // WALK thresholds (mandatory)
            Double walkingMaxAvgSpeed,
            Double walkingMaxMaxSpeed,

            // CAR thresholds (mandatory)
            Double carMinAvgSpeed,
            Double carMinMaxSpeed,

            // BICYCLE thresholds (optional)
            Boolean bicycleEnabled,
            Double bicycleMinAvgSpeed,
            Double bicycleMaxAvgSpeed,
            Double bicycleMaxMaxSpeed,

            // RUNNING thresholds (optional)
            Boolean runningEnabled,
            Double runningMinAvgSpeed,
            Double runningMaxAvgSpeed,
            Double runningMaxMaxSpeed,

            // TRAIN thresholds (optional)
            Boolean trainEnabled,
            Double trainMinAvgSpeed,
            Double trainMaxAvgSpeed,
            Double trainMinMaxSpeed,
            Double trainMaxMaxSpeed,
            Double trainMaxSpeedVariance,

            // FLIGHT thresholds (optional)
            Boolean flightEnabled,
            Double flightMinAvgSpeed,
            Double flightMinMaxSpeed
    ) {}

    /**
     * Represents one step in the classification decision process.
     * Each step corresponds to checking one transport type against thresholds.
     */
    public record ClassificationStep(
            String tripType,           // e.g., "FLIGHT", "TRAIN", "BICYCLE", etc.
            boolean checked,           // Was this type considered? (false if disabled in config)
            boolean passed,            // Did it pass all threshold checks?
            String reason,            // Human-readable explanation
            List<ThresholdCheck> checks  // Individual threshold checks (empty if not checked)
    ) {}

    /**
     * Represents a single threshold check for a transport type.
     * Example: "Average Speed >= 10 km/h" with actual value 12.5 km/h = PASS
     */
    public record ThresholdCheck(
            String name,              // e.g., "Average Speed", "Max Speed", "Speed Variance"
            String operator,          // e.g., ">=", "<=", "<", ">", "=="
            Double threshold,         // Expected threshold value
            Double actual,           // Actual measured value
            boolean passed           // Did this check pass?
    ) {}
}

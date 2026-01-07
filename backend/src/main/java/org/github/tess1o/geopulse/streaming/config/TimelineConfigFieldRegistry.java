package org.github.tess1o.geopulse.streaming.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import org.github.tess1o.geopulse.shared.configuration.ConfigField;
import org.github.tess1o.geopulse.shared.configuration.ConfigFieldRegistry;

/**
 * Registry of all timeline configuration fields.
 * This is the SINGLE place where timeline config fields are defined.
 */
@Getter
@ApplicationScoped
public class TimelineConfigFieldRegistry {

    private final ConfigFieldRegistry<TimelineConfig> registry;

    @Inject
    public TimelineConfigFieldRegistry(TimelineConfigurationProperties properties) {
        this.registry = new ConfigFieldRegistry<TimelineConfig>()
                //stay point detection
                .register(new ConfigField<>(
                        "geopulse.timeline.staypoint.radius_meters",
                        properties.getStaypointRadiusMeters(),
                        TimelineConfig::getStaypointRadiusMeters,
                        TimelineConfig::setStaypointRadiusMeters,
                        Integer::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.staypoint.min_duration_minutes",
                        properties.getStaypointMinDurationMinutes(),
                        TimelineConfig::getStaypointMinDurationMinutes,
                        TimelineConfig::setStaypointMinDurationMinutes,
                        Integer::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.staypoint.use_velocity_accuracy",
                        properties.getUseVelocityAccuracy(),
                        TimelineConfig::getUseVelocityAccuracy,
                        TimelineConfig::setUseVelocityAccuracy,
                        Boolean::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.staypoint.velocity.threshold",
                        properties.getStaypointVelocityThreshold(),
                        TimelineConfig::getStaypointVelocityThreshold,
                        TimelineConfig::setStaypointVelocityThreshold,
                        Double::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.staypoint.accuracy.threshold",
                        properties.getStaypointAccuracyThreshold(),
                        TimelineConfig::getStaypointMaxAccuracyThreshold,
                        TimelineConfig::setStaypointMaxAccuracyThreshold,
                        Double::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.staypoint.min_accuracy_ratio",
                        properties.getStaypointMinAccuracyRatio(),
                        TimelineConfig::getStaypointMinAccuracyRatio,
                        TimelineConfig::setStaypointMinAccuracyRatio,
                        Double::valueOf
                ))
                // Trip Detection
                .register(new ConfigField<>(
                        "geopulse.timeline.trip.detection.algorithm",
                        properties.getTripDetectionAlgorithm(),
                        TimelineConfig::getTripDetectionAlgorithm,
                        TimelineConfig::setTripDetectionAlgorithm,
                        String::valueOf
                ))
                // Merging
                .register(new ConfigField<>(
                        "geopulse.timeline.staypoint.merge.enabled",
                        properties.getMergeEnabled(),
                        TimelineConfig::getIsMergeEnabled,
                        TimelineConfig::setIsMergeEnabled,
                        Boolean::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.staypoint.merge.max_distance_meters",
                        properties.getMergeMaxDistanceMeters(),
                        TimelineConfig::getMergeMaxDistanceMeters,
                        TimelineConfig::setMergeMaxDistanceMeters,
                        Integer::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.staypoint.merge.max_time_gap_minutes",
                        properties.getMergeMaxTimeGapMinutes(),
                        TimelineConfig::getMergeMaxTimeGapMinutes,
                        TimelineConfig::setMergeMaxTimeGapMinutes,
                        Integer::valueOf
                ))
                // GPS Path Simplification
                .register(new ConfigField<>(
                        "geopulse.timeline.path.simplification.enabled",
                        properties.getPathSimplificationEnabled(),
                        TimelineConfig::getPathSimplificationEnabled,
                        TimelineConfig::setPathSimplificationEnabled,
                        Boolean::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.path.simplification.tolerance",
                        properties.getPathSimplificationTolerance(),
                        TimelineConfig::getPathSimplificationTolerance,
                        TimelineConfig::setPathSimplificationTolerance,
                        Double::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.path.simplification.max_points",
                        properties.getPathMaxPoints(),
                        TimelineConfig::getPathMaxPoints,
                        TimelineConfig::setPathMaxPoints,
                        Integer::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.path.simplification.adaptive",
                        properties.getPathAdaptiveSimplification(),
                        TimelineConfig::getPathAdaptiveSimplification,
                        TimelineConfig::setPathAdaptiveSimplification,
                        Boolean::valueOf
                ))
                // Data Gap Detection
                .register(new ConfigField<>(
                        "geopulse.timeline.data_gap.threshold_seconds",
                        properties.getDataGapThresholdSeconds(),
                        TimelineConfig::getDataGapThresholdSeconds,
                        TimelineConfig::setDataGapThresholdSeconds,
                        Integer::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.data_gap.min_duration_seconds",
                        properties.getDataGapMinDurationSeconds(),
                        TimelineConfig::getDataGapMinDurationSeconds,
                        TimelineConfig::setDataGapMinDurationSeconds,
                        Integer::valueOf
                ))
                // Gap Stay Inference
                .register(new ConfigField<>(
                        "geopulse.timeline.gap_stay_inference.enabled",
                        properties.getGapStayInferenceEnabled(),
                        TimelineConfig::getGapStayInferenceEnabled,
                        TimelineConfig::setGapStayInferenceEnabled,
                        Boolean::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.gap_stay_inference.max_gap_hours",
                        properties.getGapStayInferenceMaxGapHours(),
                        TimelineConfig::getGapStayInferenceMaxGapHours,
                        TimelineConfig::setGapStayInferenceMaxGapHours,
                        Integer::valueOf
                ))
                // Gap Trip Inference
                .register(new ConfigField<>(
                        "geopulse.timeline.gap_trip_inference.enabled",
                        properties.getGapTripInferenceEnabled(),
                        TimelineConfig::getGapTripInferenceEnabled,
                        TimelineConfig::setGapTripInferenceEnabled,
                        Boolean::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.gap_trip_inference.min_distance_meters",
                        properties.getGapTripInferenceMinDistanceMeters(),
                        TimelineConfig::getGapTripInferenceMinDistanceMeters,
                        TimelineConfig::setGapTripInferenceMinDistanceMeters,
                        Integer::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.gap_trip_inference.min_gap_hours",
                        properties.getGapTripInferenceMinGapHours(),
                        TimelineConfig::getGapTripInferenceMinGapHours,
                        TimelineConfig::setGapTripInferenceMinGapHours,
                        Integer::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.gap_trip_inference.max_gap_hours",
                        properties.getGapTripInferenceMaxGapHours(),
                        TimelineConfig::getGapTripInferenceMaxGapHours,
                        TimelineConfig::setGapTripInferenceMaxGapHours,
                        Integer::valueOf
                ))
                // Travel Classification
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.walking.max_avg_speed",
                        properties.getWalkingMaxAvgSpeed(),
                        TimelineConfig::getWalkingMaxAvgSpeed,
                        TimelineConfig::setWalkingMaxAvgSpeed,
                        Double::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.walking.max_max_speed",
                        properties.getWalkingMaxMaxSpeed(),
                        TimelineConfig::getWalkingMaxMaxSpeed,
                        TimelineConfig::setWalkingMaxMaxSpeed,
                        Double::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.car.min_avg_speed",
                        properties.getCarMinAvgSpeed(),
                        TimelineConfig::getCarMinAvgSpeed,
                        TimelineConfig::setCarMinAvgSpeed,
                        Double::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.car.min_max_speed",
                        properties.getCarMinMaxSpeed(),
                        TimelineConfig::getCarMinMaxSpeed,
                        TimelineConfig::setCarMinMaxSpeed,
                        Double::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.short_distance_km",
                        properties.getShortDistanceKm(),
                        TimelineConfig::getShortDistanceKm,
                        TimelineConfig::setShortDistanceKm,
                        Double::valueOf
                ))
                // Trip Stop Detection
                .register(new ConfigField<>(
                        "geopulse.timeline.trip.arrival.min_duration_seconds",
                        properties.getTripArrivalDetectionMinDurationSeconds(),
                        TimelineConfig::getTripArrivalDetectionMinDurationSeconds,
                        TimelineConfig::setTripArrivalDetectionMinDurationSeconds,
                        Integer::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.trip.sustained_stop.min_duration_seconds",
                        properties.getTripSustainedStopMinDurationSeconds(),
                        TimelineConfig::getTripSustainedStopMinDurationSeconds,
                        TimelineConfig::setTripSustainedStopMinDurationSeconds,
                        Integer::valueOf
                ))
                // Optional Trip Types - Bicycle
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.bicycle.enabled",
                        properties.getBicycleEnabled(),
                        TimelineConfig::getBicycleEnabled,
                        TimelineConfig::setBicycleEnabled,
                        Boolean::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.bicycle.min_avg_speed",
                        properties.getBicycleMinAvgSpeed(),
                        TimelineConfig::getBicycleMinAvgSpeed,
                        TimelineConfig::setBicycleMinAvgSpeed,
                        Double::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.bicycle.max_avg_speed",
                        properties.getBicycleMaxAvgSpeed(),
                        TimelineConfig::getBicycleMaxAvgSpeed,
                        TimelineConfig::setBicycleMaxAvgSpeed,
                        Double::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.bicycle.max_max_speed",
                        properties.getBicycleMaxMaxSpeed(),
                        TimelineConfig::getBicycleMaxMaxSpeed,
                        TimelineConfig::setBicycleMaxMaxSpeed,
                        Double::valueOf
                ))
                // Optional Trip Types - Running
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.running.enabled",
                        properties.getRunningEnabled(),
                        TimelineConfig::getRunningEnabled,
                        TimelineConfig::setRunningEnabled,
                        Boolean::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.running.min_avg_speed",
                        properties.getRunningMinAvgSpeed(),
                        TimelineConfig::getRunningMinAvgSpeed,
                        TimelineConfig::setRunningMinAvgSpeed,
                        Double::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.running.max_avg_speed",
                        properties.getRunningMaxAvgSpeed(),
                        TimelineConfig::getRunningMaxAvgSpeed,
                        TimelineConfig::setRunningMaxAvgSpeed,
                        Double::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.running.max_max_speed",
                        properties.getRunningMaxMaxSpeed(),
                        TimelineConfig::getRunningMaxMaxSpeed,
                        TimelineConfig::setRunningMaxMaxSpeed,
                        Double::valueOf
                ))
                // Optional Trip Types - Train
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.train.enabled",
                        properties.getTrainEnabled(),
                        TimelineConfig::getTrainEnabled,
                        TimelineConfig::setTrainEnabled,
                        Boolean::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.train.min_avg_speed",
                        properties.getTrainMinAvgSpeed(),
                        TimelineConfig::getTrainMinAvgSpeed,
                        TimelineConfig::setTrainMinAvgSpeed,
                        Double::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.train.max_avg_speed",
                        properties.getTrainMaxAvgSpeed(),
                        TimelineConfig::getTrainMaxAvgSpeed,
                        TimelineConfig::setTrainMaxAvgSpeed,
                        Double::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.train.min_max_speed",
                        properties.getTrainMinMaxSpeed(),
                        TimelineConfig::getTrainMinMaxSpeed,
                        TimelineConfig::setTrainMinMaxSpeed,
                        Double::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.train.max_max_speed",
                        properties.getTrainMaxMaxSpeed(),
                        TimelineConfig::getTrainMaxMaxSpeed,
                        TimelineConfig::setTrainMaxMaxSpeed,
                        Double::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.train.max_speed_variance",
                        properties.getTrainMaxSpeedVariance(),
                        TimelineConfig::getTrainMaxSpeedVariance,
                        TimelineConfig::setTrainMaxSpeedVariance,
                        Double::valueOf
                ))
                // Optional Trip Types - Flight
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.flight.enabled",
                        properties.getFlightEnabled(),
                        TimelineConfig::getFlightEnabled,
                        TimelineConfig::setFlightEnabled,
                        Boolean::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.flight.min_avg_speed",
                        properties.getFlightMinAvgSpeed(),
                        TimelineConfig::getFlightMinAvgSpeed,
                        TimelineConfig::setFlightMinAvgSpeed,
                        Double::valueOf
                ))
                .register(new ConfigField<>(
                        "geopulse.timeline.travel.classification.flight.min_max_speed",
                        properties.getFlightMinMaxSpeed(),
                        TimelineConfig::getFlightMinMaxSpeed,
                        TimelineConfig::setFlightMinMaxSpeed,
                        Double::valueOf
                ));
    }
}
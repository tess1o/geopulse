package org.github.tess1o.geopulse.timeline.config;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.github.tess1o.geopulse.shared.configuration.ConfigField;
import org.github.tess1o.geopulse.shared.configuration.ConfigFieldRegistry;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;

/**
 * Registry of all timeline configuration fields.
 * This is the SINGLE place where timeline config fields are defined.
 */
@Getter
@ApplicationScoped
public class TimelineConfigFieldRegistry {

    private final ConfigFieldRegistry<TimelineConfig> registry;

    public TimelineConfigFieldRegistry() {
        this.registry = new ConfigFieldRegistry<TimelineConfig>()
            // Staypoint Detection
            .register(new ConfigField<>(
                "geopulse.timeline.staypoint.detection.algorithm",
                "enhanced",
                TimelineConfig::getStaypointDetectionAlgorithm,
                TimelineConfig::setStaypointDetectionAlgorithm,
                String::valueOf
            ))
            .register(new ConfigField<>(
                "geopulse.timeline.staypoint.use_velocity_accuracy",
                "true",
                TimelineConfig::getUseVelocityAccuracy,
                TimelineConfig::setUseVelocityAccuracy,
                Boolean::valueOf
            ))
            .register(new ConfigField<>(
                "geopulse.timeline.staypoint.velocity.threshold",
                "2.0",
                TimelineConfig::getStaypointVelocityThreshold,
                TimelineConfig::setStaypointVelocityThreshold,
                Double::valueOf
            ))
            .register(new ConfigField<>(
                "geopulse.timeline.staypoint.accuracy.threshold",
                "60.0",
                TimelineConfig::getStaypointMaxAccuracyThreshold,
                TimelineConfig::setStaypointMaxAccuracyThreshold,
                Double::valueOf
            ))
            .register(new ConfigField<>(
                "geopulse.timeline.staypoint.min_accuracy_ratio",
                "0.5",
                TimelineConfig::getStaypointMinAccuracyRatio,
                TimelineConfig::setStaypointMinAccuracyRatio,
                Double::valueOf
            ))
            // Trip Detection
            .register(new ConfigField<>(
                "geopulse.timeline.trip.detection.algorithm",
                "single",
                TimelineConfig::getTripDetectionAlgorithm,
                TimelineConfig::setTripDetectionAlgorithm,
                String::valueOf
            ))
            .register(new ConfigField<>(
                "geopulse.timeline.trip.min_distance_meters",
                "50",
                TimelineConfig::getTripMinDistanceMeters,
                TimelineConfig::setTripMinDistanceMeters,
                Integer::valueOf
            ))
            .register(new ConfigField<>(
                "geopulse.timeline.trip.min_duration_minutes",
                "7",
                TimelineConfig::getTripMinDurationMinutes,
                TimelineConfig::setTripMinDurationMinutes,
                Integer::valueOf
            ))
            // Merging
            .register(new ConfigField<>(
                "geopulse.timeline.staypoint.merge.enabled",
                "true",
                TimelineConfig::getIsMergeEnabled,
                TimelineConfig::setIsMergeEnabled,
                Boolean::valueOf
            ))
            .register(new ConfigField<>(
                "geopulse.timeline.staypoint.merge.max_distance_meters",
                "150",
                TimelineConfig::getMergeMaxDistanceMeters,
                TimelineConfig::setMergeMaxDistanceMeters,
                Integer::valueOf
            ))
            .register(new ConfigField<>(
                "geopulse.timeline.staypoint.merge.max_time_gap_minutes",
                "10",
                TimelineConfig::getMergeMaxTimeGapMinutes,
                TimelineConfig::setMergeMaxTimeGapMinutes,
                Integer::valueOf
            ))
            // GPS Path Simplification
            .register(new ConfigField<>(
                "geopulse.timeline.path.simplification.enabled",
                "true",
                TimelineConfig::getPathSimplificationEnabled,
                TimelineConfig::setPathSimplificationEnabled,
                Boolean::valueOf
            ))
            .register(new ConfigField<>(
                "geopulse.timeline.path.simplification.tolerance",
                "15.0",
                TimelineConfig::getPathSimplificationTolerance,
                TimelineConfig::setPathSimplificationTolerance,
                Double::valueOf
            ))
            .register(new ConfigField<>(
                "geopulse.timeline.path.simplification.max_points",
                "100",
                TimelineConfig::getPathMaxPoints,
                TimelineConfig::setPathMaxPoints,
                Integer::valueOf
            ))
            .register(new ConfigField<>(
                "geopulse.timeline.path.simplification.adaptive",
                "true",
                TimelineConfig::getPathAdaptiveSimplification,
                TimelineConfig::setPathAdaptiveSimplification,
                Boolean::valueOf
            ))
            // Data Gap Detection
            .register(new ConfigField<>(
                "geopulse.timeline.data_gap.threshold_seconds",
                "10800", // 3 hours default
                TimelineConfig::getDataGapThresholdSeconds,
                TimelineConfig::setDataGapThresholdSeconds,
                Integer::valueOf
            ))
            .register(new ConfigField<>(
                "geopulse.timeline.data_gap.min_duration_seconds", 
                "1800", // 30 minutes minimum gap duration
                TimelineConfig::getDataGapMinDurationSeconds,
                TimelineConfig::setDataGapMinDurationSeconds,
                Integer::valueOf
            ));
    }
}
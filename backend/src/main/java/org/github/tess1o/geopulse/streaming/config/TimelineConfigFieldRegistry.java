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
                ));
    }
}
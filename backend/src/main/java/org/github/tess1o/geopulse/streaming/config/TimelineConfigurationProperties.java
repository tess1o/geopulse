package org.github.tess1o.geopulse.streaming.config;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Configuration properties for timeline processing with environment variable support.
 * All properties can be overridden via environment variables.
 */
@Getter
@ApplicationScoped
public class TimelineConfigurationProperties {

    @ConfigProperty(name = "geopulse.timeline.staypoint.use_velocity_accuracy", defaultValue = "true")
    String useVelocityAccuracy;

    @ConfigProperty(name = "geopulse.timeline.staypoint.velocity.threshold", defaultValue = "2.0")
    String staypointVelocityThreshold;

    @ConfigProperty(name = "geopulse.timeline.staypoint.accuracy.threshold", defaultValue = "60.0")
    String staypointAccuracyThreshold;

    @ConfigProperty(name = "geopulse.timeline.staypoint.min_accuracy_ratio", defaultValue = "0.5")
    String staypointMinAccuracyRatio;

    // Trip Detection
    @ConfigProperty(name = "geopulse.timeline.trip.detection.algorithm", defaultValue = "single")
    String tripDetectionAlgorithm;

    @ConfigProperty(name = "geopulse.timeline.staypoint.radius_meters", defaultValue = "50")
    String staypointRadiusMeters;

    @ConfigProperty(name = "geopulse.timeline.staypoint.min_duration_minutes", defaultValue = "7")
    String staypointMinDurationMinutes;

    // Merging
    @ConfigProperty(name = "geopulse.timeline.staypoint.merge.enabled", defaultValue = "true")
    String mergeEnabled;

    @ConfigProperty(name = "geopulse.timeline.staypoint.merge.max_distance_meters", defaultValue = "150")
    String mergeMaxDistanceMeters;

    @ConfigProperty(name = "geopulse.timeline.staypoint.merge.max_time_gap_minutes", defaultValue = "10")
    String mergeMaxTimeGapMinutes;

    // GPS Path Simplification
    @ConfigProperty(name = "geopulse.timeline.path.simplification.enabled", defaultValue = "true")
    String pathSimplificationEnabled;

    @ConfigProperty(name = "geopulse.timeline.path.simplification.tolerance", defaultValue = "15.0")
    String pathSimplificationTolerance;

    @ConfigProperty(name = "geopulse.timeline.path.simplification.max_points", defaultValue = "100")
    String pathMaxPoints;

    @ConfigProperty(name = "geopulse.timeline.path.simplification.adaptive", defaultValue = "true")
    String pathAdaptiveSimplification;

    // Data Gap Detection
    @ConfigProperty(name = "geopulse.timeline.data_gap.threshold_seconds", defaultValue = "10800")
    String dataGapThresholdSeconds;

    @ConfigProperty(name = "geopulse.timeline.data_gap.min_duration_seconds", defaultValue = "1800")
    String dataGapMinDurationSeconds;

    // Travel Classification
    @ConfigProperty(name = "geopulse.timeline.travel.classification.walking.max_avg_speed", defaultValue = "6.0")
    String walkingMaxAvgSpeed;

    @ConfigProperty(name = "geopulse.timeline.travel.classification.walking.max_max_speed", defaultValue = "8.0")
    String walkingMaxMaxSpeed;

    @ConfigProperty(name = "geopulse.timeline.travel.classification.car.min_avg_speed", defaultValue = "8.0")
    String carMinAvgSpeed;

    @ConfigProperty(name = "geopulse.timeline.travel.classification.car.min_max_speed", defaultValue = "15.0")
    String carMinMaxSpeed;

    @ConfigProperty(name = "geopulse.timeline.travel.classification.short_distance_km", defaultValue = "1.0")
    String shortDistanceKm;

    // Trip Stop Detection
    @ConfigProperty(name = "geopulse.timeline.trip.arrival.min_duration_seconds", defaultValue = "90")
    String tripArrivalDetectionMinDurationSeconds;

    @ConfigProperty(name = "geopulse.timeline.trip.sustained_stop.min_duration_seconds", defaultValue = "60")
    String tripSustainedStopMinDurationSeconds;
}
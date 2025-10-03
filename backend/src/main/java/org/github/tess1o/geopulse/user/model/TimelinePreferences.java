package org.github.tess1o.geopulse.user.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimelinePreferences {
    private Boolean useVelocityAccuracy;
    private Double staypointVelocityThreshold;
    private Double staypointMaxAccuracyThreshold;
    private Double staypointMinAccuracyRatio;
    private String tripDetectionAlgorithm;
    private Integer staypointRadiusMeters;
    private Integer staypointMinDurationMinutes;
    private Boolean isMergeEnabled;
    private Integer mergeMaxDistanceMeters;
    private Integer mergeMaxTimeGapMinutes;
    
    // GPS Path Simplification Settings
    private Boolean pathSimplificationEnabled;
    private Double pathSimplificationTolerance;
    private Integer pathMaxPoints;
    private Boolean pathAdaptiveSimplification;
    
    // Data Gap Detection Settings
    private Integer dataGapThresholdSeconds;
    private Integer dataGapMinDurationSeconds;
    
    // Travel Classification Settings
    private Double walkingMaxAvgSpeed;
    private Double walkingMaxMaxSpeed;
    private Double carMinAvgSpeed;
    private Double carMinMaxSpeed;
    private Double shortDistanceKm;

    // Trip Stop Detection Settings
    private Integer tripArrivalDetectionMinDurationSeconds;
    private Integer tripSustainedStopMinDurationSeconds;
}

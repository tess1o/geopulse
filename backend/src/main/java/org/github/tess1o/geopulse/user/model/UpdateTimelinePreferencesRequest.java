package org.github.tess1o.geopulse.user.model;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class UpdateTimelinePreferencesRequest {
    
    private Boolean useVelocityAccuracy;
    
    @DecimalMin(value = "0.1", message = "Staypoint velocity threshold must be at least 0.1")
    @DecimalMax(value = "100.0", message = "Staypoint velocity threshold must be at most 100.0")
    private Double staypointVelocityThreshold;
    
    @DecimalMin(value = "1.0", message = "Staypoint max accuracy threshold must be at least 1.0")
    @DecimalMax(value = "1000.0", message = "Staypoint max accuracy threshold must be at most 1000.0")
    private Double staypointMaxAccuracyThreshold;
    
    @DecimalMin(value = "0.1", message = "Staypoint min accuracy ratio must be at least 0.1")
    @DecimalMax(value = "1.0", message = "Staypoint min accuracy ratio must be at most 1.0")
    private Double staypointMinAccuracyRatio;
    
    @Pattern(regexp = "^(single|multiple)$", message = "Trip detection algorithm must be one of: single, multiple")
    private String tripDetectionAlgorithm;
    
    @Min(value = 1, message = "Staypoint radius must be at least 1 meter")
    @Max(value = 10_000, message = "Staypoint radius must be at most 10000 meters")
    private Integer staypointRadiusMeters;
    
    @Min(value = 1, message = "Stay min duration must be at least 1 minute")
    @Max(value = 1440, message = "Stay min duration must be at most 1440 minutes (24 hours)")
    private Integer staypointMinDurationMinutes;
    
    private Boolean isMergeEnabled;
    
    @Min(value = 1, message = "Merge max distance must be at least 1 meter")
    @Max(value = 5000, message = "Merge max distance must be at most 5000 meters")
    private Integer mergeMaxDistanceMeters;
    
    @Min(value = 1, message = "Merge max time gap must be at least 1 minute")
    @Max(value = 720, message = "Merge max time gap must be at most 720 minutes (12 hours)")
    private Integer mergeMaxTimeGapMinutes;
    
    // GPS Path Simplification Settings
    private Boolean pathSimplificationEnabled;
    
    @DecimalMin(value = "1.0", message = "Path simplification tolerance must be at least 1.0 meters")
    @DecimalMax(value = "100.0", message = "Path simplification tolerance must be at most 100.0 meters")
    private Double pathSimplificationTolerance;
    
    @Min(value = 10, message = "Path max points must be at least 10")
    @Max(value = 1000, message = "Path max points must be at most 1000")
    private Integer pathMaxPoints;
    
    private Boolean pathAdaptiveSimplification;

    @Min(value = 1, message = "Data Gap threshold (seconds) must be positive")
    private Integer dataGapThresholdSeconds;

    @Min(value = 1, message = "Data Gap minimum duration (seconds) must be positive")
    private Integer dataGapMinDurationSeconds;
    
    // Timezone Settings
    @Pattern(regexp = "^[A-Za-z_]+/[A-Za-z_]+$", 
             message = "Timezone must be a valid timezone ID (e.g., 'Europe/Kyiv', 'America/New_York')")
    private String timezone;
}

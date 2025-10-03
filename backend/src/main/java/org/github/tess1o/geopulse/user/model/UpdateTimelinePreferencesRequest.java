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
    
    // Travel Classification Settings
    @DecimalMin(value = "3.0", message = "Walking max average speed must be at least 3.0 km/h")
    @DecimalMax(value = "10.0", message = "Walking max average speed must be at most 10.0 km/h")
    private Double walkingMaxAvgSpeed;
    
    @DecimalMin(value = "5.0", message = "Walking max maximum speed must be at least 5.0 km/h") 
    @DecimalMax(value = "15.0", message = "Walking max maximum speed must be at most 15.0 km/h")
    private Double walkingMaxMaxSpeed;
    
    @DecimalMin(value = "5.0", message = "Car min average speed must be at least 5.0 km/h")
    @DecimalMax(value = "25.0", message = "Car min average speed must be at most 25.0 km/h")
    private Double carMinAvgSpeed;
    
    @DecimalMin(value = "10.0", message = "Car min maximum speed must be at least 10.0 km/h")
    @DecimalMax(value = "50.0", message = "Car min maximum speed must be at most 50.0 km/h")
    private Double carMinMaxSpeed;
    
    @DecimalMin(value = "0.1", message = "Short distance threshold must be at least 0.1 km")
    @DecimalMax(value = "3.0", message = "Short distance threshold must be at most 3.0 km")
    private Double shortDistanceKm;

    // Trip Stop Detection Settings
    @Min(value = 10, message = "Trip arrival detection min duration must be at least 10 seconds")
    @Max(value = 300, message = "Trip arrival detection min duration must be at most 300 seconds (5 minutes)")
    private Integer tripArrivalDetectionMinDurationSeconds;

    @Min(value = 10, message = "Trip sustained stop min duration must be at least 10 seconds")
    @Max(value = 600, message = "Trip sustained stop min duration must be at most 600 seconds (10 minutes)")
    private Integer tripSustainedStopMinDurationSeconds;
}

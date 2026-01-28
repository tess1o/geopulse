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

    // Gap Stay Inference Settings
    private Boolean gapStayInferenceEnabled;

    @Min(value = 1, message = "Gap stay inference max gap hours must be at least 1 hour")
    @Max(value = 168, message = "Gap stay inference max gap hours must be at most 168 hours (1 week)")
    private Integer gapStayInferenceMaxGapHours;

    // Gap Trip Inference Settings
    private Boolean gapTripInferenceEnabled;

    @Min(value = 1000, message = "Gap trip inference min distance must be at least 1000 meters (1 km)")
    @Max(value = 1000000, message = "Gap trip inference min distance must be at most 1000000 meters (1000 km)")
    private Integer gapTripInferenceMinDistanceMeters;

    @Min(value = 0, message = "Gap trip inference min gap hours must be at least 0 hours")
    @Max(value = 24, message = "Gap trip inference min gap hours must be at most 24 hours")
    private Integer gapTripInferenceMinGapHours;

    @Min(value = 1, message = "Gap trip inference max gap hours must be at least 1 hour")
    @Max(value = 336, message = "Gap trip inference max gap hours must be at most 336 hours (2 weeks)")
    private Integer gapTripInferenceMaxGapHours;

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

    @Min(value = 2, message = "Trip arrival min points must be at least 2")
    @Max(value = 5, message = "Trip arrival min points must be at most 5")
    private Integer tripArrivalMinPoints;

    // Optional Trip Types - Bicycle
    private Boolean bicycleEnabled;

    @DecimalMin(value = "5.0", message = "Bicycle min avg speed must be at least 5.0 km/h")
    @DecimalMax(value = "15.0", message = "Bicycle min avg speed must be at most 15.0 km/h")
    private Double bicycleMinAvgSpeed;

    @DecimalMin(value = "15.0", message = "Bicycle max avg speed must be at least 15.0 km/h")
    @DecimalMax(value = "35.0", message = "Bicycle max avg speed must be at most 35.0 km/h")
    private Double bicycleMaxAvgSpeed;

    @DecimalMin(value = "20.0", message = "Bicycle max max speed must be at least 20.0 km/h")
    @DecimalMax(value = "50.0", message = "Bicycle max max speed must be at most 50.0 km/h")
    private Double bicycleMaxMaxSpeed;

    // Optional Trip Types - Running
    private Boolean runningEnabled;

    @DecimalMin(value = "5.0", message = "Running min avg speed must be at least 5.0 km/h")
    @DecimalMax(value = "10.0", message = "Running min avg speed must be at most 10.0 km/h")
    private Double runningMinAvgSpeed;

    @DecimalMin(value = "10.0", message = "Running max avg speed must be at least 10.0 km/h")
    @DecimalMax(value = "18.0", message = "Running max avg speed must be at most 18.0 km/h")
    private Double runningMaxAvgSpeed;

    @DecimalMin(value = "12.0", message = "Running max max speed must be at least 12.0 km/h")
    @DecimalMax(value = "25.0", message = "Running max max speed must be at most 25.0 km/h")
    private Double runningMaxMaxSpeed;

    // Optional Trip Types - Train
    private Boolean trainEnabled;

    @DecimalMin(value = "20.0", message = "Train min avg speed must be at least 20.0 km/h")
    @DecimalMax(value = "50.0", message = "Train min avg speed must be at most 50.0 km/h")
    private Double trainMinAvgSpeed;

    @DecimalMin(value = "80.0", message = "Train max avg speed must be at least 80.0 km/h")
    @DecimalMax(value = "200.0", message = "Train max avg speed must be at most 200.0 km/h")
    private Double trainMaxAvgSpeed;

    @DecimalMin(value = "60.0", message = "Train min max speed must be at least 60.0 km/h")
    @DecimalMax(value = "120.0", message = "Train min max speed must be at most 120.0 km/h")
    private Double trainMinMaxSpeed;

    @DecimalMin(value = "100.0", message = "Train max max speed must be at least 100.0 km/h")
    @DecimalMax(value = "250.0", message = "Train max max speed must be at most 250.0 km/h")
    private Double trainMaxMaxSpeed;

    @DecimalMin(value = "5.0", message = "Train max speed variance must be at least 5.0")
    @DecimalMax(value = "30.0", message = "Train max speed variance must be at most 30.0")
    private Double trainMaxSpeedVariance;

    // Optional Trip Types - Flight
    private Boolean flightEnabled;

    @DecimalMin(value = "250.0", message = "Flight min avg speed must be at least 250.0 km/h")
    @DecimalMax(value = "600.0", message = "Flight min avg speed must be at most 600.0 km/h")
    private Double flightMinAvgSpeed;

    @DecimalMin(value = "400.0", message = "Flight min max speed must be at least 400.0 km/h")
    @DecimalMax(value = "900.0", message = "Flight min max speed must be at most 900.0 km/h")
    private Double flightMinMaxSpeed;
}

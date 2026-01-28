package org.github.tess1o.geopulse.user.mapper;

import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;
import org.github.tess1o.geopulse.user.model.UpdateTimelinePreferencesRequest;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for timeline preferences conversions.
 */
@Mapper(
    componentModel = "jakarta-cdi",
    unmappedTargetPolicy = ReportingPolicy.IGNORE // Ignore unmapped fields
)
public interface TimelinePreferencesMapper {

    /**
     * Convert TimelinePreferences to TimelineConfig
     */
    TimelineConfig preferencesToConfig(TimelinePreferences preferences);

    /**
     * Convert UpdateTimelinePreferencesRequest to TimelineConfig
     */
    TimelineConfig requestToConfig(UpdateTimelinePreferencesRequest request);

    /**
     * Convert TimelineConfig to UpdateTimelinePreferencesRequest
     */
    UpdateTimelinePreferencesRequest configToRequest(TimelineConfig config);

    /**
     * Update TimelinePreferences from TimelineConfig using @MappingTarget
     */
    void updatePreferencesFromConfig(TimelineConfig config, @MappingTarget TimelinePreferences preferences);

    /**
     * Convert TimelineConfig to TimelinePreferences
     */
    TimelinePreferences configToPreferences(TimelineConfig config);

    /**
     * Merge import preferences into existing preferences.
     * Only non-null values from import will override existing values.
     * If existing is null, returns a copy of import preferences.
     * If import is null, returns existing preferences unchanged.
     */
    @org.mapstruct.Mapping(target = "useVelocityAccuracy", 
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "staypointVelocityThreshold", 
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "staypointMaxAccuracyThreshold", 
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "staypointMinAccuracyRatio", 
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "tripDetectionAlgorithm", 
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "staypointRadiusMeters", 
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "staypointMinDurationMinutes", 
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "isMergeEnabled", 
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "mergeMaxDistanceMeters", 
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "mergeMaxTimeGapMinutes", 
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "pathSimplificationEnabled", 
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "pathSimplificationTolerance", 
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "pathMaxPoints", 
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "pathAdaptiveSimplification", 
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "dataGapThresholdSeconds",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "dataGapMinDurationSeconds",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    // Gap Stay Inference
    @org.mapstruct.Mapping(target = "gapStayInferenceEnabled",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "gapStayInferenceMaxGapHours",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    // Gap Trip Inference
    @org.mapstruct.Mapping(target = "gapTripInferenceEnabled",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "gapTripInferenceMinDistanceMeters",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "gapTripInferenceMinGapHours",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "gapTripInferenceMaxGapHours",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "walkingMaxAvgSpeed",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "walkingMaxMaxSpeed", 
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "carMinAvgSpeed", 
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "carMinMaxSpeed", 
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "shortDistanceKm",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "tripArrivalDetectionMinDurationSeconds",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "tripSustainedStopMinDurationSeconds",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "tripArrivalMinPoints",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    // Optional Trip Types - Bicycle
    @org.mapstruct.Mapping(target = "bicycleEnabled",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "bicycleMinAvgSpeed",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "bicycleMaxAvgSpeed",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "bicycleMaxMaxSpeed",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    // Optional Trip Types - Running
    @org.mapstruct.Mapping(target = "runningEnabled",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "runningMinAvgSpeed",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "runningMaxAvgSpeed",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "runningMaxMaxSpeed",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    // Optional Trip Types - Train
    @org.mapstruct.Mapping(target = "trainEnabled",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "trainMinAvgSpeed",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "trainMaxAvgSpeed",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "trainMinMaxSpeed",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "trainMaxMaxSpeed",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "trainMaxSpeedVariance",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    // Optional Trip Types - Flight
    @org.mapstruct.Mapping(target = "flightEnabled",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "flightMinAvgSpeed",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "flightMinMaxSpeed",
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void mergeImportIntoExisting(TimelinePreferences fromImport, @MappingTarget TimelinePreferences existing);

    /**
     * Merge import preferences with existing preferences, handling null cases.
     * This is a convenience method that properly handles the null cases that MapStruct can't handle directly.
     */
    default TimelinePreferences mergePreferences(TimelinePreferences fromImport, TimelinePreferences existing) {
        // If no import preferences, return existing (could be null)
        if (fromImport == null) {
            return existing;
        }
        
        // If no existing preferences, return copy of import preferences
        if (existing == null) {
            return TimelinePreferences.builder()
                    .useVelocityAccuracy(fromImport.getUseVelocityAccuracy())
                    .staypointVelocityThreshold(fromImport.getStaypointVelocityThreshold())
                    .staypointMaxAccuracyThreshold(fromImport.getStaypointMaxAccuracyThreshold())
                    .staypointMinAccuracyRatio(fromImport.getStaypointMinAccuracyRatio())
                    .tripDetectionAlgorithm(fromImport.getTripDetectionAlgorithm())
                    .staypointRadiusMeters(fromImport.getStaypointRadiusMeters())
                    .staypointMinDurationMinutes(fromImport.getStaypointMinDurationMinutes())
                    .isMergeEnabled(fromImport.getIsMergeEnabled())
                    .mergeMaxDistanceMeters(fromImport.getMergeMaxDistanceMeters())
                    .mergeMaxTimeGapMinutes(fromImport.getMergeMaxTimeGapMinutes())
                    .pathSimplificationEnabled(fromImport.getPathSimplificationEnabled())
                    .pathSimplificationTolerance(fromImport.getPathSimplificationTolerance())
                    .pathMaxPoints(fromImport.getPathMaxPoints())
                    .pathAdaptiveSimplification(fromImport.getPathAdaptiveSimplification())
                    .dataGapThresholdSeconds(fromImport.getDataGapThresholdSeconds())
                    .dataGapMinDurationSeconds(fromImport.getDataGapMinDurationSeconds())
                    .gapStayInferenceEnabled(fromImport.getGapStayInferenceEnabled())
                    .gapStayInferenceMaxGapHours(fromImport.getGapStayInferenceMaxGapHours())
                    .gapTripInferenceEnabled(fromImport.getGapTripInferenceEnabled())
                    .gapTripInferenceMinDistanceMeters(fromImport.getGapTripInferenceMinDistanceMeters())
                    .gapTripInferenceMinGapHours(fromImport.getGapTripInferenceMinGapHours())
                    .gapTripInferenceMaxGapHours(fromImport.getGapTripInferenceMaxGapHours())
                    .walkingMaxAvgSpeed(fromImport.getWalkingMaxAvgSpeed())
                    .walkingMaxMaxSpeed(fromImport.getWalkingMaxMaxSpeed())
                    .carMinAvgSpeed(fromImport.getCarMinAvgSpeed())
                    .carMinMaxSpeed(fromImport.getCarMinMaxSpeed())
                    .shortDistanceKm(fromImport.getShortDistanceKm())
                    .tripArrivalDetectionMinDurationSeconds(fromImport.getTripArrivalDetectionMinDurationSeconds())
                    .tripSustainedStopMinDurationSeconds(fromImport.getTripSustainedStopMinDurationSeconds())
                    .tripArrivalMinPoints(fromImport.getTripArrivalMinPoints())
                    // Optional Trip Types
                    .bicycleEnabled(fromImport.getBicycleEnabled())
                    .bicycleMinAvgSpeed(fromImport.getBicycleMinAvgSpeed())
                    .bicycleMaxAvgSpeed(fromImport.getBicycleMaxAvgSpeed())
                    .bicycleMaxMaxSpeed(fromImport.getBicycleMaxMaxSpeed())
                    .runningEnabled(fromImport.getRunningEnabled())
                    .runningMinAvgSpeed(fromImport.getRunningMinAvgSpeed())
                    .runningMaxAvgSpeed(fromImport.getRunningMaxAvgSpeed())
                    .runningMaxMaxSpeed(fromImport.getRunningMaxMaxSpeed())
                    .trainEnabled(fromImport.getTrainEnabled())
                    .trainMinAvgSpeed(fromImport.getTrainMinAvgSpeed())
                    .trainMaxAvgSpeed(fromImport.getTrainMaxAvgSpeed())
                    .trainMinMaxSpeed(fromImport.getTrainMinMaxSpeed())
                    .trainMaxMaxSpeed(fromImport.getTrainMaxMaxSpeed())
                    .trainMaxSpeedVariance(fromImport.getTrainMaxSpeedVariance())
                    .flightEnabled(fromImport.getFlightEnabled())
                    .flightMinAvgSpeed(fromImport.getFlightMinAvgSpeed())
                    .flightMinMaxSpeed(fromImport.getFlightMinMaxSpeed())
                    .build();
        }
        
        // Both are non-null: create a copy of existing and merge import into it
        TimelinePreferences result = TimelinePreferences.builder()
                .useVelocityAccuracy(existing.getUseVelocityAccuracy())
                .staypointVelocityThreshold(existing.getStaypointVelocityThreshold())
                .staypointMaxAccuracyThreshold(existing.getStaypointMaxAccuracyThreshold())
                .staypointMinAccuracyRatio(existing.getStaypointMinAccuracyRatio())
                .tripDetectionAlgorithm(existing.getTripDetectionAlgorithm())
                .staypointRadiusMeters(existing.getStaypointRadiusMeters())
                .staypointMinDurationMinutes(existing.getStaypointMinDurationMinutes())
                .isMergeEnabled(existing.getIsMergeEnabled())
                .mergeMaxDistanceMeters(existing.getMergeMaxDistanceMeters())
                .mergeMaxTimeGapMinutes(existing.getMergeMaxTimeGapMinutes())
                .pathSimplificationEnabled(existing.getPathSimplificationEnabled())
                .pathSimplificationTolerance(existing.getPathSimplificationTolerance())
                .pathMaxPoints(existing.getPathMaxPoints())
                .pathAdaptiveSimplification(existing.getPathAdaptiveSimplification())
                .dataGapThresholdSeconds(existing.getDataGapThresholdSeconds())
                .dataGapMinDurationSeconds(existing.getDataGapMinDurationSeconds())
                .gapStayInferenceEnabled(existing.getGapStayInferenceEnabled())
                .gapStayInferenceMaxGapHours(existing.getGapStayInferenceMaxGapHours())
                .gapTripInferenceEnabled(existing.getGapTripInferenceEnabled())
                .gapTripInferenceMinDistanceMeters(existing.getGapTripInferenceMinDistanceMeters())
                .gapTripInferenceMinGapHours(existing.getGapTripInferenceMinGapHours())
                .gapTripInferenceMaxGapHours(existing.getGapTripInferenceMaxGapHours())
                .walkingMaxAvgSpeed(existing.getWalkingMaxAvgSpeed())
                .walkingMaxMaxSpeed(existing.getWalkingMaxMaxSpeed())
                .carMinAvgSpeed(existing.getCarMinAvgSpeed())
                .carMinMaxSpeed(existing.getCarMinMaxSpeed())
                .shortDistanceKm(existing.getShortDistanceKm())
                .tripArrivalDetectionMinDurationSeconds(existing.getTripArrivalDetectionMinDurationSeconds())
                .tripSustainedStopMinDurationSeconds(existing.getTripSustainedStopMinDurationSeconds())
                .tripArrivalMinPoints(existing.getTripArrivalMinPoints())
                // Optional Trip Types
                .bicycleEnabled(existing.getBicycleEnabled())
                .bicycleMinAvgSpeed(existing.getBicycleMinAvgSpeed())
                .bicycleMaxAvgSpeed(existing.getBicycleMaxAvgSpeed())
                .bicycleMaxMaxSpeed(existing.getBicycleMaxMaxSpeed())
                .runningEnabled(existing.getRunningEnabled())
                .runningMinAvgSpeed(existing.getRunningMinAvgSpeed())
                .runningMaxAvgSpeed(existing.getRunningMaxAvgSpeed())
                .runningMaxMaxSpeed(existing.getRunningMaxMaxSpeed())
                .trainEnabled(existing.getTrainEnabled())
                .trainMinAvgSpeed(existing.getTrainMinAvgSpeed())
                .trainMaxAvgSpeed(existing.getTrainMaxAvgSpeed())
                .trainMinMaxSpeed(existing.getTrainMinMaxSpeed())
                .trainMaxMaxSpeed(existing.getTrainMaxMaxSpeed())
                .trainMaxSpeedVariance(existing.getTrainMaxSpeedVariance())
                .flightEnabled(existing.getFlightEnabled())
                .flightMinAvgSpeed(existing.getFlightMinAvgSpeed())
                .flightMinMaxSpeed(existing.getFlightMinMaxSpeed())
                .build();
        
        // Use MapStruct to merge non-null import values into the result
        mergeImportIntoExisting(fromImport, result);
        return result;
    }
}
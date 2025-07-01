package org.github.tess1o.geopulse.user.mapper;

import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
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
    @org.mapstruct.Mapping(target = "staypointDetectionAlgorithm", 
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
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
    @org.mapstruct.Mapping(target = "tripMinDistanceMeters", 
                          nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @org.mapstruct.Mapping(target = "tripMinDurationMinutes", 
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
                    .staypointDetectionAlgorithm(fromImport.getStaypointDetectionAlgorithm())
                    .useVelocityAccuracy(fromImport.getUseVelocityAccuracy())
                    .staypointVelocityThreshold(fromImport.getStaypointVelocityThreshold())
                    .staypointMaxAccuracyThreshold(fromImport.getStaypointMaxAccuracyThreshold())
                    .staypointMinAccuracyRatio(fromImport.getStaypointMinAccuracyRatio())
                    .tripDetectionAlgorithm(fromImport.getTripDetectionAlgorithm())
                    .tripMinDistanceMeters(fromImport.getTripMinDistanceMeters())
                    .tripMinDurationMinutes(fromImport.getTripMinDurationMinutes())
                    .isMergeEnabled(fromImport.getIsMergeEnabled())
                    .mergeMaxDistanceMeters(fromImport.getMergeMaxDistanceMeters())
                    .mergeMaxTimeGapMinutes(fromImport.getMergeMaxTimeGapMinutes())
                    .pathSimplificationEnabled(fromImport.getPathSimplificationEnabled())
                    .pathSimplificationTolerance(fromImport.getPathSimplificationTolerance())
                    .pathMaxPoints(fromImport.getPathMaxPoints())
                    .pathAdaptiveSimplification(fromImport.getPathAdaptiveSimplification())
                    .build();
        }
        
        // Both are non-null: create a copy of existing and merge import into it
        TimelinePreferences result = TimelinePreferences.builder()
                .staypointDetectionAlgorithm(existing.getStaypointDetectionAlgorithm())
                .useVelocityAccuracy(existing.getUseVelocityAccuracy())
                .staypointVelocityThreshold(existing.getStaypointVelocityThreshold())
                .staypointMaxAccuracyThreshold(existing.getStaypointMaxAccuracyThreshold())
                .staypointMinAccuracyRatio(existing.getStaypointMinAccuracyRatio())
                .tripDetectionAlgorithm(existing.getTripDetectionAlgorithm())
                .tripMinDistanceMeters(existing.getTripMinDistanceMeters())
                .tripMinDurationMinutes(existing.getTripMinDurationMinutes())
                .isMergeEnabled(existing.getIsMergeEnabled())
                .mergeMaxDistanceMeters(existing.getMergeMaxDistanceMeters())
                .mergeMaxTimeGapMinutes(existing.getMergeMaxTimeGapMinutes())
                .pathSimplificationEnabled(existing.getPathSimplificationEnabled())
                .pathSimplificationTolerance(existing.getPathSimplificationTolerance())
                .pathMaxPoints(existing.getPathMaxPoints())
                .pathAdaptiveSimplification(existing.getPathAdaptiveSimplification())
                .build();
        
        // Use MapStruct to merge non-null import values into the result
        mergeImportIntoExisting(fromImport, result);
        return result;
    }
}
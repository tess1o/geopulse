package org.github.tess1o.geopulse.timeline.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the configuration for generating and managing movement timelines.
 *
 * This configuration is used to customize the detection of staypoints, filtering of track points,
 * and the merging of nearby locations or trips in the timeline.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class TimelineConfig {

    private String staypointDetectionAlgorithm;

    /**
     * Indicates whether velocity accuracy should be considered for filtering or processing data
     * in the timeline configuration.
     *
     * When set to true, the velocity accuracy of a `TrackPoint` is taken into account,
     * which can impact the accuracy and reliability of staypoint detection, trip identification,
     * and other operations within the timeline.
     *
     * This flag can help refine results in cases where velocity-based measurements
     * are important for determining movement patterns, but may be disabled to simplify
     * or optimize computations if velocity accuracy is not required.
     */
    private Boolean useVelocityAccuracy;
    /**
     * Determines the velocity threshold (in meters per second) used to identify staypoints.
     * A staypoint is detected when the velocity of movement falls below this threshold,
     * indicating that the user is likely stationary. Adjusting this value impacts how
     * sensitive the system is to detecting periods of low movement or stillness.
     */
    private Double staypointVelocityThreshold;
    /**
     * Defines the maximum accuracy threshold (in meters) for identifying staypoints.
     *
     * This value is used to filter out track points with poor GPS accuracy during
     * the staypoint detection process. Staypoints associated with GPS points that
     * have an accuracy greater than this threshold will be excluded, ensuring that
     * only reliable and precise data is used in the timeline generation.
     *
     * A lower value enforces stricter criteria for GPS accuracy, which may improve
     * the precision of staypoint detection at the cost of excluding more data.
     */
    private Double staypointMaxAccuracyThreshold;
    /**
     * Defines the minimum ratio of accuracy required for a track point to be considered
     * in staypoint detection. This ratio is evaluated as a fraction of the track point
     * accuracy compared to a larger accuracy threshold (e.g., {@code staypointMaxAccuracyThreshold}).
     *
     * A lower value allows more imprecise points to qualify, while a higher value enforces stricter
     * precision requirements.
     *
     * The parameter is used to filter track points in order to determine whether they contribute
     * to identifying staypoints, ensuring that staypoints are derived from high-confidence data.
     */
    private Double staypointMinAccuracyRatio;
    /**
     * Specifies the minimum distance, in meters, required for a trip to be considered valid.
     * Trips with a total distance less than this threshold will not be included in the timeline.
     *
     * This parameter is used to filter out very short trips that may not represent meaningful movement.
     */
    private Integer tripMinDistanceMeters;
    /**
     * Specifies the minimum duration (in minutes) for a trip to be considered valid.
     *
     * Trips with a duration shorter than this threshold will be ignored during timeline generation.
     * This parameter helps filter out very short trips that may not be significant or relevant
     * for analysis.
     */
    private Integer tripMinDurationMinutes;
    /**
     * Indicates whether merging is enabled for nearby locations or trips in the timeline.
     *
     * When set to true, the system attempts to merge geographically proximate stays or trips
     * that occur within a specified time gap. This helps to create a more concise and aggregated
     * representation of movement data by reducing fragmentation of closely related data points.
     */
    private Boolean isMergeEnabled;
    /**
     * Specifies the maximum distance in meters within which nearby locations or trips can be merged.
     *
     * This value is used to determine whether two locations or trips are close enough
     * in spatial terms to be combined into a single entity when merging is enabled in the timeline configuration.
     */
    private Integer mergeMaxDistanceMeters;
    /**
     * Specifies the maximum allowable time gap, in minutes, between two nearby locations or trips
     * to be merged into a single timeline item.
     *
     * This configuration is used to determine whether spatially close timeline entries
     * with a temporal gap smaller than the specified threshold should be combined.
     *
     * A smaller value results in stricter merging criteria, while a larger value allows
     * for more lenient merging of timeline segments.
     */
    private Integer mergeMaxTimeGapMinutes;

    private String tripDetectionAlgorithm;
    
    // GPS Path Simplification Configuration
    /**
     * Indicates whether GPS path simplification is enabled for timeline trips.
     * When enabled, trip paths will be simplified using the Douglas-Peucker algorithm
     * to reduce the number of GPS points while preserving route accuracy.
     */
    private Boolean pathSimplificationEnabled;
    
    /**
     * Base tolerance in meters for GPS path simplification.
     * Points closer than this distance to the simplified line will be removed.
     * Lower values preserve more detail, higher values provide more compression.
     */
    private Double pathSimplificationTolerance;
    
    /**
     * Maximum number of GPS points to retain in simplified paths.
     * If a simplified path still exceeds this limit, tolerance will be
     * automatically increased until the limit is met. Set to 0 for no limit.
     */
    private Integer pathMaxPoints;
    
    /**
     * Enables adaptive simplification that adjusts tolerance based on trip characteristics.
     * When enabled, longer trips use higher tolerance values for better compression
     * while shorter trips maintain higher accuracy with lower tolerance.
     */
    private Boolean pathAdaptiveSimplification;
    
    /**
     * Maximum time gap in seconds allowed between GPS points before considering it a data gap.
     * When the time difference between two consecutive GPS points exceeds this threshold,
     * a DataGap entity will be created instead of extending the current stay or trip.
     * This prevents artificial extension of activities during periods of missing GPS data.
     */
    private Integer dataGapThresholdSeconds;
    
    /**
     * Minimum duration in seconds for a gap to be recorded as a DataGap entity.
     * Gaps shorter than this threshold will be ignored to reduce noise.
     * This prevents very short connectivity issues from creating unnecessary gap records.
     */
    private Integer dataGapMinDurationSeconds;
}

package org.github.tess1o.geopulse.streaming.config;

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
     * Specifies the radius (in meters) used for stay point detection and clustering.
     * GPS points within this distance are considered part of the same stay location.
     * Also defines the minimum distance required between stays to create a trip.
     */
    private Integer staypointRadiusMeters;
    /**
     * Specifies the minimum duration (in minutes) for a stay point to be confirmed.
     * GPS point clusters must persist for at least this duration to be considered a confirmed stay.
     * Shorter clusters remain as potential stays and may be converted to trips if movement is detected.
     */
    private Integer staypointMinDurationMinutes;
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
    
    // Travel Classification Configuration
    /**
     * Maximum average speed in km/h to classify movement as walking.
     * Trips with average speeds below this threshold are likely walking trips.
     */
    private Double walkingMaxAvgSpeed;
    
    /**
     * Maximum peak speed in km/h to classify movement as walking.
     * Trips with maximum speeds below this threshold are likely walking trips.
     */
    private Double walkingMaxMaxSpeed;
    
    /**
     * Minimum average speed in km/h to classify movement as car travel.
     * Trips with average speeds above this threshold are likely car trips.
     */
    private Double carMinAvgSpeed;
    
    /**
     * Minimum peak speed in km/h to classify movement as car travel.
     * Trips with maximum speeds above this threshold are likely car trips.
     */
    private Double carMinMaxSpeed;
    
    /**
     * Distance threshold in km for applying relaxed walking speed classification.
     * Trips shorter than this distance get slightly more lenient walking speed
     * classification to account for GPS inaccuracies.
     */
    private Double shortDistanceKm;

    // Trip Stop Detection Configuration
    /**
     * Minimum duration in seconds for arrival detection during trips.
     * When GPS points are spatially clustered (within stay radius) and moving slowly
     * for at least this duration, it indicates arrival at a destination.
     * Lower values make arrival detection more sensitive, higher values more conservative.
     */
    private Integer tripArrivalDetectionMinDurationSeconds;

    /**
     * Minimum duration in seconds for sustained stop detection during trips.
     * When GPS points consistently show slow movement for at least this duration,
     * it indicates a sustained stop (not just a traffic light).
     * This helps filter out brief stops while detecting real arrivals.
     */
    private Integer tripSustainedStopMinDurationSeconds;

    // Optional Trip Types Configuration - Bicycle
    /**
     * Enable or disable bicycle trip type detection.
     * When disabled, trips in bicycle speed range (8-25 km/h) fall back to CAR or WALK classification.
     * Default: false (disabled)
     */
    private Boolean bicycleEnabled;

    /**
     * Minimum average speed in km/h to classify movement as bicycle.
     * Separates cycling from fast walking/jogging.
     * Default: 8.0 km/h
     */
    private Double bicycleMinAvgSpeed;

    /**
     * Maximum average speed in km/h to classify movement as bicycle.
     * Above this speed, trips are classified as CAR.
     * Default: 25.0 km/h (typical comfortable cycling speed)
     */
    private Double bicycleMaxAvgSpeed;

    /**
     * Maximum peak speed in km/h for bicycle classification.
     * Allows for downhill segments or e-bikes while staying below car speeds.
     * Default: 35.0 km/h
     */
    private Double bicycleMaxMaxSpeed;

    // Optional Trip Types Configuration - Train
    /**
     * Enable or disable train trip type detection.
     * When disabled, trips matching train criteria fall back to CAR classification.
     * Default: false (disabled)
     */
    private Boolean trainEnabled;

    /**
     * Minimum average speed in km/h to classify movement as train.
     * Separates train from cars in heavy traffic.
     * Default: 30.0 km/h
     */
    private Double trainMinAvgSpeed;

    /**
     * Maximum average speed in km/h to classify movement as train.
     * Covers regional and intercity trains.
     * Default: 150.0 km/h
     */
    private Double trainMaxAvgSpeed;

    /**
     * Minimum peak speed in km/h for train classification.
     * CRITICAL: Filters out trips with only station waiting time.
     * Ensures actual high-speed train movement was captured.
     * Default: 80.0 km/h
     */
    private Double trainMinMaxSpeed;

    /**
     * Maximum peak speed in km/h for train classification.
     * Upper limit for train speeds.
     * Default: 180.0 km/h
     */
    private Double trainMaxMaxSpeed;

    /**
     * Maximum speed variance for train classification.
     * KEY DISCRIMINATOR: Trains maintain steady speed, cars have stop-and-go patterns.
     * Low variance (< 15) indicates train, high variance (> 25) indicates car.
     * Default: 15.0
     */
    private Double trainMaxSpeedVariance;

    // Optional Trip Types Configuration - Flight
    /**
     * Enable or disable flight trip type detection.
     * When disabled, flights may be misclassified as CAR or UNKNOWN.
     * Default: false (disabled)
     */
    private Boolean flightEnabled;

    /**
     * Minimum average speed in km/h to classify movement as flight.
     * Average includes taxi, takeoff, landing, and cruise.
     * Default: 400.0 km/h
     */
    private Double flightMinAvgSpeed;

    /**
     * Minimum peak speed in km/h for flight classification.
     * CRITICAL: Catches flights with long taxi/ground time using OR logic.
     * Nothing else on earth reaches this speed.
     * Default: 500.0 km/h
     */
    private Double flightMinMaxSpeed;
}

package org.github.tess1o.geopulse.timeline.core;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.timeline.model.TrackPoint;
import org.github.tess1o.geopulse.timeline.util.TimelineConstants;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for centralized validation logic across timeline processing.
 * Provides consistent validation methods for timeline data, configuration, and processing parameters.
 */
@ApplicationScoped
@Slf4j
public class TimelineValidationService {

    /**
     * Validate timeline configuration parameters.
     *
     * @param config timeline configuration to validate
     * @throws IllegalArgumentException if configuration is invalid
     */
    public void validateTimelineConfig(TimelineConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Timeline configuration cannot be null");
        }

        log.debug("Validating timeline configuration");

        // Validate stay point detection parameters
        if (config.getStaypointVelocityThreshold() < 0) {
            throw new IllegalArgumentException("Stay point velocity threshold must be non-negative");
        }

        if (config.getStaypointMaxAccuracyThreshold() <= 0) {
            throw new IllegalArgumentException("Stay point max accuracy threshold must be positive");
        }

        if (config.getStaypointMinAccuracyRatio() < 0 || config.getStaypointMinAccuracyRatio() > 1) {
            throw new IllegalArgumentException("Stay point min accuracy ratio must be between 0 and 1");
        }

        // Validate trip detection parameters
        if (config.getTripMinDistanceMeters() < 0) {
            throw new IllegalArgumentException("Trip minimum distance must be non-negative");
        }

        if (config.getTripMinDurationMinutes() < 0) {
            throw new IllegalArgumentException("Trip minimum duration must be non-negative");
        }

        // Validate merging parameters
        if (config.getMergeMaxDistanceMeters() < 0) {
            throw new IllegalArgumentException("Merge max distance must be non-negative");
        }

        if (config.getMergeMaxTimeGapMinutes() < 0) {
            throw new IllegalArgumentException("Merge max time gap must be non-negative");
        }

        log.debug("Timeline configuration validation passed");
    }

    /**
     * Validate track points list for timeline processing.
     *
     * @param trackPoints list of track points to validate
     * @throws IllegalArgumentException if track points are invalid
     */
    public void validateTrackPoints(List<TrackPoint> trackPoints) {
        if (trackPoints == null) {
            throw new IllegalArgumentException("Track points list cannot be null");
        }

        if (trackPoints.isEmpty()) {
            log.debug("Track points list is empty - validation passed");
            return;
        }

        log.debug("Validating {} track points", trackPoints.size());

        for (int i = 0; i < trackPoints.size(); i++) {
            TrackPoint point = trackPoints.get(i);
            if (point == null) {
                throw new IllegalArgumentException("Track point at index " + i + " is null");
            }

            if (point.getTimestamp() == null) {
                throw new IllegalArgumentException("Track point at index " + i + " has null timestamp");
            }

            if (!isValidCoordinate(point.getLatitude(), point.getLongitude())) {
                throw new IllegalArgumentException("Track point at index " + i + " has invalid coordinates: " +
                        point.getLatitude() + ", " + point.getLongitude());
            }
        }

        log.debug("Track points validation passed");
    }

    /**
     * Validate user ID parameter.
     *
     * @param userId user identifier to validate
     * @throws IllegalArgumentException if user ID is invalid
     */
    public void validateUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        log.debug("Validating user ID: {}", userId);
    }

    /**
     * Validate time range parameters.
     *
     * @param startTime start of time range
     * @param endTime   end of time range
     * @throws IllegalArgumentException if time range is invalid
     */
    public void validateTimeRange(Instant startTime, Instant endTime) {
        if (startTime == null) {
            throw new IllegalArgumentException("Start time cannot be null");
        }

        if (endTime == null) {
            throw new IllegalArgumentException("End time cannot be null");
        }

        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("Start time cannot be after end time");
        }

        Duration duration = Duration.between(startTime, endTime);
        if (duration.toDays() > TimelineConstants.MAX_TIMELINE_DAYS) {
            throw new IllegalArgumentException("Time range cannot exceed " + TimelineConstants.MAX_TIMELINE_DAYS + " days");
        }

        log.debug("Time range validation passed: {} to {}", startTime, endTime);
    }

    /**
     * Check if a track point passes accuracy and velocity filters.
     *
     * @param point  track point to check
     * @param config timeline configuration with thresholds
     * @return true if point passes validation
     */
    public boolean passesAccuracyAndVelocityChecks(TrackPoint point, TimelineConfig config) {
        if (point == null || config == null) {
            return false;
        }

        // Check accuracy threshold
        if (point.getAccuracy() != null && point.getAccuracy() > config.getStaypointMaxAccuracyThreshold()) {
            return false;
        }

        // Check velocity threshold if enabled
        if (config.getUseVelocityAccuracy() && point.getVelocity() != null) {
            return point.getVelocity() <= config.getStaypointVelocityThreshold();
        }

        return true;
    }

    /**
     * Check if coordinates are valid (within valid latitude/longitude ranges).
     *
     * @param latitude  latitude value
     * @param longitude longitude value
     * @return true if coordinates are valid
     */
    public boolean isValidCoordinate(double latitude, double longitude) {
        return latitude >= -90.0 && latitude <= 90.0 &&
                longitude >= -180.0 && longitude <= 180.0;
    }

    /**
     * Check if a timestamp is within a given time range (inclusive).
     *
     * @param start  start time (inclusive)
     * @param end    end time (inclusive)
     * @param target timestamp to check
     * @return true if timestamp is within range
     */
    public boolean isBetweenInclusive(Instant start, Instant end, Instant target) {
        if (start == null || end == null || target == null) {
            return false;
        }

        return !target.isBefore(start) && !target.isAfter(end);
    }

    /**
     * Validate algorithm name parameter.
     *
     * @param algorithmName algorithm name to validate
     * @param allowedValues list of allowed algorithm names
     * @throws IllegalArgumentException if algorithm name is invalid
     */
    public void validateAlgorithmName(String algorithmName, List<String> allowedValues) {
        if (algorithmName == null || algorithmName.trim().isEmpty()) {
            throw new IllegalArgumentException("Algorithm name cannot be null or empty");
        }

        if (allowedValues != null && !allowedValues.contains(algorithmName.toLowerCase())) {
            throw new IllegalArgumentException("Unknown algorithm: " + algorithmName +
                    ". Allowed values: " + allowedValues);
        }

        log.debug("Algorithm name validation passed: {}", algorithmName);
    }

    /**
     * Check if input data is sufficient for reliable timeline processing.
     *
     * @param trackPoints list of track points
     * @param config      timeline configuration
     * @return true if data is sufficient for processing
     */
    public boolean hasSufficientDataForProcessing(List<TrackPoint> trackPoints, TimelineConfig config) {
        if (trackPoints == null || trackPoints.isEmpty()) {
            return false;
        }

        if (trackPoints.size() < TimelineConstants.MIN_POINTS_FOR_RELIABLE_DETECTION) {
            log.debug("Insufficient data: only {} points, need at least {}",
                    trackPoints.size(), TimelineConstants.MIN_POINTS_FOR_RELIABLE_DETECTION);
            return false;
        }

        // Check if we have enough accurate points
        long accuratePoints = trackPoints.stream()
                .filter(p -> p.getAccuracy() == null || p.getAccuracy() <= config.getStaypointMaxAccuracyThreshold())
                .count();

        if (accuratePoints < TimelineConstants.MIN_ACCURATE_POINTS_FOR_ANALYSIS) {
            log.debug("Insufficient accurate data: only {} accurate points, need at least {}",
                    accuratePoints, TimelineConstants.MIN_ACCURATE_POINTS_FOR_ANALYSIS);
            return false;
        }

        return true;
    }

    /**
     * Calculate accuracy ratio for a cluster of track points.
     * Returns the ratio of points with accuracy below the threshold.
     *
     * @param cluster              list of track points
     * @param maxAccuracyThreshold maximum allowed accuracy
     * @return ratio of accurate points (0.0 to 1.0)
     */
    public double calculateAccuracyRatio(List<TrackPoint> cluster, double maxAccuracyThreshold) {
        if (cluster == null || cluster.isEmpty()) {
            return 0.0;
        }

        long total = cluster.size();
        long accurateCount = cluster.stream()
                .filter(p -> p.getAccuracy() == null || p.getAccuracy() <= maxAccuracyThreshold)
                .count();

        return (double) accurateCount / total;
    }

    /**
     * Validate if a cluster meets the accuracy and velocity requirements.
     * This method validates a cluster of track points against configuration thresholds.
     *
     * @param cluster                 list of track points in the cluster
     * @param config                  timeline configuration with accuracy and velocity thresholds
     * @param velocityAnalysisService service for velocity calculations
     * @return true if cluster passes validation, false otherwise
     */
    public boolean passesAccuracyAndVelocityChecks(List<TrackPoint> cluster,
                                                   TimelineConfig config,
                                                   VelocityAnalysisService velocityAnalysisService) {
        if (cluster == null || cluster.isEmpty() || config == null) {
            return false;
        }

        if (!config.getUseVelocityAccuracy()) {
            return true; // Skip validation if not enabled
        }

        // Check accuracy ratio
        double accurateRatio = calculateAccuracyRatio(cluster, config.getStaypointMaxAccuracyThreshold());
        if (accurateRatio < config.getStaypointMinAccuracyRatio()) {
            log.debug("Cluster failed accuracy ratio check: {} < {}", accurateRatio, config.getStaypointMinAccuracyRatio());
            return false;
        }

        // Check median velocity
        double medianVelocity = velocityAnalysisService.calculateMedianVelocity(cluster);
        return medianVelocity < config.getStaypointVelocityThreshold();
    }
}
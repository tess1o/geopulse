package org.github.tess1o.geopulse.gps.service;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for detecting duplicate GPS points to prevent data duplication.
 */
@ApplicationScoped
@Slf4j
public class GpsPointDuplicateDetectionService {

    // Location tolerance for duplicate detection: ~11 meters
    private static final double LOCATION_TOLERANCE = 0.0001;

    private final GpsPointRepository gpsPointRepository;

    @ConfigProperty(name = "geopulse.gps.duplicate-detection.location-time-threshold-minutes", defaultValue = "2")
    @StaticInitSafe
    int locationTimeThresholdMinutes;

    @Inject
    public GpsPointDuplicateDetectionService(GpsPointRepository gpsPointRepository) {
        this.gpsPointRepository = gpsPointRepository;
    }

    /**
     * Check if a GPS point with the same timestamp and source type already exists for the user.
     * This prevents duplicate data ingestion from the same source.
     *
     * @param userId     The user ID
     * @param timestamp  The timestamp of the GPS point
     * @param sourceType The source type of the GPS point
     * @return true if a duplicate exists, false otherwise
     */
    public boolean isDuplicatePoint(UUID userId, Instant timestamp, GpsSourceType sourceType) {
        long existingCount = gpsPointRepository.findByUserIdAndTimePeriod(userId, timestamp, timestamp)
                .stream()
                .filter(point -> point.getSourceType() == sourceType)
                .count();

        boolean isDuplicate = existingCount > 0;
        if (isDuplicate) {
            log.warn("Duplicate GPS point detected for user {} at timestamp {} from source {}", 
                    userId, timestamp, sourceType);
        }

        return isDuplicate;
    }

    /**
     * Check if a GPS point is a location-based duplicate within the configured time threshold.
     * This prevents saving GPS points with the same coordinates when sent frequently from devices.
     *
     * <p>The method queries GPS points within a time window (timestamp ± threshold) and checks
     * if any point has the same location (within ~11 meter tolerance). This approach correctly
     * handles historical data insertion and allows disabling the check by setting threshold ≤ 0.
     *
     * @param userId The user ID
     * @param latitude The latitude of the new GPS point
     * @param longitude The longitude of the new GPS point
     * @param timestamp The timestamp of the new GPS point
     * @param sourceType The source type of the GPS point
     * @return true if this point should be skipped as a location-based duplicate, false otherwise
     */
    public boolean isLocationDuplicate(UUID userId, double latitude, double longitude, Instant timestamp, GpsSourceType sourceType) {
        // Early return: if threshold <= 0, duplicate detection is disabled
        if (locationTimeThresholdMinutes <= 0) {
            log.debug("Location duplicate detection disabled (threshold <= 0)");
            return false;
        }

        // Calculate time window: timestamp ± threshold
        Instant startTime = timestamp.minus(locationTimeThresholdMinutes, ChronoUnit.MINUTES);
        Instant endTime = timestamp.plus(locationTimeThresholdMinutes, ChronoUnit.MINUTES);

        // Fetch all GPS points within the time window
        List<GpsPointEntity> pointsInWindow = gpsPointRepository.findByUserIdAndTimePeriod(userId, startTime, endTime);

        // Filter by source type and check for location match
        boolean hasDuplicate = pointsInWindow.stream()
                .filter(point -> point.getSourceType() == sourceType)
                .anyMatch(point -> {
                    double latDiff = Math.abs(point.getLatitude() - latitude);
                    double lonDiff = Math.abs(point.getLongitude() - longitude);
                    return latDiff < LOCATION_TOLERANCE && lonDiff < LOCATION_TOLERANCE;
                });

        if (hasDuplicate) {
            log.debug("Location duplicate detected for user {}: same coordinates within {} minutes window, skipping",
                    userId, locationTimeThresholdMinutes);
        }

        return hasDuplicate;
    }
}
package org.github.tess1o.geopulse.gps.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Service responsible for detecting duplicate GPS points to prevent data duplication.
 */
@ApplicationScoped
@Slf4j
public class GpsPointDuplicateDetectionService {

    private final GpsPointRepository gpsPointRepository;
    
    @ConfigProperty(name = "geopulse.gps.duplicate-detection.location-time-threshold-minutes", defaultValue = "2")
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
     * @param userId The user ID
     * @param latitude The latitude of the new GPS point
     * @param longitude The longitude of the new GPS point  
     * @param timestamp The timestamp of the new GPS point
     * @param sourceType The source type of the GPS point
     * @return true if this point should be skipped as a location-based duplicate, false otherwise
     */
    public boolean isLocationDuplicate(UUID userId, double latitude, double longitude, Instant timestamp, GpsSourceType sourceType) {
        // Get the latest GPS point for this user
        Optional<GpsPointEntity> latestPoint = gpsPointRepository.findLatestByUserIdAndSourceType(userId, sourceType);
        
        if (latestPoint.isEmpty()) {
            log.debug("No previous GPS point found for user {}, allowing new point", userId);
            return false;
        }

        GpsPointEntity latest = latestPoint.get();
        
        // Check if coordinates are the same (with small tolerance for floating point comparison)
        double latDiff = Math.abs(latest.getLatitude() - latitude);
        double lonDiff = Math.abs(latest.getLongitude() - longitude);
        double tolerance = 0.0001; // ~11 meters tolerance
        
        boolean sameLocation = latDiff < tolerance && lonDiff < tolerance;
        
        if (!sameLocation) {
            log.debug("Location changed for user {}, allowing new point", userId);
            return false;
        }
        
        // Check time difference
        Duration timeDiff = Duration.between(latest.getTimestamp(), timestamp);
        long thresholdMinutes = locationTimeThresholdMinutes;
        
        boolean withinTimeThreshold = timeDiff.toMinutes() < thresholdMinutes;
        
        if (withinTimeThreshold) {
            log.debug("Location duplicate detected for user {}: same coordinates within {} minutes, skipping", 
                    userId, thresholdMinutes);
            return true;
        }
        
        log.debug("Location same but time threshold exceeded for user {}, allowing new point", userId);
        return false;
    }
}
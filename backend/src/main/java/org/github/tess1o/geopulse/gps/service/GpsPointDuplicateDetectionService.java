package org.github.tess1o.geopulse.gps.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.time.Instant;
import java.util.UUID;

/**
 * Service responsible for detecting duplicate GPS points to prevent data duplication.
 */
@ApplicationScoped
@Slf4j
public class GpsPointDuplicateDetectionService {

    private final GpsPointRepository gpsPointRepository;

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
}
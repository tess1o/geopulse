package org.github.tess1o.geopulse.gps.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.point.DawarichLocation;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.point.DawarichPayload;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.model.*;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.gps.integrations.overland.model.OverlandLocationMessage;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.user.model.UserEntity;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class GpsPointService {
    private final GpsPointMapper gpsPointMapper;
    private final GpsPointRepository gpsPointRepository;
    private final GpsPointDuplicateDetectionService duplicateDetectionService;
    private final EntityManager em;

    @Inject
    public GpsPointService(GpsPointMapper gpsPointMapper, GpsPointRepository gpsPointRepository,
                           GpsPointDuplicateDetectionService duplicateDetectionService, EntityManager em) {
        this.gpsPointMapper = gpsPointMapper;
        this.gpsPointRepository = gpsPointRepository;
        this.duplicateDetectionService = duplicateDetectionService;
        this.em = em;
    }

    @Transactional
    public void saveOwnTracksGpsPoint(OwnTracksLocationMessage message, UUID userId, String deviceId, GpsSourceType sourceType) {
        Instant timestamp = Instant.ofEpochSecond(message.getTst());

        // Check for location-based duplicates (same coordinates within time threshold)
        if (duplicateDetectionService.isLocationDuplicate(userId, message.getLat(), message.getLon(), timestamp, sourceType)) {
            log.info("Skipping location duplicate OwnTracks GPS point for user {} at coordinates ({}, {})",
                     userId, message.getLat(), message.getLon());
            return;
        }

        UserEntity user = em.getReference(UserEntity.class, userId);
        GpsPointEntity entity = gpsPointMapper.toEntity(message, user, deviceId, sourceType);
        gpsPointRepository.persist(entity);
        log.info("Saved OwnTracks GPS point for user {} at timestamp {}", userId, timestamp);
    }

    @Transactional
    public void saveOverlandGpsPoint(OverlandLocationMessage message, UUID userId, GpsSourceType sourceType) {
        Instant timestamp = message.getProperties().getTimestamp();

        if (duplicateDetectionService.isDuplicatePoint(userId, timestamp, sourceType)) {
            log.info("Skipping duplicate Overland GPS point for user {} at timestamp {}", userId, timestamp);
            return;
        }

        UserEntity user = em.getReference(UserEntity.class, userId);
        GpsPointEntity entity = gpsPointMapper.toEntity(message, user, sourceType);
        gpsPointRepository.persist(entity);
        log.info("Saved Overland GPS point for user {} at timestamp {}", userId, timestamp);
    }

    @Transactional
    public void saveDarawichGpsPoints(DawarichPayload payload, UUID userId, GpsSourceType sourceType) {
        UserEntity user = em.getReference(UserEntity.class, userId);
        for (DawarichLocation location : payload.getLocations()) {
            if (duplicateDetectionService.isDuplicatePoint(userId, location.getProperties().getTimestamp(), sourceType)) {
                log.info("Skipping duplicate Overland GPS point for user {} at timestamp {}", userId, location.getProperties().getTimestamp());
                return;
            }

            // avoid negative velocity, use 0 to say it's a stationary value
            if (location.getProperties().getSpeed() < 0) {
                location.getProperties().setSpeed(0.0);
            }

            GpsPointEntity entity = gpsPointMapper.toEntity(location, user, sourceType);
            gpsPointRepository.persist(entity);
            log.info("Saved Dawarich GPS point for user {} at timestamp {}", userId, entity.getTimestamp());
        }
    }

    /**
     * Get a GPS point path for a user within a specified time period.
     * This method retrieves individual GPS points and constructs a path from them.
     *
     * @param userId    The ID of the user
     * @param startTime The start of the time period
     * @param endTime   The end of the time period
     * @return A GpsPointPathDTO containing the path points
     */
    public GpsPointPathDTO getGpsPointPath(UUID userId, Instant startTime, Instant endTime) {
        List<GpsPointEntity> gpsPoints = gpsPointRepository.findByUserIdAndTimePeriod(userId, startTime, endTime);
        List<GpsPointPathPointDTO> pathPoints = gpsPointMapper.toPathPoints(gpsPoints);

        return new GpsPointPathDTO(userId, pathPoints);
    }

    /**
     * Get summary statistics for GPS points for a user.
     *
     * @param userId The ID of the user
     * @return Summary statistics
     */
    public GpsPointSummaryDTO getGpsPointSummary(UUID userId) {
        long totalPoints = gpsPointRepository.count("user.id", userId);

        Instant todayStart = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        Instant todayEnd = todayStart.plus(1, java.time.temporal.ChronoUnit.DAYS);
        long pointsToday = gpsPointRepository.count("user.id = ?1 AND timestamp >= ?2 AND timestamp < ?3",
                userId, todayStart, todayEnd);

        GpsPointEntity firstPoint = gpsPointRepository.find("user.id = ?1 ORDER BY timestamp ASC", userId).firstResult();
        GpsPointEntity lastPoint = gpsPointRepository.find("user.id = ?1 ORDER BY timestamp DESC", userId).firstResult();

        String firstPointDate = firstPoint != null ? DateTimeFormatter.ISO_DATE_TIME.format(firstPoint.getTimestamp().atOffset(ZoneOffset.UTC)) : null;
        String lastPointDate = lastPoint != null ? DateTimeFormatter.ISO_DATE_TIME.format(lastPoint.getTimestamp().atOffset(ZoneOffset.UTC)) : null;

        return new GpsPointSummaryDTO(totalPoints, pointsToday, firstPointDate, lastPointDate);
    }

    /**
     * Get paginated GPS points for a user within a time period.
     *
     * @param userId    The ID of the user
     * @param startTime The start of the time period
     * @param endTime   The end of the time period
     * @param page      Page number (1-based)
     * @param limit     Number of items per page
     * @return Paginated GPS points
     */
    public GpsPointPageDTO getGpsPointsPage(UUID userId, Instant startTime, Instant endTime, int page, int limit) {
        int pageIndex = page - 1; // Convert to 0-based for repository

        List<GpsPointEntity> points = gpsPointRepository.findByUserAndDateRange(userId, startTime, endTime, pageIndex, limit);
        long total = gpsPointRepository.count("user.id = ?1 AND timestamp >= ?2 AND timestamp <= ?3",
                userId, startTime, endTime);

        List<GpsPointDTO> pointDTOs = gpsPointMapper.toGpsPointDTOs(points);

        long totalPages = (total + limit - 1) / limit; // Ceiling division
        GpsPointPaginationDTO pagination = new GpsPointPaginationDTO(page, limit, total, totalPages);

        return new GpsPointPageDTO(pointDTOs, pagination);
    }

    /**
     * Get all GPS points for a user within a time period for export.
     *
     * @param userId    The ID of the user
     * @param startTime The start of the time period
     * @param endTime   The end of the time period
     * @return List of GPS points for export
     */
    public List<GpsPointEntity> getGpsPointsForExport(UUID userId, Instant startTime, Instant endTime) {
        return gpsPointRepository.findByUserIdAndTimePeriod(userId, startTime, endTime);
    }
}
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
import org.github.tess1o.geopulse.timeline.service.TimelineBackgroundService;
import org.github.tess1o.geopulse.timeline.service.TimelineCacheService;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class GpsPointService {
    private final GpsPointMapper gpsPointMapper;
    private final GpsPointRepository gpsPointRepository;
    private final GpsPointDuplicateDetectionService duplicateDetectionService;
    private final EntityManager em;
    private final TimelineBackgroundService timelineBackgroundService;
    private final TimelineCacheService timelineCacheService;

    @Inject
    public GpsPointService(GpsPointMapper gpsPointMapper, GpsPointRepository gpsPointRepository,
                           GpsPointDuplicateDetectionService duplicateDetectionService, EntityManager em,
                           TimelineBackgroundService timelineBackgroundService, TimelineCacheService timelineCacheService) {
        this.gpsPointMapper = gpsPointMapper;
        this.gpsPointRepository = gpsPointRepository;
        this.duplicateDetectionService = duplicateDetectionService;
        this.em = em;
        this.timelineBackgroundService = timelineBackgroundService;
        this.timelineCacheService = timelineCacheService;
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

    /**
     * Update a GPS point for a user.
     * This method allows updating location (latitude/longitude), speed, and accuracy.
     * 
     * @param pointId The ID of the GPS point to update
     * @param dto The update data
     * @param userId The ID of the user (for security check)
     * @return Updated GPS point DTO
     */
    @Transactional
    public GpsPointDTO updateGpsPoint(Long pointId, EditGpsPointDto dto, UUID userId) {
        log.info("Updating GPS point {} for user {}", pointId, userId);
        
        // Find the GPS point and verify ownership
        Optional<GpsPointEntity> optionalPoint = gpsPointRepository.findByIdOptional(pointId);
        if (optionalPoint.isEmpty()) {
            throw new NotFoundException("GPS point not found with ID: " + pointId);
        }
        
        GpsPointEntity gpsPoint = optionalPoint.get();
        if (!gpsPoint.getUser().getId().equals(userId)) {
            throw new ForbiddenException("GPS point does not belong to the user");
        }
        
        // Store original date for timeline recalculation check
        LocalDate originalDate = gpsPoint.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate();
        
        // Update the GPS point
        gpsPoint.setCoordinates(GeoUtils.createPoint(dto.getCoordinates().getLng(), dto.getCoordinates().getLat()));
        gpsPoint.setVelocity(dto.getVelocity());
        gpsPoint.setAccuracy(dto.getAccuracy());
        
        gpsPointRepository.persist(gpsPoint);
        
        // Check if timeline recalculation is needed
        triggerTimelineRecalculationIfNeeded(userId, originalDate);
        
        // Return updated point as DTO
        return gpsPointMapper.toGpsPointDTO(gpsPoint);
    }

    /**
     * Delete a GPS point for a user.
     * 
     * @param pointId The ID of the GPS point to delete
     * @param userId The ID of the user (for security check)
     */
    @Transactional
    public void deleteGpsPoint(Long pointId, UUID userId) {
        log.info("Deleting GPS point {} for user {}", pointId, userId);
        
        // Find the GPS point and verify ownership
        Optional<GpsPointEntity> optionalPoint = gpsPointRepository.findByIdOptional(pointId);
        if (optionalPoint.isEmpty()) {
            throw new NotFoundException("GPS point not found with ID: " + pointId);
        }
        
        GpsPointEntity gpsPoint = optionalPoint.get();
        if (!gpsPoint.getUser().getId().equals(userId)) {
            throw new ForbiddenException("GPS point does not belong to the user");
        }
        
        // Store date for timeline recalculation check
        LocalDate pointDate = gpsPoint.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate();
        
        // Delete the GPS point
        gpsPointRepository.delete(gpsPoint);
        
        // Check if timeline recalculation is needed
        triggerTimelineRecalculationIfNeeded(userId, pointDate);
    }

    /**
     * Delete multiple GPS points for a user.
     * 
     * @param pointIds List of GPS point IDs to delete
     * @param userId The ID of the user (for security check)
     * @return Number of points deleted
     */
    @Transactional
    public int deleteGpsPoints(List<Long> pointIds, UUID userId) {
        log.info("Deleting {} GPS points for user {}", pointIds.size(), userId);
        
        if (pointIds.isEmpty()) {
            return 0;
        }
        
        // Find all GPS points and verify ownership
        List<GpsPointEntity> gpsPoints = gpsPointRepository.list("id in ?1", pointIds);
        
        // Verify all points belong to the user
        for (GpsPointEntity point : gpsPoints) {
            if (!point.getUser().getId().equals(userId)) {
                throw new ForbiddenException("One or more GPS points do not belong to the user");
            }
        }
        
        // Collect unique dates for timeline recalculation
        List<LocalDate> affectedDates = gpsPoints.stream()
            .map(point -> point.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate())
            .distinct()
            .toList();
        
        // Delete all points
        int deletedCount = 0;
        for (GpsPointEntity point : gpsPoints) {
            gpsPointRepository.delete(point);
            deletedCount++;
        }
        
        // Trigger timeline recalculation for all affected dates
        triggerTimelineRecalculationIfNeeded(userId, affectedDates);
        
        log.info("Successfully deleted {} GPS points for user {}", deletedCount, userId);
        return deletedCount;
    }

    /**
     * Check if timeline recalculation is needed for a specific date.
     * If the date is "today", no recalculation is needed (live calculation).
     * If the date is in the past, trigger high-priority timeline regeneration.
     */
    private void triggerTimelineRecalculationIfNeeded(UUID userId, LocalDate affectedDate) {
        triggerTimelineRecalculationIfNeeded(userId, List.of(affectedDate));
    }

    /**
     * Check if timeline recalculation is needed for multiple dates.
     * If any date is in the past, trigger high-priority timeline regeneration.
     */
    private void triggerTimelineRecalculationIfNeeded(UUID userId, List<LocalDate> affectedDates) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        
        // Filter out today's date (no recalculation needed for live data)
        List<LocalDate> pastDates = affectedDates.stream()
            .filter(date -> date.isBefore(today))
            .distinct()
            .toList();
        
        if (!pastDates.isEmpty()) {
            log.info("GPS point changes affect past dates: {}. Triggering timeline recalculation for user {}", 
                     pastDates, userId);
            
            // Clear existing timeline cache for affected dates
            timelineCacheService.delete(userId, pastDates);
            
            // Queue high-priority timeline regeneration
            timelineBackgroundService.queueHighPriorityRegeneration(userId, pastDates);
        } else {
            log.debug("GPS point changes only affect today's data. No timeline recalculation needed.");
        }
    }
}
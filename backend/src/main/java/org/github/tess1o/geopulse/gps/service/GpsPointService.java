package org.github.tess1o.geopulse.gps.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.point.DawarichLocation;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.point.DawarichPayload;
import org.github.tess1o.geopulse.gps.integrations.homeassistant.model.HomeAssistantGpsData;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.model.*;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gps.service.filter.GpsDataFilteringService;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.gps.integrations.overland.model.OverlandLocationMessage;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.time.*;
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
    private final StreamingTimelineGenerationService streamingTimelineGenerationService;
    private final GpsDataFilteringService filteringService;

    @Inject
    public GpsPointService(GpsPointMapper gpsPointMapper, GpsPointRepository gpsPointRepository,
                           GpsPointDuplicateDetectionService duplicateDetectionService, EntityManager em,
                           StreamingTimelineGenerationService streamingTimelineGenerationService,
                           GpsDataFilteringService filteringService) {
        this.gpsPointMapper = gpsPointMapper;
        this.gpsPointRepository = gpsPointRepository;
        this.duplicateDetectionService = duplicateDetectionService;
        this.em = em;
        this.streamingTimelineGenerationService = streamingTimelineGenerationService;
        this.filteringService = filteringService;
    }

    /**
     * Common logic for filtering and persisting a GPS point entity.
     * This method should be called after entity mapping.
     *
     * @param entity The mapped GPS point entity to filter and persist
     * @param config The GPS source configuration containing filter settings
     * @return true if the point was saved, false if rejected by filters
     */
    private boolean filterAndPersistGpsPoint(GpsPointEntity entity, GpsSourceConfigEntity config) {
        var filterResult = filteringService.filter(entity, config);
        if (filterResult.isRejected()) {
            // Already logged in filtering service
            return false;
        }

        // Check for existing point with the same unique key
        Optional<GpsPointEntity> existingPoint = gpsPointRepository.findByUniqueKey(
                entity.getUser().getId(),
                entity.getTimestamp(),
                entity.getCoordinates()
        );

        if (existingPoint.isPresent()) {
            // It's a duplicate, reject it
            log.info("Skipping duplicate GPS point for user {} at timestamp {} with same coordinates", entity.getUser().getId(), entity.getTimestamp());
            return false;
        } else {
            // Persist the new entity
            gpsPointRepository.persist(entity);
            log.info("Saved {} GPS point for user {} at timestamp {}", entity.getSourceType(), entity.getUser().getId(), entity.getTimestamp());
            return true;
        }
    }

    @Transactional
    public void saveOwnTracksGpsPoint(OwnTracksLocationMessage message, UUID userId, String deviceId, GpsSourceType sourceType, GpsSourceConfigEntity config) {
        Instant timestamp = Instant.ofEpochSecond(message.getTst());

        // Check for location-based duplicates first (before creating entity)
        if (duplicateDetectionService.isLocationDuplicate(userId, message.getLat(), message.getLon(), timestamp, sourceType)) {
            log.info("Skipping location duplicate OwnTracks GPS point for user {} at coordinates ({}, {})",
                    userId, message.getLat(), message.getLon());
            return;
        }

        // Map message to entity (mapper handles unit conversions)
        UserEntity user = em.getReference(UserEntity.class, userId);
        GpsPointEntity entity = gpsPointMapper.toEntity(message, user, deviceId, sourceType);

        // Filter and persist
        filterAndPersistGpsPoint(entity, config);
    }

    @Transactional
    public void saveOverlandGpsPoint(OverlandLocationMessage message, UUID userId, GpsSourceType sourceType, GpsSourceConfigEntity config) {
        Instant timestamp = message.getProperties().getTimestamp();

        // Check for duplicates first
        if (duplicateDetectionService.isDuplicatePoint(userId, timestamp, sourceType)) {
            log.info("Skipping duplicate Overland GPS point for user {} at timestamp {}", userId, timestamp);
            return;
        }

        // Map message to entity (mapper handles m/s → km/h conversion)
        UserEntity user = em.getReference(UserEntity.class, userId);
        GpsPointEntity entity = gpsPointMapper.toEntity(message, user, sourceType);

        // Filter and persist
        filterAndPersistGpsPoint(entity, config);
    }

    @Transactional
    public void saveDarawichGpsPoints(DawarichPayload payload, UUID userId, GpsSourceType sourceType, GpsSourceConfigEntity config) {
        UserEntity user = em.getReference(UserEntity.class, userId);
        for (DawarichLocation location : payload.getLocations()) {
            // Check for duplicates first
            if (duplicateDetectionService.isDuplicatePoint(userId, location.getProperties().getTimestamp(), sourceType)) {
                log.info("Skipping duplicate Dawarich GPS point for user {} at timestamp {}", userId, location.getProperties().getTimestamp());
                continue;
            }

            // Avoid negative velocity - use 0 to say it's a stationary value
            if (location.getProperties().getSpeed() != null && location.getProperties().getSpeed() < 0) {
                location.getProperties().setSpeed(0.0);
            }

            // Map message to entity (mapper handles m/s → km/h conversion)
            GpsPointEntity entity = gpsPointMapper.toEntity(location, user, sourceType);

            // Filter and persist
            filterAndPersistGpsPoint(entity, config);
        }
    }

    @Transactional
    public void saveHomeAssitantGpsPoint(HomeAssistantGpsData data, UUID userId, GpsSourceType sourceType, GpsSourceConfigEntity config) {
        Instant timestamp = data.getTimestamp();

        // Check for duplicates first
        if (duplicateDetectionService.isDuplicatePoint(userId, timestamp, sourceType)) {
            log.info("Skipping duplicate Home Assistant GPS point for user {} at timestamp {}", userId, timestamp);
            return;
        }

        // Map message to entity (mapper handles m/s → km/h conversion)
        UserEntity user = em.getReference(UserEntity.class, userId);
        GpsPointEntity entity = gpsPointMapper.toEntity(data, user, sourceType);

        // Filter and persist
        filterAndPersistGpsPoint(entity, config);
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
        return getGpsPointSummary(userId, ZoneId.of("UTC"));
    }

    /**
     * Get summary statistics for GPS points for a user with timezone support.
     *
     * @param userId       The ID of the user
     * @param userTimezone The user's timezone for calculating "today"
     * @return Summary statistics
     */
    public GpsPointSummaryDTO getGpsPointSummary(UUID userId, ZoneId userTimezone) {
        // Calculate "today" in the user's timezone, not UTC
        ZonedDateTime nowInUserTz = ZonedDateTime.now(userTimezone);
        ZonedDateTime todayStartInUserTz = nowInUserTz.toLocalDate().atStartOfDay(userTimezone);
        ZonedDateTime todayEndInUserTz = todayStartInUserTz.plusDays(1);

        // Convert to UTC for database query (since timestamps are stored in UTC)
        Instant todayStart = todayStartInUserTz.toInstant();
        Instant todayEnd = todayEndInUserTz.toInstant();

        // Get all summary data in a single optimized query
        Object[] summaryData = gpsPointRepository.getGpsPointSummaryData(userId, todayStart, todayEnd);


        long totalPoints = ((Number) summaryData[0]).longValue();
        long pointsToday = ((Number) summaryData[1]).longValue();
        Instant firstTimestamp = getInstantSafe(summaryData[2]);
        Instant lastTimestamp = getInstantSafe(summaryData[3]);

        return new GpsPointSummaryDTO(totalPoints, pointsToday, firstTimestamp, lastTimestamp);
    }

    private static Instant getInstantSafe(Object date) {
        if (date == null) return null;
        if (date instanceof Instant) return (Instant) date;
        if (date instanceof java.sql.Timestamp) {
            // Database timestamps are stored in UTC, so treat them as UTC
            java.sql.Timestamp ts = (java.sql.Timestamp) date;
            return ts.toLocalDateTime().toInstant(ZoneOffset.UTC);
        }
        if (date instanceof java.util.Date) return ((java.util.Date) date).toInstant();
        if (date instanceof Long) return Instant.ofEpochMilli((Long) date);
        if (date instanceof LocalDateTime) return ((LocalDateTime) date).toInstant(ZoneOffset.UTC);
        if (date instanceof String) {
            try {
                return Instant.parse((String) date);
            } catch (Exception e) {
                log.warn("Failed to parse timestamp string: {}", date);
            }
        }
        log.warn("Unsupported timestamp type: {} with value: {}", date.getClass().getName(), date);
        return null;
    }


    /**
     * Get paginated GPS points for a user within a time period with sorting.
     *
     * @param userId    The ID of the user
     * @param startTime The start of the time period
     * @param endTime   The end of the time period
     * @param page      Page number (1-based)
     * @param limit     Number of items per page
     * @param sortBy    Field to sort by
     * @param sortOrder Sort order (asc or desc)
     * @return Paginated GPS points
     */
    public GpsPointPageDTO getGpsPointsPage(UUID userId, Instant startTime, Instant endTime,
                                            int page, int limit, String sortBy, String sortOrder) {
        int pageIndex = page - 1; // Convert to 0-based for repository

        List<GpsPointEntity> points = gpsPointRepository.findByUserAndDateRange(userId, startTime, endTime,
                pageIndex, limit, sortBy, sortOrder);
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
     * @param dto     The update data
     * @param userId  The ID of the user (for security check)
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

        // Store original timestamp for timeline recalculation
        Instant originalTimestamp = gpsPoint.getTimestamp();

        // Update the GPS point
        gpsPoint.setCoordinates(GeoUtils.createPoint(dto.getCoordinates().getLng(), dto.getCoordinates().getLat()));
        gpsPoint.setVelocity(dto.getVelocity());
        gpsPoint.setAccuracy(dto.getAccuracy());

        gpsPointRepository.persist(gpsPoint);

        // Trigger synchronous timeline regeneration if needed
        triggerSynchronousTimelineRegenerationIfNeeded(userId, originalTimestamp);

        // Return updated point as DTO
        return gpsPointMapper.toGpsPointDTO(gpsPoint);
    }

    /**
     * Delete a GPS point for a user.
     *
     * @param pointId The ID of the GPS point to delete
     * @param userId  The ID of the user (for security check)
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

        // Store timestamp for timeline recalculation
        Instant pointTimestamp = gpsPoint.getTimestamp();

        // Delete the GPS point
        gpsPointRepository.delete(gpsPoint);

        // Trigger synchronous timeline regeneration if needed
        triggerSynchronousTimelineRegenerationIfNeeded(userId, pointTimestamp);
    }

    /**
     * Delete multiple GPS points for a user.
     *
     * @param pointIds List of GPS point IDs to delete
     * @param userId   The ID of the user (for security check)
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

        // Find the earliest timestamp among all points to be deleted
        Instant earliestTimestamp = gpsPoints.stream()
                .map(GpsPointEntity::getTimestamp)
                .min(Instant::compareTo)
                .orElse(null);

        // Delete all points
        int deletedCount = 0;
        for (GpsPointEntity point : gpsPoints) {
            gpsPointRepository.delete(point);
            deletedCount++;
        }

        // Trigger synchronous timeline regeneration if needed
        if (earliestTimestamp != null) {
            triggerSynchronousTimelineRegenerationIfNeeded(userId, earliestTimestamp);
        }

        log.info("Successfully deleted {} GPS points for user {}", deletedCount, userId);
        return deletedCount;
    }

    /**
     * Trigger synchronous timeline regeneration if needed.
     * If the affected timestamp is in the past (not today), regenerate timeline immediately.
     * For today's data, timeline is handled by live processing.
     */
    private void triggerSynchronousTimelineRegenerationIfNeeded(UUID userId, Instant affectedTimestamp) {
        streamingTimelineGenerationService.generateTimelineFromTimestamp(userId, affectedTimestamp);
    }
}
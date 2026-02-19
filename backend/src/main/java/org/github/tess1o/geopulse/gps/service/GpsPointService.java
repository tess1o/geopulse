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
import org.github.tess1o.geopulse.gps.model.GpsPointFilterDTO;
import org.github.tess1o.geopulse.gps.service.filter.GpsDataFilteringService;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.gps.integrations.overland.model.OverlandLocationMessage;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@ApplicationScoped
@Slf4j
public class GpsPointService {
    private final GpsPointMapper gpsPointMapper;
    private final GpsPointRepository gpsPointRepository;
    private final GpsPointDuplicateDetectionService duplicateDetectionService;
    private final EntityManager em;
    private final StreamingTimelineGenerationService streamingTimelineGenerationService;
    private final GpsDataFilteringService filteringService;

    @ConfigProperty(name = "geopulse.gps.duplicate-detection.location-time-threshold-minutes", defaultValue = "2")
    int globalDuplicateDetectionThresholdMinutes;

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

        // Check for location-based duplicates first (before creating entity) if enabled
        if (config.isEnableDuplicateDetection()) {
            // Use per-source threshold if set, otherwise fall back to global config
            int threshold = config.getDuplicateDetectionThresholdMinutes() != null
                ? config.getDuplicateDetectionThresholdMinutes()
                : globalDuplicateDetectionThresholdMinutes;

            if (duplicateDetectionService.isLocationDuplicate(userId, message.getLat(), message.getLon(), timestamp, sourceType, threshold)) {
                log.info("Skipping OwnTracks GPS point for user {} at coordinates ({}, {}): duplicate location detected within {} minutes window",
                        userId, message.getLat(), message.getLon(), threshold);
                return;
            }
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

        // Check for location-based duplicates if enabled, otherwise use exact timestamp check
        if (config.isEnableDuplicateDetection()) {
            // Use per-source threshold if set, otherwise fall back to global config
            int threshold = config.getDuplicateDetectionThresholdMinutes() != null
                ? config.getDuplicateDetectionThresholdMinutes()
                : globalDuplicateDetectionThresholdMinutes;

            double lon = message.getGeometry().getCoordinates()[0];
            double lat = message.getGeometry().getCoordinates()[1];

            if (duplicateDetectionService.isLocationDuplicate(userId, lat, lon, timestamp, sourceType, threshold)) {
                log.info("Skipping Overland GPS point for user {} at coordinates ({}, {}): duplicate location detected within {} minutes window",
                        userId, lat, lon, threshold);
                return;
            }
        } else {
            // Fallback to exact timestamp duplicate check
            if (duplicateDetectionService.isDuplicatePoint(userId, timestamp, sourceType)) {
                log.info("Skipping duplicate Overland GPS point for user {} at timestamp {}", userId, timestamp);
                return;
            }
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

        // Pre-calculate threshold if duplicate detection is enabled
        Integer threshold = null;
        if (config.isEnableDuplicateDetection()) {
            threshold = config.getDuplicateDetectionThresholdMinutes() != null
                ? config.getDuplicateDetectionThresholdMinutes()
                : globalDuplicateDetectionThresholdMinutes;
        }

        for (DawarichLocation location : payload.getLocations()) {
            Instant timestamp = location.getProperties().getTimestamp();
            double lon = location.getGeometry().getCoordinates().get(0);
            double lat = location.getGeometry().getCoordinates().get(1);

            // Check for location-based duplicates if enabled, otherwise use exact timestamp check
            if (config.isEnableDuplicateDetection()) {
                if (duplicateDetectionService.isLocationDuplicate(userId, lat, lon, timestamp, sourceType, threshold)) {
                    log.info("Skipping Dawarich GPS point for user {} at coordinates ({}, {}): duplicate location detected within {} minutes window",
                            userId, lat, lon, threshold);
                    continue;
                }
            } else {
                // Fallback to exact timestamp duplicate check
                if (duplicateDetectionService.isDuplicatePoint(userId, timestamp, sourceType)) {
                    log.info("Skipping duplicate Dawarich GPS point for user {} at timestamp {}", userId, timestamp);
                    continue;
                }
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

        // Check for location-based duplicates if enabled, otherwise use exact timestamp check
        if (config.isEnableDuplicateDetection()) {
            // Use per-source threshold if set, otherwise fall back to global config
            int threshold = config.getDuplicateDetectionThresholdMinutes() != null
                ? config.getDuplicateDetectionThresholdMinutes()
                : globalDuplicateDetectionThresholdMinutes;

            double lat = data.getLocation().getLatitude();
            double lon = data.getLocation().getLongitude();

            if (duplicateDetectionService.isLocationDuplicate(userId, lat, lon, timestamp, sourceType, threshold)) {
                log.info("Skipping Home Assistant GPS point for user {} at coordinates ({}, {}): duplicate location detected within {} minutes window",
                        userId, lat, lon, threshold);
                return;
            }
        } else {
            // Fallback to exact timestamp duplicate check
            if (duplicateDetectionService.isDuplicatePoint(userId, timestamp, sourceType)) {
                log.info("Skipping duplicate Home Assistant GPS point for user {} at timestamp {}", userId, timestamp);
                return;
            }
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
        Instant firstTimestamp = TimestampUtils.getInstantSafe(summaryData[2]);
        Instant lastTimestamp = TimestampUtils.getInstantSafe(summaryData[3]);

        return new GpsPointSummaryDTO(totalPoints, pointsToday, firstTimestamp, lastTimestamp, null);
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

    public Optional<GpsPointDTO> getLastKnownPosition(UUID userId) {
        return gpsPointRepository.findLatest(userId).map(gpsPointMapper::toGpsPointDTO);
    }

    /**
     * Get paginated GPS points with filters.
     *
     * @param userId    The ID of the user
     * @param filters   Filter criteria
     * @param page      Page number (1-based)
     * @param limit     Number of items per page
     * @param sortBy    Field to sort by
     * @param sortOrder Sort order (asc or desc)
     * @return Paginated GPS points
     */
    public GpsPointPageDTO getGpsPointsPageWithFilters(UUID userId, GpsPointFilterDTO filters,
                                                        int page, int limit, String sortBy, String sortOrder) {
        int pageIndex = page - 1; // Convert to 0-based for repository

        List<GpsPointEntity> points = gpsPointRepository.findByUserAndFilters(userId, filters,
                pageIndex, limit, sortBy, sortOrder);
        long total = gpsPointRepository.countByUserAndFilters(userId, filters);

        List<GpsPointDTO> pointDTOs = gpsPointMapper.toGpsPointDTOs(points);

        long totalPages = (total + limit - 1) / limit; // Ceiling division
        GpsPointPaginationDTO pagination = new GpsPointPaginationDTO(page, limit, total, totalPages);

        return new GpsPointPageDTO(pointDTOs, pagination);
    }

    /**
     * Get summary statistics with optional filters.
     *
     * @param userId       The ID of the user
     * @param userTimezone The user's timezone for calculating "today"
     * @param filters      Optional filter criteria
     * @return Summary statistics
     */
    public GpsPointSummaryDTO getGpsPointSummaryWithFilters(UUID userId, ZoneId userTimezone, GpsPointFilterDTO filters) {
        // Calculate "today" in the user's timezone
        ZonedDateTime nowInUserTz = ZonedDateTime.now(userTimezone);
        ZonedDateTime todayStartInUserTz = nowInUserTz.toLocalDate().atStartOfDay(userTimezone);
        ZonedDateTime todayEndInUserTz = todayStartInUserTz.plusDays(1);

        Instant todayStart = todayStartInUserTz.toInstant();
        Instant todayEnd = todayEndInUserTz.toInstant();

        // Get total summary data (no filters)
        Object[] summaryData = gpsPointRepository.getGpsPointSummaryData(userId, todayStart, todayEnd);

        long totalPoints = ((Number) summaryData[0]).longValue();
        long pointsToday = ((Number) summaryData[1]).longValue();
        Instant firstTimestamp = TimestampUtils.getInstantSafe(summaryData[2]);
        Instant lastTimestamp = TimestampUtils.getInstantSafe(summaryData[3]);

        GpsPointSummaryDTO summary = new GpsPointSummaryDTO(totalPoints, pointsToday, firstTimestamp, lastTimestamp, null);

        // If filters are active, get filtered count
        if (filters != null && filters.hasFilters()) {
            long filteredPoints = gpsPointRepository.countByUserAndFilters(userId, filters);
            summary.setFilteredPoints(filteredPoints);
        }

        return summary;
    }

    /**
     * Stream GPS points for export with filters.
     * Uses batching to avoid OOM with large datasets.
     *
     * @param userId       The ID of the user
     * @param filters      Filter criteria
     * @param batchSize    Number of records to process at a time
     * @param consumer     Consumer to process each batch
     */
    public void streamGpsPointsForExport(UUID userId, GpsPointFilterDTO filters,
                                         int batchSize, Consumer<List<GpsPointEntity>> consumer) {
        gpsPointRepository.streamByUserAndFilters(userId, filters, batchSize, consumer);
    }

    /**
     * Delete ALL GPS points and timeline data for a user.
     * Uses native SQL for maximum efficiency with large datasets (millions of records).
     * Does not trigger timeline regeneration since everything is being deleted.
     *
     * @param userId The ID of the user whose data to delete
     */
    @Transactional
    public void deleteAllGpsData(UUID userId) {
        log.info("Deleting ALL GPS and timeline data for user {}", userId);

        // Delete timeline data first (before GPS points)
        em.createNativeQuery("DELETE FROM timeline_stays WHERE user_id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();

        em.createNativeQuery("DELETE FROM timeline_trips WHERE user_id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();

        em.createNativeQuery("DELETE FROM timeline_data_gaps WHERE user_id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();

        em.createNativeQuery("DELETE FROM gps_points WHERE user_id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();

        log.info("Successfully deleted all GPS and timeline data for user {}", userId);
    }
}
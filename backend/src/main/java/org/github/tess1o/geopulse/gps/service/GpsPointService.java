package org.github.tess1o.geopulse.gps.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.colota.model.ColotaLocationMessage;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.point.DawarichLocation;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.point.DawarichPayload;
import org.github.tess1o.geopulse.gps.integrations.homeassistant.model.HomeAssistantGpsData;
import org.github.tess1o.geopulse.gps.integrations.traccar.model.TraccarPositionData;
import org.github.tess1o.geopulse.geofencing.service.GeofenceEvaluationService;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.model.*;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gps.service.filter.GpsDataFilteringService;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.gps.integrations.overland.model.OverlandLocationMessage;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;
import org.github.tess1o.geopulse.shared.service.LocationPointResolver;
import org.github.tess1o.geopulse.shared.service.LocationResolutionResult;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.service.trips.GpsPointEnvironmentService;
import org.github.tess1o.geopulse.streaming.util.TimelineGpsAccuracyFilter;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
    private final GpsTelemetryRenderingService telemetryRenderingService;
    private final GeofenceEvaluationService geofenceEvaluationService;
    private final TimelineConfigurationProvider timelineConfigurationProvider;
    private final GpsPointEnvironmentService gpsPointEnvironmentService;

    @Inject
    LocationPointResolver locationPointResolver;

    @ConfigProperty(name = "geopulse.gps.duplicate-detection.location-time-threshold-minutes", defaultValue = "2")
    int globalDuplicateDetectionThresholdMinutes;

    @Inject
    public GpsPointService(GpsPointMapper gpsPointMapper, GpsPointRepository gpsPointRepository,
                           GpsPointDuplicateDetectionService duplicateDetectionService, EntityManager em,
                           StreamingTimelineGenerationService streamingTimelineGenerationService,
                           GpsDataFilteringService filteringService,
                           GpsTelemetryRenderingService telemetryRenderingService,
                           GeofenceEvaluationService geofenceEvaluationService,
                           TimelineConfigurationProvider timelineConfigurationProvider,
                           GpsPointEnvironmentService gpsPointEnvironmentService) {
        this.gpsPointMapper = gpsPointMapper;
        this.gpsPointRepository = gpsPointRepository;
        this.duplicateDetectionService = duplicateDetectionService;
        this.em = em;
        this.streamingTimelineGenerationService = streamingTimelineGenerationService;
        this.filteringService = filteringService;
        this.telemetryRenderingService = telemetryRenderingService;
        this.geofenceEvaluationService = geofenceEvaluationService;
        this.timelineConfigurationProvider = timelineConfigurationProvider;
        this.gpsPointEnvironmentService = gpsPointEnvironmentService;
    }

    /**
     * Common logic for filtering and persisting a GPS point entity.
     * This method should be called after entity mapping.
     *
     * @param entity The mapped GPS point entity to filter and persist
     * @param config The GPS source configuration containing filter settings
     * @return saved point if persisted, otherwise empty when rejected by filters or duplicate detection
     */
    private Optional<GpsPointEntity> filterAndPersistGpsPoint(GpsPointEntity entity, GpsSourceConfigEntity config) {
        var filterResult = filteringService.filter(entity, config);
        if (filterResult.isRejected()) {
            // Already logged in filtering service
            return Optional.empty();
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
            return Optional.empty();
        } else {
            // Persist the new entity
            gpsPointRepository.persist(entity);
            geofenceEvaluationService.handlePersistedPoint(entity);
            log.info("Saved {} GPS point for user {} at timestamp {}", entity.getSourceType(), entity.getUser().getId(), entity.getTimestamp());
            return Optional.of(entity);
        }
    }

    private void enrichSavedGpsPointsIfBoatReady(UUID userId, Collection<GpsPointEntity> savedPoints) {
        if (savedPoints == null || savedPoints.isEmpty() || timelineConfigurationProvider == null || gpsPointEnvironmentService == null) {
            return;
        }

        try {
            String datasetVersion = gpsPointEnvironmentService.getCurrentEnvironmentDatasetVersion();
            if (datasetVersion == null) {
                return;
            }

            if (!timelineConfigurationProvider.isBoatEnabledForUser(userId)) {
                return;
            }

            em.flush();
            List<Long> savedPointIds = savedPoints.stream()
                    .map(GpsPointEntity::getId)
                    .filter(id -> id != null)
                    .toList();
            if (!savedPointIds.isEmpty()) {
                gpsPointEnvironmentService.enrichPoints(userId, savedPointIds, datasetVersion);
            }
        } catch (Exception e) {
            log.warn("Failed to enrich Boat water evidence for newly saved GPS points for user {}: {}",
                    userId, e.getMessage());
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

        filterAndPersistGpsPoint(entity, config)
                .ifPresent(savedPoint -> enrichSavedGpsPointsIfBoatReady(userId, List.of(savedPoint)));
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

        filterAndPersistGpsPoint(entity, config)
                .ifPresent(savedPoint -> enrichSavedGpsPointsIfBoatReady(userId, List.of(savedPoint)));
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

        List<GpsPointEntity> savedPoints = new ArrayList<>();
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

            filterAndPersistGpsPoint(entity, config).ifPresent(savedPoints::add);
        }
        enrichSavedGpsPointsIfBoatReady(userId, savedPoints);
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

        filterAndPersistGpsPoint(entity, config)
                .ifPresent(savedPoint -> enrichSavedGpsPointsIfBoatReady(userId, List.of(savedPoint)));
    }

    @Transactional
    public void saveColotaGpsPoint(ColotaLocationMessage message, UUID userId, GpsSourceType sourceType, GpsSourceConfigEntity config) {
        Instant timestamp = Instant.ofEpochSecond(message.getTst());

        // Check for location-based duplicates if enabled, otherwise use exact timestamp check
        if (config.isEnableDuplicateDetection()) {
            int threshold = config.getDuplicateDetectionThresholdMinutes() != null
                ? config.getDuplicateDetectionThresholdMinutes()
                : globalDuplicateDetectionThresholdMinutes;

            if (duplicateDetectionService.isLocationDuplicate(userId, message.getLat(), message.getLon(), timestamp, sourceType, threshold)) {
                log.info("Skipping Colota GPS point for user {} at coordinates ({}, {}): duplicate location detected within {} minutes window",
                        userId, message.getLat(), message.getLon(), threshold);
                return;
            }
        } else {
            if (duplicateDetectionService.isDuplicatePoint(userId, timestamp, sourceType)) {
                log.info("Skipping duplicate Colota GPS point for user {} at timestamp {}", userId, timestamp);
                return;
            }
        }

        // Map message to entity (mapper handles m/s -> km/h conversion)
        UserEntity user = em.getReference(UserEntity.class, userId);
        GpsPointEntity entity = gpsPointMapper.toEntity(message, user, sourceType);

        filterAndPersistGpsPoint(entity, config)
                .ifPresent(savedPoint -> enrichSavedGpsPointsIfBoatReady(userId, List.of(savedPoint)));
    }

    @Transactional
    public void saveTraccarGpsPoint(TraccarPositionData data, UUID userId, GpsSourceType sourceType, GpsSourceConfigEntity config) {
        if (data == null || data.getPosition() == null) {
            log.warn("Skipping Traccar payload for user {}: missing position", userId);
            return;
        }

        var position = data.getPosition();
        if (position.getLatitude() == null || position.getLongitude() == null) {
            log.warn("Skipping Traccar payload for user {}: missing latitude/longitude", userId);
            return;
        }

        Instant timestamp = position.resolveTimestamp();
        if (timestamp == null) {
            log.warn("Skipping Traccar payload for user {}: missing timestamp", userId);
            return;
        }

        double lat = position.getLatitude();
        double lon = position.getLongitude();

        if (config.isEnableDuplicateDetection()) {
            int threshold = config.getDuplicateDetectionThresholdMinutes() != null
                    ? config.getDuplicateDetectionThresholdMinutes()
                    : globalDuplicateDetectionThresholdMinutes;

            if (duplicateDetectionService.isLocationDuplicate(userId, lat, lon, timestamp, sourceType, threshold)) {
                log.info("Skipping Traccar GPS point for user {} at coordinates ({}, {}): duplicate location detected within {} minutes window",
                        userId, lat, lon, threshold);
                return;
            }
        } else {
            if (duplicateDetectionService.isDuplicatePoint(userId, timestamp, sourceType)) {
                log.info("Skipping duplicate Traccar GPS point for user {} at timestamp {}", userId, timestamp);
                return;
            }
        }

        UserEntity user = em.getReference(UserEntity.class, userId);
        GpsPointEntity entity = gpsPointMapper.toEntity(data, user, sourceType);
        filterAndPersistGpsPoint(entity, config)
                .ifPresent(savedPoint -> enrichSavedGpsPointsIfBoatReady(userId, List.of(savedPoint)));
    }

    @Transactional
    public void saveMobileAppGpsPoints(List<GpsPointDTO> data, String deviceId, UUID userId, GpsSourceType sourceType, GpsSourceConfigEntity config) {
        if (data == null || data.isEmpty()) {
            log.warn("Gps points are empty");
            return;
        }

        List<GpsPointEntity> savedPoints = new ArrayList<>();
        data.stream()
                .filter(point -> point.getTimestamp() != null)
                .sorted(Comparator.comparing(GpsPointDTO::getTimestamp))
                .forEach(point -> saveMobileAppGpsPointInternal(point, deviceId, userId, sourceType, config)
                        .ifPresent(savedPoints::add));
        enrichSavedGpsPointsIfBoatReady(userId, savedPoints);
    }

    @Transactional
    public void saveMobileAppGpsPoint(GpsPointDTO data, String deviceId, UUID userId, GpsSourceType sourceType, GpsSourceConfigEntity config) {
        saveMobileAppGpsPointInternal(data, deviceId, userId, sourceType, config)
                .ifPresent(savedPoint -> enrichSavedGpsPointsIfBoatReady(userId, List.of(savedPoint)));
    }

    private Optional<GpsPointEntity> saveMobileAppGpsPointInternal(GpsPointDTO data, String deviceId, UUID userId, GpsSourceType sourceType, GpsSourceConfigEntity config) {
        Instant timestamp = data.getTimestamp();

        double lat = data.getCoordinates().getLat();
        double lon = data.getCoordinates().getLng();

        if (config.isEnableDuplicateDetection()) {
            int threshold = config.getDuplicateDetectionThresholdMinutes() != null
                    ? config.getDuplicateDetectionThresholdMinutes()
                    : globalDuplicateDetectionThresholdMinutes;

            if (duplicateDetectionService.isLocationDuplicate(userId, lat, lon, timestamp, sourceType, threshold)) {
                log.info("Skipping Mobile App GPS point for user {} and device id {} at coordinates ({}, {}): duplicate location detected within {} minutes window", userId, deviceId, lat, lon, threshold);
                return Optional.empty();
            }
        } else {
            if (duplicateDetectionService.isDuplicatePoint(userId, timestamp, sourceType)) {
                log.info("Skipping duplicate Mobile App GPS point for user {} and device id {} at timestamp {}", userId, deviceId, timestamp);
                return Optional.empty();
            }
        }

        UserEntity user = em.getReference(UserEntity.class, userId);
        GpsPointEntity entity = gpsPointMapper.toEntity(data, deviceId, user, sourceType);
        return filterAndPersistGpsPoint(entity, config);
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
        TimelineConfig config = timelineConfigurationProvider.getConfigurationForUser(userId);
        Double maxAccuracy = TimelineGpsAccuracyFilter.getActiveMaxAccuracyThreshold(config);
        List<GpsPointEntity> gpsPoints = gpsPointRepository.findEligibleByUserIdAndTimePeriod(userId, startTime, endTime, maxAccuracy);
        List<GpsPointPathPointDTO> pathPoints = gpsPointMapper.toPathPoints(gpsPoints);
        applyTelemetryToPathPoints(userId, gpsPoints, pathPoints);

        return new GpsPointPathDTO(userId, pathPoints);
    }

    public RawGpsPointMapResponseDTO getRawGpsMapPoints(UUID userId, Instant startTime, Instant endTime, int limit) {
        TimelineConfig config = timelineConfigurationProvider.getConfigurationForUser(userId);
        Double maxAccuracy = TimelineGpsAccuracyFilter.getActiveMaxAccuracyThreshold(config);
        long totalCount = gpsPointRepository.countEligibleByUserIdAndTimePeriod(userId, startTime, endTime, maxAccuracy);
        List<GpsPointEntity> gpsPoints = gpsPointRepository.findEligibleMapPointsByUserIdAndTimePeriod(userId, startTime, endTime, limit, maxAccuracy);
        List<RawGpsPointMapPointDTO> points = gpsPointMapper.toRawGpsPointMapPointDTOs(gpsPoints);

        return RawGpsPointMapResponseDTO.builder()
                .points(points)
                .totalCount(totalCount)
                .returnedCount(points.size())
                .limit(limit)
                .limited(totalCount > points.size())
                .build();
    }

    public RawGpsPointLocationDTO resolveRawGpsPointLocation(UUID userId, Long pointId) {
        Optional<GpsPointEntity> optionalPoint = gpsPointRepository.findByIdOptional(pointId);
        if (optionalPoint.isEmpty()) {
            throw new NotFoundException("GPS point not found with ID: " + pointId);
        }

        GpsPointEntity gpsPoint = optionalPoint.get();
        if (!gpsPoint.getUser().getId().equals(userId)) {
            throw new ForbiddenException("GPS point does not belong to the user");
        }

        if (gpsPoint.getCoordinates() == null) {
            throw new NotFoundException("GPS point has no coordinates");
        }

        LocationResolutionResult result = locationPointResolver.resolveLocationWithReferences(userId, gpsPoint.getCoordinates());
        String sourceType = result.getFavoriteId() != null
                ? "favorite"
                : result.getGeocodingId() != null ? "geocoding" : "unknown";

        return RawGpsPointLocationDTO.builder()
                .locationName(result.getLocationName())
                .sourceType(sourceType)
                .favoriteId(result.getFavoriteId())
                .geocodingId(result.getGeocodingId())
                .anchorLatitude(result.getAnchorLatitude())
                .anchorLongitude(result.getAnchorLongitude())
                .build();
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
        enrichSavedGpsPointsIfBoatReady(userId, List.of(gpsPoint));

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
        return gpsPointRepository.findLatest(userId).map(point -> {
            GpsPointDTO dto = gpsPointMapper.toGpsPointDTO(point);
            applyTelemetryToGpsPoints(userId, List.of(point), List.of(dto));
            return dto;
        });
    }

    public GpsStatusDTO getGpsStatus(UUID userId) {
        Instant generatedAt = Instant.now();
        long totalGpsPoints = gpsPointRepository.countByUser(userId);

        return gpsPointRepository.findLatest(userId)
                .map(latestPoint -> {
                    Instant latestTimestamp = latestPoint.getTimestamp();
                    Long ageSeconds = latestTimestamp == null
                            ? null
                            : Duration.between(latestTimestamp, generatedAt).getSeconds();
                    Long ageMinutes = latestTimestamp == null
                            ? null
                            : Duration.between(latestTimestamp, generatedAt).toMinutes();

                    return new GpsStatusDTO(
                            generatedAt,
                            true,
                            latestTimestamp,
                            latestTimestamp == null ? null : latestTimestamp.getEpochSecond(),
                            ageSeconds,
                            ageMinutes,
                            latestPoint.getCreatedAt(),
                            latestPoint.getSourceType() == null ? null : latestPoint.getSourceType().name(),
                            latestPoint.getDeviceId(),
                            totalGpsPoints
                    );
                })
                .orElseGet(() -> new GpsStatusDTO(
                        generatedAt,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        totalGpsPoints
                ));
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
        applyTelemetryToGpsPoints(userId, points, pointDTOs);

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

    private void applyTelemetryToGpsPoints(UUID userId, List<GpsPointEntity> sourcePoints, List<GpsPointDTO> targetDtos) {
        if (sourcePoints == null || sourcePoints.isEmpty() || targetDtos == null || targetDtos.isEmpty()) {
            return;
        }

        var telemetryByPointId = telemetryRenderingService.renderForPoints(userId, sourcePoints);
        if (telemetryByPointId.isEmpty()) {
            return;
        }

        for (GpsPointDTO dto : targetDtos) {
            GpsTelemetryRenderingService.RenderedTelemetry rendered = telemetryByPointId.get(dto.getId());
            if (rendered == null) {
                continue;
            }
            dto.setTelemetryGpsData(rendered.gpsData());
            dto.setTelemetryCurrentPopup(rendered.currentPopup());
        }
    }

    private void applyTelemetryToPathPoints(UUID userId, List<GpsPointEntity> sourcePoints, List<GpsPointPathPointDTO> targetDtos) {
        if (sourcePoints == null || sourcePoints.isEmpty() || targetDtos == null || targetDtos.isEmpty()) {
            return;
        }

        var telemetryByPointId = telemetryRenderingService.renderForPoints(userId, sourcePoints);
        if (telemetryByPointId.isEmpty()) {
            return;
        }

        for (GpsPointPathPointDTO dto : targetDtos) {
            GpsTelemetryRenderingService.RenderedTelemetry rendered = telemetryByPointId.get(dto.getId());
            if (rendered == null) {
                continue;
            }
            dto.setTelemetryGpsData(rendered.gpsData());
            dto.setTelemetryCurrentPopup(rendered.currentPopup());
        }
    }
}

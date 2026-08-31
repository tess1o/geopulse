package org.github.tess1o.geopulse.importdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.QuarkusTransactionException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.github.tess1o.geopulse.export.dto.*;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.friends.model.UserFriendEntity;
import org.github.tess1o.geopulse.friends.model.UserFriendPermissionEntity;
import org.github.tess1o.geopulse.friends.repository.FriendshipRepository;
import org.github.tess1o.geopulse.friends.repository.UserFriendPermissionRepository;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.*;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.importdata.mapper.ImportDataMapper;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingStatus;
import org.github.tess1o.geopulse.mapmatching.model.TimelineTripPathMatchEntity;
import org.github.tess1o.geopulse.notes.model.NoteAnchorType;
import org.github.tess1o.geopulse.notes.model.NoteLocationSource;
import org.github.tess1o.geopulse.notes.model.TimelineNoteEntity;
import org.github.tess1o.geopulse.periods.model.entity.PeriodTagEntity;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.shared.exportimport.SequenceResetService;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapStayOverrideEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripMovementOverrideEntity;
import org.github.tess1o.geopulse.streaming.model.shared.DataGapStayOverrideLocationStrategy;
import org.github.tess1o.geopulse.streaming.model.shared.MovementTypeSource;
import org.github.tess1o.geopulse.trips.model.entity.*;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.weather.model.WeatherSampleEntity;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;
import org.locationtech.jts.geom.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Import strategy for GeoPulse ZIP format exports
 */
@ApplicationScoped
@Slf4j
public class GeoPulseImportStrategy implements ImportStrategy {

    @Inject
    UserRepository userRepository;

    @Inject
    ImportDataMapper importDataMapper;

    @Inject
    EntityManager entityManager;

    @Inject
    SequenceResetService sequenceResetService;

    @Inject
    TimelineImportHelper timelineImportHelper;

    @Inject
    ImportDataClearingService dataClearingService;

    @Inject
    BatchProcessor batchProcessor;

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    GpsSourceRepository gpsSourceRepository;

    @Inject
    FriendshipRepository friendshipRepository;

    @Inject
    UserFriendPermissionRepository friendPermissionRepository;

    @Inject
    AIEncryptionService encryptionService;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    private final GeometryFactory geometryFactory = new GeometryFactory();
    @Override
    public String getFormat() {
        return ExportImportConstants.Formats.GEOPULSE;
    }

    @Override
    public List<String> validateAndDetectDataTypes(ImportJob job) throws IOException {
        List<String> detectedDataTypes = new ArrayList<>();

        try (ByteArrayInputStream bais = new ByteArrayInputStream(job.getFileData());
             ZipInputStream zis = new ZipInputStream(bais)) {

            ZipEntry entry;
            boolean hasMetadata = false;

            while ((entry = zis.getNextEntry()) != null) {
                String fileName = entry.getName();

                switch (fileName) {
                    case ExportImportConstants.FileNames.METADATA:
                        hasMetadata = true;
                        validateMetadata(zis, job);
                        break;
                    case ExportImportConstants.FileNames.RAW_GPS_DATA:
                        detectedDataTypes.add(ExportImportConstants.DataTypes.RAW_GPS);
                        break;
                    case ExportImportConstants.FileNames.TIMELINE_DATA:
                        if (job.getOptions().isSnapshotRestore()) {
                            detectedDataTypes.add(ExportImportConstants.DataTypes.TIMELINE);
                        } else {
                            // Timeline data will be regenerated from GPS data for normal user imports.
                            log.debug("Timeline data found in export - will be regenerated from GPS data");
                        }
                        break;
                    case ExportImportConstants.FileNames.DATA_GAPS:
                        // Data gaps will be regenerated during timeline generation - skip detection
                        log.debug("Data gaps found in export - will be regenerated during timeline generation");
                        break;
                    case ExportImportConstants.FileNames.FAVORITES:
                        detectedDataTypes.add(ExportImportConstants.DataTypes.FAVORITES);
                        break;
                    case ExportImportConstants.FileNames.USER_INFO:
                        detectedDataTypes.add(ExportImportConstants.DataTypes.USER_INFO);
                        break;
                    case ExportImportConstants.FileNames.LOCATION_SOURCES:
                        detectedDataTypes.add(ExportImportConstants.DataTypes.LOCATION_SOURCES);
                        break;
                    case ExportImportConstants.FileNames.REVERSE_GEOCODING:
                        detectedDataTypes.add(ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION);
                        break;
                    case ExportImportConstants.FileNames.PERIOD_TAGS:
                        detectedDataTypes.add(ExportImportConstants.DataTypes.PERIOD_TAGS);
                        break;
                    case ExportImportConstants.FileNames.TIMELINE_OVERRIDES:
                        detectedDataTypes.add(ExportImportConstants.DataTypes.TIMELINE_OVERRIDES);
                        break;
                    case ExportImportConstants.FileNames.TRIP_WORKSPACE:
                        detectedDataTypes.add(ExportImportConstants.DataTypes.TRIP_WORKSPACE);
                        break;
                    case ExportImportConstants.FileNames.NOTIFICATION_TEMPLATES:
                        detectedDataTypes.add(ExportImportConstants.DataTypes.NOTIFICATION_TEMPLATES);
                        break;
                    case ExportImportConstants.FileNames.GEOFENCING:
                        detectedDataTypes.add(ExportImportConstants.DataTypes.GEOFENCING);
                        break;
                    case ExportImportConstants.FileNames.NOTES:
                        detectedDataTypes.add(ExportImportConstants.DataTypes.NOTES);
                        break;
                    case ExportImportConstants.FileNames.WEATHER_SAMPLES:
                        detectedDataTypes.add(ExportImportConstants.DataTypes.WEATHER_SAMPLES);
                        break;
                    case ExportImportConstants.FileNames.MAP_MATCHING:
                        detectedDataTypes.add(ExportImportConstants.DataTypes.MAP_MATCHING);
                        break;
                    case ExportImportConstants.FileNames.FRIENDS:
                        detectedDataTypes.add(ExportImportConstants.DataTypes.FRIENDS);
                        break;
                    case ExportImportConstants.FileNames.FRIEND_PERMISSIONS:
                        detectedDataTypes.add(ExportImportConstants.DataTypes.FRIEND_PERMISSIONS);
                        break;
                    default:
                        log.warn("Unknown file in import: {}", fileName);
                }
                zis.closeEntry();
            }

            if (!hasMetadata) {
                throw new IllegalArgumentException("Invalid import file: missing metadata.json");
            }

            if (detectedDataTypes.isEmpty()) {
                throw new IllegalArgumentException("Invalid import file: no data files found");
            }

            return detectedDataTypes;
        }
    }

    @Override
    @Transactional
    public void processImportData(ImportJob job) throws IOException {
        // Collect all file contents for proper dependency ordering
        Map<String, byte[]> fileContents = extractZipContents(job);

        // Import in dependency order using native SQL
        processFilesInOrder(fileContents, job);

        // Reset sequences after import to prevent future ID conflicts
        log.info("Resetting sequences after import...");
        sequenceResetService.resetAllSequences();
        if (job.getTimelineJobId() == null) {
            job.setProgress(100);
        }
    }

    private Map<String, byte[]> extractZipContents(ImportJob job) throws IOException {
        Map<String, byte[]> fileContents = new HashMap<>();

        try (ByteArrayInputStream bais = new ByteArrayInputStream(job.getFileData());
             ZipInputStream zis = new ZipInputStream(bais)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String fileName = entry.getName();
                byte[] content = zis.readAllBytes();

                // Skip if this data type is not requested for import
                String dataType = getDataTypeFromFileName(fileName);
                if (dataType != null && !job.getOptions().getDataTypes().contains(dataType)) {
                    log.debug("Skipping {} - not requested for import", fileName);
                    zis.closeEntry();
                    continue;
                }

                fileContents.put(fileName, content);
                zis.closeEntry();
            }
        }

        return fileContents;
    }

    private void processFilesInOrder(Map<String, byte[]> fileContents, ImportJob job) throws IOException {
        int totalProgress = 0;
        ImportReferenceMaps referenceMaps = new ImportReferenceMaps();

        // 1. Import reverse geocoding locations first (no dependencies)
        if (fileContents.containsKey(ExportImportConstants.FileNames.REVERSE_GEOCODING)) {
            importReverseGeocodingData(fileContents.get(ExportImportConstants.FileNames.REVERSE_GEOCODING), job, referenceMaps);
            totalProgress += 5;
            job.setProgress(totalProgress);
        }

        // 2. Import favorites (no dependencies)
        if (fileContents.containsKey(ExportImportConstants.FileNames.FAVORITES)) {
            importFavoritesData(fileContents.get(ExportImportConstants.FileNames.FAVORITES), job, referenceMaps);
            totalProgress += 10;
            job.setProgress(totalProgress);
        }

        // Check if GPS data exists for import
        boolean hasGpsData = fileContents.containsKey(ExportImportConstants.FileNames.RAW_GPS_DATA);
        boolean hasTimelineData = fileContents.containsKey(ExportImportConstants.FileNames.TIMELINE_DATA);
        boolean snapshotRestore = job.getOptions().isSnapshotRestore();

        // Handle data clearing before import if requested
        if (job.getOptions().isClearDataBeforeImport()) {
            clearExistingDataBeforeImport(fileContents, job);
            totalProgress += 10;
            job.setProgress(totalProgress);
        }

        // 3. Import GPS data first
        Instant firstGpsTimestamp = null;
        if (hasGpsData) {
            firstGpsTimestamp = importRawGpsData(fileContents.get(ExportImportConstants.FileNames.RAW_GPS_DATA), job);
            log.info("Successfully imported raw GPS data for user {} - first timestamp: {}", job.getUserId(), firstGpsTimestamp);
            totalProgress += 30;
            job.setProgress(totalProgress);
        }

        // 4. Full backup restores are snapshots: import stored timeline rows instead of regenerating.
        if (snapshotRestore && hasTimelineData) {
            importTimelineDataSnapshot(fileContents.get(ExportImportConstants.FileNames.TIMELINE_DATA), job, referenceMaps);
            totalProgress += 35;
            job.setProgress(totalProgress);
            log.info("Restored timeline snapshot for user {}; skipping timeline regeneration", job.getUserId());
        } else if (hasGpsData && firstGpsTimestamp != null) {
            if (snapshotRestore) {
                log.warn("Snapshot restore for user {} has GPS data but no timeline snapshot; falling back to timeline regeneration",
                        job.getUserId());
            }
            log.info("Regenerating timeline from imported GPS data starting from timestamp: {}", firstGpsTimestamp);

            // Update progress FIRST, before blocking timeline trigger
            job.updateProgress(totalProgress, "Triggering timeline generation...");

            // Trigger timeline generation (may block for 30s with retry logic)
            // Note: timelineJobId is set inside this method, which also updates progress
            UUID timelineJobId = timelineImportHelper.triggerTimelineGenerationForImportedGpsData(job, firstGpsTimestamp);
            log.info("Timeline job {} triggered for import {}", timelineJobId, job.getJobId());

            // Note: Import will be marked as completed by ImportService once timeline job completes
            totalProgress += 35;
        }

        // 5. Import user info
        if (fileContents.containsKey(ExportImportConstants.FileNames.USER_INFO)) {
            importUserInfoData(fileContents.get(ExportImportConstants.FileNames.USER_INFO), job);
            totalProgress += 5;
            job.setProgress(totalProgress);
        }

        // 6. Import location sources
        if (fileContents.containsKey(ExportImportConstants.FileNames.LOCATION_SOURCES)) {
            importLocationSourcesData(fileContents.get(ExportImportConstants.FileNames.LOCATION_SOURCES), job);
            totalProgress += 5;
            job.setProgress(totalProgress);
        }

        if (fileContents.containsKey(ExportImportConstants.FileNames.PERIOD_TAGS)) {
            importPeriodTagsData(fileContents.get(ExportImportConstants.FileNames.PERIOD_TAGS), job, referenceMaps);
        }

        if (fileContents.containsKey(ExportImportConstants.FileNames.TIMELINE_OVERRIDES)) {
            importTimelineOverridesData(fileContents.get(ExportImportConstants.FileNames.TIMELINE_OVERRIDES), job, referenceMaps);
        }

        if (fileContents.containsKey(ExportImportConstants.FileNames.NOTIFICATION_TEMPLATES)) {
            importNotificationTemplatesData(fileContents.get(ExportImportConstants.FileNames.NOTIFICATION_TEMPLATES), job, referenceMaps);
        }

        if (fileContents.containsKey(ExportImportConstants.FileNames.TRIP_WORKSPACE)) {
            importTripWorkspaceData(fileContents.get(ExportImportConstants.FileNames.TRIP_WORKSPACE), job, referenceMaps);
        }

        if (fileContents.containsKey(ExportImportConstants.FileNames.GEOFENCING)) {
            importGeofencingData(fileContents.get(ExportImportConstants.FileNames.GEOFENCING), job, referenceMaps);
        }

        if (fileContents.containsKey(ExportImportConstants.FileNames.NOTES)) {
            importNotesData(fileContents.get(ExportImportConstants.FileNames.NOTES), job, referenceMaps);
        }

        if (fileContents.containsKey(ExportImportConstants.FileNames.WEATHER_SAMPLES)) {
            importWeatherSamplesData(fileContents.get(ExportImportConstants.FileNames.WEATHER_SAMPLES), job);
        }

        if (fileContents.containsKey(ExportImportConstants.FileNames.MAP_MATCHING)) {
            importMapMatchingData(fileContents.get(ExportImportConstants.FileNames.MAP_MATCHING), job, referenceMaps);
        }

        if (fileContents.containsKey(ExportImportConstants.FileNames.FRIENDS)) {
            importFriendsData(fileContents.get(ExportImportConstants.FileNames.FRIENDS), job);
        }

        if (fileContents.containsKey(ExportImportConstants.FileNames.FRIEND_PERMISSIONS)) {
            importFriendPermissionsData(fileContents.get(ExportImportConstants.FileNames.FRIEND_PERMISSIONS), job);
        }
    }

    private void validateMetadata(ZipInputStream zis, ImportJob job) throws IOException {
        byte[] content = zis.readAllBytes();
        ExportMetadataDto metadata = objectMapper.readValue(content, ExportMetadataDto.class);

        if (!ExportImportConstants.Versions.CURRENT.equals(metadata.getVersion()) &&
                !ExportImportConstants.Versions.V1_0.equals(metadata.getVersion())) {
            throw new IllegalArgumentException("Unsupported export version: " + metadata.getVersion());
        }

        if (!ExportImportConstants.Formats.GEOPULSE.equals(job.getOptions().getImportFormat()) &&
                !ExportImportConstants.Formats.JSON.equals(metadata.getFormat())) {
            throw new IllegalArgumentException("Unsupported export format: " + metadata.getFormat());
        }
    }

    private String getDataTypeFromFileName(String fileName) {
        switch (fileName) {
            case ExportImportConstants.FileNames.RAW_GPS_DATA:
                return ExportImportConstants.DataTypes.RAW_GPS;
            case ExportImportConstants.FileNames.TIMELINE_DATA:
                return ExportImportConstants.DataTypes.TIMELINE;
            case ExportImportConstants.FileNames.DATA_GAPS:
                return ExportImportConstants.DataTypes.DATA_GAPS;
            case ExportImportConstants.FileNames.FAVORITES:
                return ExportImportConstants.DataTypes.FAVORITES;
            case ExportImportConstants.FileNames.USER_INFO:
                return ExportImportConstants.DataTypes.USER_INFO;
            case ExportImportConstants.FileNames.LOCATION_SOURCES:
                return ExportImportConstants.DataTypes.LOCATION_SOURCES;
            case ExportImportConstants.FileNames.REVERSE_GEOCODING:
                return ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION;
            case ExportImportConstants.FileNames.PERIOD_TAGS:
                return ExportImportConstants.DataTypes.PERIOD_TAGS;
            case ExportImportConstants.FileNames.TIMELINE_OVERRIDES:
                return ExportImportConstants.DataTypes.TIMELINE_OVERRIDES;
            case ExportImportConstants.FileNames.TRIP_WORKSPACE:
                return ExportImportConstants.DataTypes.TRIP_WORKSPACE;
            case ExportImportConstants.FileNames.NOTIFICATION_TEMPLATES:
                return ExportImportConstants.DataTypes.NOTIFICATION_TEMPLATES;
            case ExportImportConstants.FileNames.GEOFENCING:
                return ExportImportConstants.DataTypes.GEOFENCING;
            case ExportImportConstants.FileNames.NOTES:
                return ExportImportConstants.DataTypes.NOTES;
            case ExportImportConstants.FileNames.WEATHER_SAMPLES:
                return ExportImportConstants.DataTypes.WEATHER_SAMPLES;
            case ExportImportConstants.FileNames.MAP_MATCHING:
                return ExportImportConstants.DataTypes.MAP_MATCHING;
            case ExportImportConstants.FileNames.FRIENDS:
                return ExportImportConstants.DataTypes.FRIENDS;
            case ExportImportConstants.FileNames.FRIEND_PERMISSIONS:
                return ExportImportConstants.DataTypes.FRIEND_PERMISSIONS;
            default:
                return null;
        }
    }

    // Import methods delegated from original ImportDataService
    @Transactional
    public Instant importRawGpsData(byte[] content, ImportJob job) throws IOException {
        RawGpsDataDto gpsData = objectMapper.readValue(content, RawGpsDataDto.class);
        log.info("Importing {} GPS points for user {} using BatchProcessor", gpsData.getPoints().size(), job.getUserId());

        // Get user entity
        UserEntity user = userRepository.findById(job.getUserId());
        if (user == null) {
            throw new IllegalStateException("User not found: " + job.getUserId());
        }

        // Convert DTOs to entities without preserving IDs
        List<GpsPointEntity> gpsEntities = convertDtosToGpsEntities(gpsData.getPoints(), user, job);
        
        if (gpsEntities.isEmpty()) {
            log.warn("No GPS points to import for user {}", job.getUserId());
            return null;
        }

        // Use BatchProcessor with Clear/Merge mode based on user preference
        boolean clearMode = job.getOptions().isClearDataBeforeImport();
        int batchSize = clearMode ? 500 : 250; // Use appropriate batch size

        // Calculate base progress from what we already did (reverse geocoding 5% + favorites 10% + clearing 10% = 25%, or 15% without clearing)
        int baseProgress = clearMode ? 25 : 15;

        BatchProcessor.BatchResult result = batchProcessor.processInBatches(
            gpsEntities, batchSize, clearMode, job, baseProgress, baseProgress + 30);
        if (result.imported > 0) {
            job.setGpsDataImported(true);
        }
        
        log.info("Successfully imported {} GPS points using BatchProcessor (skipped {} duplicates)", 
                result.imported, result.skipped);

        // Return the earliest timestamp from imported GPS data
        return gpsEntities.stream()
                .map(GpsPointEntity::getTimestamp)
                .min(Instant::compareTo)
                .orElse(null);
    }

    /**
     * Convert GPS point DTOs to entities without preserving original IDs
     */
    private List<GpsPointEntity> convertDtosToGpsEntities(List<RawGpsDataDto.GpsPointDto> pointDtos, 
                                                         UserEntity user, ImportJob job) {
        List<GpsPointEntity> gpsEntities = new ArrayList<>();
        
        for (RawGpsDataDto.GpsPointDto pointDto : pointDtos) {
            // Skip points without valid coordinates or timestamp
            if (pointDto.getTimestamp() == null ||
                pointDto.getLatitude() == null || pointDto.getLongitude() == null) {
                continue;
            }

            // Apply date range filter if specified
            if (job.getOptions().getDateRangeFilter() != null) {
                if (pointDto.getTimestamp().isBefore(job.getOptions().getDateRangeFilter().getStartDate()) ||
                        pointDto.getTimestamp().isAfter(job.getOptions().getDateRangeFilter().getEndDate())) {
                    continue;
                }
            }

            try {
                GpsPointEntity gpsEntity = new GpsPointEntity();
                gpsEntity.setUser(user);
                gpsEntity.setDeviceId(pointDto.getDeviceId() != null ? pointDto.getDeviceId() : "geopulse-import");
                gpsEntity.setCoordinates(org.github.tess1o.geopulse.shared.geo.GeoUtils.createPoint(
                        pointDto.getLongitude(), pointDto.getLatitude()));
                gpsEntity.setTimestamp(pointDto.getTimestamp());
                // Use original source type from export data
                try {
                    org.github.tess1o.geopulse.shared.gps.GpsSourceType sourceType = 
                        org.github.tess1o.geopulse.shared.gps.GpsSourceType.valueOf(pointDto.getSource());
                    gpsEntity.setSourceType(sourceType);
                } catch (IllegalArgumentException e) {
                    // Fallback to GPX if original source is invalid/unknown
                    log.warn("Unknown source type '{}' for GPS point, using GPX as fallback", pointDto.getSource());
                    gpsEntity.setSourceType(org.github.tess1o.geopulse.shared.gps.GpsSourceType.GPX);
                }
                gpsEntity.setCreatedAt(Instant.now());
                
                // Set optional fields if available
                if (pointDto.getAccuracy() != null) {
                    gpsEntity.setAccuracy(pointDto.getAccuracy());
                }
                if (pointDto.getAltitude() != null) {
                    gpsEntity.setAltitude(pointDto.getAltitude());
                }
                if (pointDto.getSpeed() != null) {
                    gpsEntity.setVelocity(pointDto.getSpeed());
                }
                if (pointDto.getBattery() != null) {
                    gpsEntity.setBattery(pointDto.getBattery());
                }
                
                gpsEntities.add(gpsEntity);
                
            } catch (Exception e) {
                log.warn("Failed to create GPS entity from DTO with timestamp {}: {}", 
                        pointDto.getTimestamp(), e.getMessage());
            }
        }
        
        return gpsEntities;
    }

    @Transactional
    public void importTimelineDataSnapshot(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        TimelineDataDto timelineData = objectMapper.readValue(content, TimelineDataDto.class);
        UserEntity user = getImportingUser(job);

        int stays = importTimelineStaysSnapshot(timelineData, user, job, referenceMaps);
        int trips = importTimelineTripsSnapshot(timelineData, user, job, referenceMaps);
        int dataGaps = importTimelineDataGapsSnapshot(timelineData, user, job, referenceMaps);
        entityManager.flush();

        log.info("Imported timeline snapshot for user {}: {} stays, {} trips, {} data gaps",
                job.getUserId(), stays, trips, dataGaps);
    }

    private int importTimelineStaysSnapshot(TimelineDataDto timelineData,
                                            UserEntity user,
                                            ImportJob job,
                                            ImportReferenceMaps referenceMaps) {
        int imported = 0;
        for (TimelineDataDto.StayDto dto : emptyIfNull(timelineData.getStays())) {
            if (dto.getId() <= 0
                    || dto.getTimestamp() == null
                    || dto.getLatitude() == null
                    || dto.getLongitude() == null
                    || shouldSkipDueToDateFilter(dto.getTimestamp(), job)) {
                continue;
            }

            Long favoriteId = resolveMappedId(dto.getFavoriteId(), referenceMaps.favoriteIds);
            Long geocodingId = resolveMappedId(dto.getGeocodingId(), referenceMaps.geocodingIds);
            String locationSource = favoriteId == null ? LocationSource.GEOCODING.name() : LocationSource.FAVORITE.name();

            entityManager.createNativeQuery("""
                            INSERT INTO timeline_stays
                            (id, user_id, timestamp, stay_duration, location, location_name,
                             favorite_id, geocoding_id, created_at, last_updated, location_source)
                            VALUES (:id, :userId, :timestamp, :duration,
                                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326),
                                    :locationName, :favoriteId, :geocodingId, :createdAt, :lastUpdated, :locationSource)
                            ON CONFLICT (id) DO UPDATE SET
                                user_id = EXCLUDED.user_id,
                                timestamp = EXCLUDED.timestamp,
                                stay_duration = EXCLUDED.stay_duration,
                                location = EXCLUDED.location,
                                location_name = EXCLUDED.location_name,
                                favorite_id = EXCLUDED.favorite_id,
                                geocoding_id = EXCLUDED.geocoding_id,
                                created_at = EXCLUDED.created_at,
                                last_updated = EXCLUDED.last_updated,
                                location_source = EXCLUDED.location_source
                            """)
                    .setParameter("id", dto.getId())
                    .setParameter("userId", user.getId())
                    .setParameter("timestamp", dto.getTimestamp())
                    .setParameter("duration", durationSeconds(dto.getTimestamp(), dto.getEndTime(), dto.getDuration()))
                    .setParameter("longitude", dto.getLongitude())
                    .setParameter("latitude", dto.getLatitude())
                    .setParameter("locationName", dto.getAddress() == null ? "" : dto.getAddress())
                    .setParameter("favoriteId", favoriteId)
                    .setParameter("geocodingId", geocodingId)
                    .setParameter("createdAt", Instant.now())
                    .setParameter("lastUpdated", Instant.now())
                    .setParameter("locationSource", locationSource)
                    .executeUpdate();

            referenceMaps.timelineStayIds.put(dto.getId(), dto.getId());
            imported++;
        }
        return imported;
    }

    private int importTimelineTripsSnapshot(TimelineDataDto timelineData,
                                            UserEntity user,
                                            ImportJob job,
                                            ImportReferenceMaps referenceMaps) {
        int imported = 0;
        for (TimelineDataDto.TripDto dto : emptyIfNull(timelineData.getTrips())) {
            if (dto.getId() <= 0
                    || dto.getTimestamp() == null
                    || dto.getStartLatitude() == null
                    || dto.getStartLongitude() == null
                    || dto.getEndLatitude() == null
                    || dto.getEndLongitude() == null
                    || shouldSkipDueToDateFilter(dto.getTimestamp(), job)) {
                continue;
            }

            MovementTypeSource source = parseEnum(MovementTypeSource.class, dto.getMovementTypeSource(), MovementTypeSource.AUTO);
            entityManager.createNativeQuery("""
                            INSERT INTO timeline_trips
                            (id, user_id, timestamp, trip_duration, start_point, end_point,
                             distance_meters, movement_type, movement_type_source, avg_gps_speed, max_gps_speed,
                             speed_variance, low_accuracy_points_count, water_distance_meters, water_distance_ratio,
                             longest_water_segment_meters, water_sample_count, water_evidence_available,
                             created_at, last_updated)
                            VALUES (:id, :userId, :timestamp, :duration,
                                    ST_SetSRID(ST_MakePoint(:startLongitude, :startLatitude), 4326),
                                    ST_SetSRID(ST_MakePoint(:endLongitude, :endLatitude), 4326),
                                    :distance, :movementType, :movementTypeSource, :avgGpsSpeed, :maxGpsSpeed,
                                    :speedVariance, :lowAccuracyPointsCount, :waterDistanceMeters, :waterDistanceRatio,
                                    :longestWaterSegmentMeters, :waterSampleCount, :waterEvidenceAvailable,
                                    :createdAt, :lastUpdated)
                            ON CONFLICT (id) DO UPDATE SET
                                user_id = EXCLUDED.user_id,
                                timestamp = EXCLUDED.timestamp,
                                trip_duration = EXCLUDED.trip_duration,
                                start_point = EXCLUDED.start_point,
                                end_point = EXCLUDED.end_point,
                                distance_meters = EXCLUDED.distance_meters,
                                movement_type = EXCLUDED.movement_type,
                                movement_type_source = EXCLUDED.movement_type_source,
                                avg_gps_speed = EXCLUDED.avg_gps_speed,
                                max_gps_speed = EXCLUDED.max_gps_speed,
                                speed_variance = EXCLUDED.speed_variance,
                                low_accuracy_points_count = EXCLUDED.low_accuracy_points_count,
                                water_distance_meters = EXCLUDED.water_distance_meters,
                                water_distance_ratio = EXCLUDED.water_distance_ratio,
                                longest_water_segment_meters = EXCLUDED.longest_water_segment_meters,
                                water_sample_count = EXCLUDED.water_sample_count,
                                water_evidence_available = EXCLUDED.water_evidence_available,
                                created_at = EXCLUDED.created_at,
                                last_updated = EXCLUDED.last_updated
                            """)
                    .setParameter("id", dto.getId())
                    .setParameter("userId", user.getId())
                    .setParameter("timestamp", dto.getTimestamp())
                    .setParameter("duration", durationSeconds(dto.getTimestamp(), dto.getEndTime(), dto.getDuration()))
                    .setParameter("startLongitude", dto.getStartLongitude())
                    .setParameter("startLatitude", dto.getStartLatitude())
                    .setParameter("endLongitude", dto.getEndLongitude())
                    .setParameter("endLatitude", dto.getEndLatitude())
                    .setParameter("distance", dto.getDistance() == null ? 0L : dto.getDistance())
                    .setParameter("movementType", dto.getTransportMode())
                    .setParameter("movementTypeSource", source.name())
                    .setParameter("avgGpsSpeed", dto.getAvgGpsSpeed())
                    .setParameter("maxGpsSpeed", dto.getMaxGpsSpeed())
                    .setParameter("speedVariance", dto.getSpeedVariance())
                    .setParameter("lowAccuracyPointsCount", dto.getLowAccuracyPointsCount())
                    .setParameter("waterDistanceMeters", dto.getWaterDistanceMeters())
                    .setParameter("waterDistanceRatio", dto.getWaterDistanceRatio())
                    .setParameter("longestWaterSegmentMeters", dto.getLongestWaterSegmentMeters())
                    .setParameter("waterSampleCount", dto.getWaterSampleCount())
                    .setParameter("waterEvidenceAvailable", dto.getWaterEvidenceAvailable())
                    .setParameter("createdAt", Instant.now())
                    .setParameter("lastUpdated", Instant.now())
                    .executeUpdate();

            referenceMaps.timelineTripIds.put(dto.getId(), dto.getId());
            imported++;
        }
        return imported;
    }

    private int importTimelineDataGapsSnapshot(TimelineDataDto timelineData,
                                               UserEntity user,
                                               ImportJob job,
                                               ImportReferenceMaps referenceMaps) {
        int imported = 0;
        for (TimelineDataDto.DataGapDto dto : emptyIfNull(timelineData.getDataGaps())) {
            if (dto.getId() <= 0
                    || dto.getStartTime() == null
                    || dto.getEndTime() == null
                    || !dto.getEndTime().isAfter(dto.getStartTime())
                    || shouldSkipDueToDateFilter(dto.getStartTime(), job)) {
                continue;
            }

            entityManager.createNativeQuery("""
                            INSERT INTO timeline_data_gaps
                            (id, user_id, start_time, end_time, duration_seconds, created_at)
                            VALUES (:id, :userId, :startTime, :endTime, :durationSeconds, :createdAt)
                            ON CONFLICT (id) DO UPDATE SET
                                user_id = EXCLUDED.user_id,
                                start_time = EXCLUDED.start_time,
                                end_time = EXCLUDED.end_time,
                                duration_seconds = EXCLUDED.duration_seconds,
                                created_at = EXCLUDED.created_at
                            """)
                    .setParameter("id", dto.getId())
                    .setParameter("userId", user.getId())
                    .setParameter("startTime", dto.getStartTime())
                    .setParameter("endTime", dto.getEndTime())
                    .setParameter("durationSeconds", Math.max(1L, dto.getDurationSeconds()))
                    .setParameter("createdAt", defaultInstant(dto.getCreatedAt()))
                    .executeUpdate();

            referenceMaps.timelineDataGapIds.put(dto.getId(), dto.getId());
            imported++;
        }
        return imported;
    }

    @Transactional
    public void importFavoritesData(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        FavoritesDataDto favoritesData = objectMapper.readValue(content, FavoritesDataDto.class);
        log.info("Importing {} favorite points and {} favorite areas for user {} using duplicate detection",
                favoritesData.getPoints().size(), favoritesData.getAreas().size(), job.getUserId());

        // Get user entity
        UserEntity user = userRepository.findById(job.getUserId());
        if (user == null) {
            throw new IllegalStateException("User not found: " + job.getUserId());
        }

        int importedFavorites = 0;
        int skippedFavorites = 0;

        // Import favorite points with duplicate detection
        for (FavoritesDataDto.FavoritePointDto pointDto : favoritesData.getPoints()) {
            try {
                // Create Point geometry
                org.locationtech.jts.geom.Point geometry = importDataMapper.createPointFromCoordinates(
                        pointDto.getLongitude(), pointDto.getLatitude());

                // Check for duplicates by user + name + location
                List<FavoritesEntity> duplicates = favoritesRepository.findByUserAndNameAndLocation(
                        job.getUserId(), pointDto.getName(), geometry);

                if (duplicates.isEmpty()) {
                    // Create new favorite entity
                    FavoritesEntity favorite = new FavoritesEntity();
                    favorite.setUser(user);
                    favorite.setName(pointDto.getName());
                    favorite.setCity(pointDto.getCity());
                    favorite.setCountry(pointDto.getCountry());
                    favorite.setType(FavoriteLocationType.POINT);
                    favorite.setGeometry(geometry);
                    favorite.setMergeImpact(false);

                    favoritesRepository.persist(favorite);
                    entityManager.flush();
                    referenceMaps.favoriteIds.put(pointDto.getId(), favorite.getId());
                    importedFavorites++;
                } else {
                    // Update existing favorite with potentially better data
                    FavoritesEntity existing = duplicates.get(0);
                    updateFavoriteIfNecessary(existing, pointDto.getCity(), pointDto.getCountry());
                    referenceMaps.favoriteIds.put(pointDto.getId(), existing.getId());
                    skippedFavorites++;
                }
            } catch (Exception e) {
                log.warn("Failed to import favorite point '{}': {}", pointDto.getName(), e.getMessage());
            }
        }

        // Import favorite areas with duplicate detection
        for (FavoritesDataDto.FavoriteAreaDto areaDto : favoritesData.getAreas()) {
            try {
                // Create Polygon geometry
                org.locationtech.jts.geom.Polygon geometry = importDataMapper.createPolygonFromCoordinates(areaDto);
                // Convert to Point for duplicate detection (use centroid)
                org.locationtech.jts.geom.Point centroid = geometry.getCentroid();

                // Check for duplicates by user + name + location (centroid)
                List<FavoritesEntity> duplicates = favoritesRepository.findByUserAndNameAndLocation(
                        job.getUserId(), areaDto.getName(), centroid);

                if (duplicates.isEmpty()) {
                    // Create new favorite entity
                    FavoritesEntity favorite = new FavoritesEntity();
                    favorite.setUser(user);
                    favorite.setName(areaDto.getName());
                    favorite.setCity(areaDto.getCity());
                    favorite.setCountry(areaDto.getCountry());
                    favorite.setType(FavoriteLocationType.AREA);
                    favorite.setGeometry(geometry);
                    favorite.setMergeImpact(false);

                    favoritesRepository.persist(favorite);
                    entityManager.flush();
                    referenceMaps.favoriteIds.put(areaDto.getId(), favorite.getId());
                    importedFavorites++;
                } else {
                    // Update existing favorite with potentially better data
                    FavoritesEntity existing = duplicates.get(0);
                    updateFavoriteIfNecessary(existing, areaDto.getCity(), areaDto.getCountry());
                    referenceMaps.favoriteIds.put(areaDto.getId(), existing.getId());
                    skippedFavorites++;
                }
            } catch (Exception e) {
                log.warn("Failed to import favorite area '{}': {}", areaDto.getName(), e.getMessage());
            }
        }

        log.info("Successfully imported {} favorites using duplicate detection (skipped {} duplicates)", 
                importedFavorites, skippedFavorites);
    }

    /**
     * Update existing favorite with better data if available
     */
    private void updateFavoriteIfNecessary(FavoritesEntity existing, String newCity, String newCountry) {
        boolean updated = false;

        // Update city if current is null and new has value
        if (newCity != null && existing.getCity() == null) {
            existing.setCity(newCity);
            updated = true;
        }

        // Update country if current is null and new has value
        if (newCountry != null && existing.getCountry() == null) {
            existing.setCountry(newCountry);
            updated = true;
        }

        if (updated) {
            favoritesRepository.persist(existing);
        }
    }

    @Transactional
    public void importUserInfoData(byte[] content, ImportJob job) throws IOException {
        UserInfoDataDto userInfoData = objectMapper.readValue(content, UserInfoDataDto.class);
        log.info("Importing user info for user {}", job.getUserId());

        UserEntity user = userRepository.findById(job.getUserId());
        if (user == null) {
            throw new IllegalStateException("User not found: " + job.getUserId());
        }

        UserInfoDataDto.UserDto userData = userInfoData.getUser();

        // Update user preferences if they exist in the import
        if (userData.getPreferences() != null) {
            user.setTimelinePreferences(importDataMapper.updateTimelinePreferences(
                    userData.getPreferences(), user.getTimelinePreferences()));
        }

        userRepository.persist(user);
    }

    @Transactional
    public void importLocationSourcesData(byte[] content, ImportJob job) throws IOException {
        LocationSourcesDataDto sourcesData = objectMapper.readValue(content, LocationSourcesDataDto.class);
        log.info("Importing {} location sources for user {} using duplicate detection", sourcesData.getSources().size(), job.getUserId());

        // Get user entity
        UserEntity user = userRepository.findById(job.getUserId());
        if (user == null) {
            throw new IllegalStateException("User not found: " + job.getUserId());
        }

        int imported = 0;
        int skipped = 0;
        
        for (LocationSourcesDataDto.SourceDto sourceDto : sourcesData.getSources()) {
            try {
                // Convert string type to enum
                org.github.tess1o.geopulse.shared.gps.GpsSourceType sourceType = 
                    org.github.tess1o.geopulse.shared.gps.GpsSourceType.valueOf(sourceDto.getType());

                Optional<GpsSourceConfigEntity> existingById = sourceDto.getId() == null
                        ? Optional.empty()
                        : gpsSourceRepository.findByConfigIdAndUserId(sourceDto.getId(), job.getUserId());
                List<GpsSourceConfigEntity> duplicates = existingById
                        .map(List::of)
                        .orElseGet(() -> gpsSourceRepository.findByUserAndUsernameAndType(
                                job.getUserId(), sourceDto.getUsername(), sourceType));

                if (duplicates.isEmpty()) {
                    GpsSourceConfigEntity sourceConfig = new GpsSourceConfigEntity();
                    if (sourceDto.getId() != null) {
                        sourceConfig.setId(sourceDto.getId());
                    }
                    applyLocationSourceDto(sourceConfig, user, sourceDto, sourceType);
                    persistNewLocationSource(sourceConfig);
                    imported++;
                } else {
                    GpsSourceConfigEntity existing = duplicates.get(0);
                    applyLocationSourceDto(existing, user, sourceDto, sourceType);
                    skipped++;
                }
            } catch (Exception e) {
                log.warn("Failed to import GPS source '{}': {}", sourceDto.getUsername(), e.getMessage());
            }
        }

        log.info("Successfully imported {} GPS sources using duplicate detection (skipped {} duplicates)", imported, skipped);
    }

    private void persistNewLocationSource(GpsSourceConfigEntity sourceConfig) {
        if (sourceConfig.getId() == null) {
            gpsSourceRepository.persist(sourceConfig);
        } else {
            entityManager.createNativeQuery("""
                            INSERT INTO gps_source_config (
                                id,
                                user_id,
                                username,
                                password_hash,
                                token,
                                device_id,
                                payload_encryption_secret_encrypted,
                                payload_encryption_secret_key_id,
                                source_type,
                                active,
                                connection_type,
                                filter_inaccurate_data,
                                max_allowed_accuracy,
                                max_allowed_speed,
                                enable_duplicate_detection,
                                duplicate_detection_threshold_minutes
                            ) VALUES (
                                :id,
                                :userId,
                                :username,
                                :passwordHash,
                                :token,
                                :deviceId,
                                :payloadEncryptionSecretEncrypted,
                                :payloadEncryptionSecretKeyId,
                                :sourceType,
                                :active,
                                :connectionType,
                                :filterInaccurateData,
                                :maxAllowedAccuracy,
                                :maxAllowedSpeed,
                                :enableDuplicateDetection,
                                :duplicateDetectionThresholdMinutes
                            )
                            """)
                    .setParameter("id", sourceConfig.getId())
                    .setParameter("userId", sourceConfig.getUser().getId())
                    .setParameter("username", sourceConfig.getUsername())
                    .setParameter("passwordHash", sourceConfig.getPasswordHash())
                    .setParameter("token", sourceConfig.getToken())
                    .setParameter("deviceId", sourceConfig.getDeviceId())
                    .setParameter("payloadEncryptionSecretEncrypted", sourceConfig.getPayloadEncryptionSecretEncrypted())
                    .setParameter("payloadEncryptionSecretKeyId", sourceConfig.getPayloadEncryptionSecretKeyId())
                    .setParameter("sourceType", sourceConfig.getSourceType().name())
                    .setParameter("active", sourceConfig.isActive())
                    .setParameter("connectionType", sourceConfig.getConnectionType().name())
                    .setParameter("filterInaccurateData", sourceConfig.isFilterInaccurateData())
                    .setParameter("maxAllowedAccuracy", sourceConfig.getMaxAllowedAccuracy())
                    .setParameter("maxAllowedSpeed", sourceConfig.getMaxAllowedSpeed())
                    .setParameter("enableDuplicateDetection", sourceConfig.isEnableDuplicateDetection())
                    .setParameter("duplicateDetectionThresholdMinutes", sourceConfig.getDuplicateDetectionThresholdMinutes())
                    .executeUpdate();
        }
    }

    private void applyLocationSourceDto(GpsSourceConfigEntity target,
                                        UserEntity user,
                                        LocationSourcesDataDto.SourceDto sourceDto,
                                        org.github.tess1o.geopulse.shared.gps.GpsSourceType sourceType) {
        target.setUser(user);
        target.setSourceType(sourceType);
        target.setUsername(sourceDto.getUsername());
        target.setPasswordHash(sourceDto.getPasswordHash());
        target.setToken(sourceDto.getToken());
        target.setDeviceId(sourceDto.getDeviceId());
        target.setActive(sourceDto.isActive());
        target.setConnectionType(parseConnectionType(sourceDto.getConnectionType()));
        target.setFilterInaccurateData(sourceDto.isFilterInaccurateData());
        target.setMaxAllowedAccuracy(sourceDto.getMaxAllowedAccuracy());
        target.setMaxAllowedSpeed(sourceDto.getMaxAllowedSpeed());
        target.setEnableDuplicateDetection(sourceDto.isEnableDuplicateDetection());
        target.setDuplicateDetectionThresholdMinutes(sourceDto.getDuplicateDetectionThresholdMinutes());
        if (sourceDto.getPayloadEncryptionSecret() == null || sourceDto.getPayloadEncryptionSecret().isBlank()) {
            target.setPayloadEncryptionSecretEncrypted(null);
            target.setPayloadEncryptionSecretKeyId(null);
        } else {
            target.setPayloadEncryptionSecretEncrypted(encryptionService.encrypt(sourceDto.getPayloadEncryptionSecret()));
            target.setPayloadEncryptionSecretKeyId(encryptionService.getCurrentKeyId());
        }
    }

    private GpsSourceConfigEntity.ConnectionType parseConnectionType(String connectionType) {
        if (connectionType == null || connectionType.isBlank()) {
            return GpsSourceConfigEntity.ConnectionType.HTTP;
        }
        return GpsSourceConfigEntity.ConnectionType.valueOf(connectionType);
    }

    public void importReverseGeocodingData(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        try {
            QuarkusTransaction.requiringNew()
                    .call(() -> {
                        importReverseGeocodingDataInTransaction(content, job, referenceMaps);
                        return null;
                    });
        } catch (QuarkusTransactionException e) {
            throw ImportTransactionExceptions.unwrapIOExceptionOrThrowRuntime(e);
        }
    }

    private void importReverseGeocodingDataInTransaction(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        ReverseGeocodingDataDto geocodingData = objectMapper.readValue(content, ReverseGeocodingDataDto.class);
        log.info("Importing {} reverse geocoding locations for user {} with user assignment logic",
                geocodingData.getLocations().size(), job.getUserId());

        // Get user entity
        UserEntity importingUser = userRepository.findById(job.getUserId());
        if (importingUser == null) {
            throw new IllegalStateException("User not found: " + job.getUserId());
        }

        int imported = 0;
        int skipped = 0;

        for (ReverseGeocodingDataDto.ReverseGeocodingLocationDto locationDto : geocodingData.getLocations()) {
            try {
                // Create geometry objects for coordinates and bounding box
                Point requestCoordinates = null;
                if (locationDto.getRequestLatitude() != null && locationDto.getRequestLongitude() != null) {
                    requestCoordinates = GeoUtils.createPoint(
                            locationDto.getRequestLongitude(), locationDto.getRequestLatitude());
                }

                Point resultCoordinates = null;
                if (locationDto.getResultLatitude() != null && locationDto.getResultLongitude() != null) {
                    resultCoordinates = GeoUtils.createPoint(
                            locationDto.getResultLongitude(), locationDto.getResultLatitude());
                }

                Polygon boundingBox = null;
                if (locationDto.getBoundingBoxNorthEastLatitude() != null &&
                        locationDto.getBoundingBoxNorthEastLongitude() != null &&
                        locationDto.getBoundingBoxSouthWestLatitude() != null &&
                        locationDto.getBoundingBoxSouthWestLongitude() != null) {
                    boundingBox = GeoUtils.buildBoundingBoxPolygon(
                            locationDto.getBoundingBoxSouthWestLatitude(),
                            locationDto.getBoundingBoxNorthEastLatitude(),
                            locationDto.getBoundingBoxSouthWestLongitude(),
                            locationDto.getBoundingBoxNorthEastLongitude()
                    );
                }

                ReverseGeocodingLocationEntity existing = findExistingUserGeocoding(job.getUserId(), requestCoordinates);
                ReverseGeocodingLocationEntity entity = existing == null ? new ReverseGeocodingLocationEntity() : existing;

                entity.setUser(importingUser);
                entity.setRequestCoordinates(requestCoordinates);
                entity.setResultCoordinates(resultCoordinates);
                entity.setBoundingBox(boundingBox);
                entity.setDisplayName(locationDto.getDisplayName());
                entity.setProviderName(locationDto.getProviderName());
                entity.setCreatedAt(defaultInstant(locationDto.getCreatedAt()));
                entity.setLastAccessedAt(defaultInstant(locationDto.getLastAccessedAt()));
                entity.setCity(locationDto.getCity());
                entity.setCountry(locationDto.getCountry());
                entityManager.persist(entity);
                entityManager.flush();
                referenceMaps.geocodingIds.put(locationDto.getId(), entity.getId());

                if (existing == null) {
                    imported++;
                    log.debug("Imported user-specific geocoding entity (assigned to user {})", job.getUserId());
                } else {
                    skipped++;
                    log.debug("Updated existing user-specific geocoding entity {}", existing.getId());
                }

            } catch (Exception e) {
                log.warn("Failed to import reverse geocoding location at ({}, {}): {}",
                        locationDto.getRequestLatitude(), locationDto.getRequestLongitude(), e.getMessage());
            }
        }

        log.info("Successfully imported {} reverse geocoding locations (skipped {} existing originals)",
                imported, skipped);
    }

    @Transactional
    public void importPeriodTagsData(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        PeriodTagsDataDto data = objectMapper.readValue(content, PeriodTagsDataDto.class);
        UserEntity user = getImportingUser(job);

        int imported = 0;
        int updated = 0;
        List<PeriodTagsDataDto.PeriodTagDto> periodTags = emptyIfNull(data.getPeriodTags());
        for (PeriodTagsDataDto.PeriodTagDto dto : periodTags) {
            if (dto.getTagName() == null || dto.getStartTime() == null || shouldSkipDueToDateFilter(dto.getStartTime(), job)) {
                continue;
            }

            PeriodTagEntity entity = entityManager.createQuery("""
                            SELECT tag FROM PeriodTagEntity tag
                            WHERE tag.user.id = :userId
                              AND tag.tagName = :name
                              AND tag.startTime = :startTime
                            """, PeriodTagEntity.class)
                    .setParameter("userId", job.getUserId())
                    .setParameter("name", dto.getTagName())
                    .setParameter("startTime", dto.getStartTime())
                    .getResultStream()
                    .findFirst()
                    .orElse(null);

            boolean isNew = entity == null;
            if (isNew) {
                entity = new PeriodTagEntity();
                entity.setUser(user);
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }

            entity.setTagName(dto.getTagName());
            entity.setStartTime(dto.getStartTime());
            entity.setEndTime(dto.getEndTime());
            entity.setSource(dto.getSource());
            entity.setIsActive(Boolean.TRUE.equals(dto.getActive()));
            entity.setColor(dto.getColor());
            entity.setShowAsPreset(dto.getShowAsPreset() == null ? Boolean.TRUE : dto.getShowAsPreset());
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
            entityManager.flush();

            if (dto.getId() != null) {
                referenceMaps.periodTagIds.put(dto.getId(), entity.getId());
            }
            if (isNew) {
                imported++;
            } else {
                updated++;
            }
        }
        log.info("Imported {} and updated {} period tags for user {}", imported, updated, job.getUserId());
    }

    @Transactional
    public void importTimelineOverridesData(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        TimelineOverridesDataDto data = objectMapper.readValue(content, TimelineOverridesDataDto.class);
        UserEntity user = getImportingUser(job);

        int tripOverrides = 0;
        for (TimelineOverridesDataDto.TripMovementOverrideDto dto : emptyIfNull(data.getTripMovementOverrides())) {
            if (dto.getSourceTripTimestamp() == null || shouldSkipDueToDateFilter(dto.getSourceTripTimestamp(), job)) {
                continue;
            }
            TimelineTripMovementOverrideEntity entity = entityManager.createQuery("""
                            SELECT override FROM TimelineTripMovementOverrideEntity override
                            WHERE override.user.id = :userId
                              AND override.sourceTripTimestamp = :timestamp
                              AND override.sourceTripDurationSeconds = :duration
                            """, TimelineTripMovementOverrideEntity.class)
                    .setParameter("userId", job.getUserId())
                    .setParameter("timestamp", dto.getSourceTripTimestamp())
                    .setParameter("duration", dto.getSourceTripDurationSeconds() == null ? 0L : dto.getSourceTripDurationSeconds())
                    .getResultStream()
                    .findFirst()
                    .orElseGet(TimelineTripMovementOverrideEntity::new);

            entity.setUser(user);
            entity.setMovementType(dto.getMovementType());
            Long mappedTripId = resolveMappedId(dto.getTripId(), referenceMaps.timelineTripIds);
            entity.setTrip(mappedTripId == null ? null : entityManager.getReference(TimelineTripEntity.class, mappedTripId));
            entity.setSourceTripTimestamp(dto.getSourceTripTimestamp());
            entity.setSourceTripDurationSeconds(dto.getSourceTripDurationSeconds() == null ? 0L : dto.getSourceTripDurationSeconds());
            entity.setSourceDistanceMeters(dto.getSourceDistanceMeters() == null ? 0L : dto.getSourceDistanceMeters());
            entity.setSourceStartLatitude(defaultDouble(dto.getSourceStartLatitude()));
            entity.setSourceStartLongitude(defaultDouble(dto.getSourceStartLongitude()));
            entity.setSourceEndLatitude(defaultDouble(dto.getSourceEndLatitude()));
            entity.setSourceEndLongitude(defaultDouble(dto.getSourceEndLongitude()));
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
            tripOverrides++;
        }

        int gapOverrides = 0;
        for (TimelineOverridesDataDto.DataGapStayOverrideDto dto : emptyIfNull(data.getDataGapStayOverrides())) {
            if (dto.getSourceGapStartTime() == null || shouldSkipDueToDateFilter(dto.getSourceGapStartTime(), job)) {
                continue;
            }
            TimelineDataGapStayOverrideEntity entity = entityManager.createQuery("""
                            SELECT override FROM TimelineDataGapStayOverrideEntity override
                            WHERE override.user.id = :userId
                              AND override.sourceGapStartTime = :startTime
                              AND override.sourceGapEndTime = :endTime
                            """, TimelineDataGapStayOverrideEntity.class)
                    .setParameter("userId", job.getUserId())
                    .setParameter("startTime", dto.getSourceGapStartTime())
                    .setParameter("endTime", dto.getSourceGapEndTime())
                    .getResultStream()
                    .findFirst()
                    .orElseGet(TimelineDataGapStayOverrideEntity::new);

            entity.setUser(user);
            Long mappedDataGapId = resolveMappedId(dto.getDataGapId(), referenceMaps.timelineDataGapIds);
            Long mappedStayId = resolveMappedId(dto.getStayId(), referenceMaps.timelineStayIds);
            entity.setDataGap(mappedDataGapId == null ? null : entityManager.getReference(TimelineDataGapEntity.class, mappedDataGapId));
            entity.setStay(mappedStayId == null ? null : entityManager.getReference(TimelineStayEntity.class, mappedStayId));
            entity.setLocationStrategy(parseEnum(DataGapStayOverrideLocationStrategy.class, dto.getLocationStrategy(), DataGapStayOverrideLocationStrategy.SELECTED_LOCATION));
            entity.setSelectedFavoriteId(resolveMappedId(dto.getSelectedFavoriteId(), referenceMaps.favoriteIds));
            entity.setSelectedGeocodingId(resolveMappedId(dto.getSelectedGeocodingId(), referenceMaps.geocodingIds));
            entity.setSelectedLatitude(dto.getSelectedLatitude());
            entity.setSelectedLongitude(dto.getSelectedLongitude());
            entity.setSelectedLocationName(dto.getSelectedLocationName());
            entity.setSourceGapStartTime(dto.getSourceGapStartTime());
            entity.setSourceGapEndTime(dto.getSourceGapEndTime());
            entity.setSourceGapDurationSeconds(dto.getSourceGapDurationSeconds() == null ? 0L : dto.getSourceGapDurationSeconds());
            entity.setSourceBeforeLatitude(defaultDouble(dto.getSourceBeforeLatitude()));
            entity.setSourceBeforeLongitude(defaultDouble(dto.getSourceBeforeLongitude()));
            entity.setSourceAfterLatitude(defaultDouble(dto.getSourceAfterLatitude()));
            entity.setSourceAfterLongitude(defaultDouble(dto.getSourceAfterLongitude()));
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
            gapOverrides++;
        }
        log.info("Imported/updated {} trip overrides and {} gap overrides for user {}",
                tripOverrides, gapOverrides, job.getUserId());
    }

    @Transactional
    public void importNotificationTemplatesData(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        NotificationTemplatesDataDto data = objectMapper.readValue(content, NotificationTemplatesDataDto.class);
        UserEntity user = getImportingUser(job);

        List<NotificationTemplatesDataDto.NotificationTemplateDto> templates = emptyIfNull(data.getTemplates());
        for (NotificationTemplatesDataDto.NotificationTemplateDto dto : templates) {
            if (dto.getName() == null) {
                continue;
            }
            NotificationTemplateEntity entity = entityManager.createQuery("""
                            SELECT template FROM NotificationTemplateEntity template
                            WHERE template.user.id = :userId AND template.name = :name
                            """, NotificationTemplateEntity.class)
                    .setParameter("userId", job.getUserId())
                    .setParameter("name", dto.getName())
                    .getResultStream()
                    .findFirst()
                    .orElseGet(NotificationTemplateEntity::new);

            entity.setUser(user);
            entity.setName(dto.getName());
            entity.setDestination(dto.getDestination());
            entity.setExternalRoutingMode(parseEnum(AppriseExternalRoutingMode.class, dto.getExternalRoutingMode(), AppriseExternalRoutingMode.URLS));
            entity.setAppriseConfigKey(dto.getAppriseConfigKey());
            entity.setAppriseTag(dto.getAppriseTag());
            entity.setTitleTemplate(dto.getTitleTemplate());
            entity.setBodyTemplate(dto.getBodyTemplate());
            entity.setDefaultForEnter(Boolean.TRUE.equals(dto.getDefaultForEnter()));
            entity.setDefaultForLeave(Boolean.TRUE.equals(dto.getDefaultForLeave()));
            entity.setEnabled(dto.getEnabled() == null || dto.getEnabled());
            entity.setSendInApp(dto.getSendInApp() == null || dto.getSendInApp());
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
            entityManager.flush();

            if (dto.getId() != null) {
                referenceMaps.notificationTemplateIds.put(dto.getId(), entity.getId());
            }
        }
        log.info("Imported/updated {} notification templates for user {}", templates.size(), job.getUserId());
    }

    @Transactional
    public void importTripWorkspaceData(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        TripWorkspaceDataDto data = objectMapper.readValue(content, TripWorkspaceDataDto.class);
        UserEntity user = getImportingUser(job);

        List<TripWorkspaceDataDto.TripDto> trips = emptyIfNull(data.getTrips());
        for (TripWorkspaceDataDto.TripDto dto : trips) {
            if (dto.getName() == null || (dto.getStartTime() != null && shouldSkipDueToDateFilter(dto.getStartTime(), job))) {
                continue;
            }
            TripEntity entity = findExistingTrip(job.getUserId(), dto)
                    .orElseGet(TripEntity::new);

            entity.setUser(user);
            Long mappedPeriodTagId = dto.getPeriodTagId() == null ? null : referenceMaps.periodTagIds.get(dto.getPeriodTagId());
            entity.setPeriodTag(mappedPeriodTagId == null ? null : entityManager.getReference(PeriodTagEntity.class, mappedPeriodTagId));
            entity.setName(dto.getName());
            entity.setStartTime(dto.getStartTime());
            entity.setEndTime(dto.getEndTime());
            entity.setStatus(parseEnum(TripStatus.class, dto.getStatus(), TripStatus.UPCOMING));
            entity.setColor(dto.getColor());
            entity.setNotes(dto.getNotes());
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
            entityManager.flush();

            if (dto.getId() != null) {
                referenceMaps.tripIds.put(dto.getId(), entity.getId());
            }
            importTripPlanItems(entity, dto.getPlanItems());
            importTripCollaborators(entity, dto.getCollaborators());
        }
        log.info("Imported/updated {} trip workspace records for user {}", trips.size(), job.getUserId());
    }

    private void importTripPlanItems(TripEntity trip, List<TripWorkspaceDataDto.TripPlanItemDto> items) {
        if (items == null) {
            return;
        }
        for (TripWorkspaceDataDto.TripPlanItemDto dto : items) {
            TripPlanItemEntity entity = entityManager.createQuery("""
                            SELECT item FROM TripPlanItemEntity item
                            WHERE item.trip.id = :tripId AND item.title = :title AND item.orderIndex = :orderIndex
                            """, TripPlanItemEntity.class)
                    .setParameter("tripId", trip.getId())
                    .setParameter("title", dto.getTitle())
                    .setParameter("orderIndex", dto.getOrderIndex() == null ? 0 : dto.getOrderIndex())
                    .getResultStream()
                    .findFirst()
                    .orElseGet(TripPlanItemEntity::new);
            entity.setTrip(trip);
            entity.setTitle(dto.getTitle());
            entity.setNotes(dto.getNotes());
            entity.setLatitude(dto.getLatitude());
            entity.setLongitude(dto.getLongitude());
            entity.setPlannedDay(dto.getPlannedDay());
            entity.setPriority(parseEnum(TripPlanItemPriority.class, dto.getPriority(), TripPlanItemPriority.OPTIONAL));
            entity.setOrderIndex(dto.getOrderIndex() == null ? 0 : dto.getOrderIndex());
            entity.setIsVisited(Boolean.TRUE.equals(dto.getVisited()));
            entity.setVisitConfidence(dto.getVisitConfidence());
            entity.setVisitSource(parseEnum(TripPlanItemVisitSource.class, dto.getVisitSource(), null));
            entity.setVisitedAt(dto.getVisitedAt());
            entity.setManualOverrideState(parseEnum(TripPlanItemOverrideState.class, dto.getManualOverrideState(), null));
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
        }
    }

    private void importTripCollaborators(TripEntity trip, List<TripWorkspaceDataDto.TripCollaboratorDto> collaborators) {
        if (collaborators == null) {
            return;
        }
        for (TripWorkspaceDataDto.TripCollaboratorDto dto : collaborators) {
            if (dto.getEmail() == null) {
                continue;
            }
            Optional<UserEntity> collaboratorUser = userRepository.findByEmailIgnoreCase(dto.getEmail());
            if (collaboratorUser.isEmpty() || collaboratorUser.get().getId().equals(trip.getUser().getId())) {
                continue;
            }
            boolean exists = entityManager.createQuery("""
                            SELECT COUNT(collaborator) FROM TripCollaboratorEntity collaborator
                            WHERE collaborator.trip.id = :tripId AND collaborator.collaborator.id = :collaboratorId
                            """, Long.class)
                    .setParameter("tripId", trip.getId())
                    .setParameter("collaboratorId", collaboratorUser.get().getId())
                    .getSingleResult() > 0;
            if (!exists) {
                TripCollaboratorEntity entity = TripCollaboratorEntity.builder()
                        .trip(trip)
                        .collaborator(collaboratorUser.get())
                        .accessRole(parseEnum(TripCollaboratorAccessRole.class, dto.getAccessRole(), TripCollaboratorAccessRole.VIEW))
                        .createdAt(defaultInstant(dto.getCreatedAt()))
                        .updatedAt(Instant.now())
                        .build();
                entityManager.persist(entity);
            }
        }
    }

    @Transactional
    public void importGeofencingData(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        GeofencingDataDto data = objectMapper.readValue(content, GeofencingDataDto.class);
        UserEntity owner = getImportingUser(job);

        List<GeofencingDataDto.GeofenceRuleDto> rules = emptyIfNull(data.getRules());
        for (GeofencingDataDto.GeofenceRuleDto dto : rules) {
            if (dto.getName() == null) {
                continue;
            }
            GeofenceRuleEntity entity = entityManager.createQuery("""
                            SELECT rule FROM GeofenceRuleEntity rule
                            WHERE rule.ownerUser.id = :userId AND rule.name = :name
                            """, GeofenceRuleEntity.class)
                    .setParameter("userId", job.getUserId())
                    .setParameter("name", dto.getName())
                    .getResultStream()
                    .findFirst()
                    .orElseGet(GeofenceRuleEntity::new);
            entity.setOwnerUser(owner);
            entity.setName(dto.getName());
            entity.setNorthEastLat(dto.getNorthEastLat());
            entity.setNorthEastLon(dto.getNorthEastLon());
            entity.setSouthWestLat(dto.getSouthWestLat());
            entity.setSouthWestLon(dto.getSouthWestLon());
            entity.setMonitorEnter(dto.getMonitorEnter() == null || dto.getMonitorEnter());
            entity.setMonitorLeave(dto.getMonitorLeave() == null || dto.getMonitorLeave());
            entity.setCooldownSeconds(dto.getCooldownSeconds() == null ? 120 : dto.getCooldownSeconds());
            entity.setEnterTemplate(resolveTemplate(dto.getEnterTemplateId(), referenceMaps));
            entity.setLeaveTemplate(resolveTemplate(dto.getLeaveTemplateId(), referenceMaps));
            entity.setStatus(parseEnum(GeofenceRuleStatus.class, dto.getStatus(), GeofenceRuleStatus.ACTIVE));
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
            entityManager.flush();

            importGeofenceSubjects(entity, dto.getSubjects());
        }
        log.info("Imported/updated {} geofence rules for user {}", rules.size(), job.getUserId());
    }

    private NotificationTemplateEntity resolveTemplate(Long oldTemplateId, ImportReferenceMaps referenceMaps) {
        if (oldTemplateId == null) {
            return null;
        }
        Long mappedId = referenceMaps.notificationTemplateIds.get(oldTemplateId);
        return mappedId == null ? null : entityManager.getReference(NotificationTemplateEntity.class, mappedId);
    }

    private void importGeofenceSubjects(GeofenceRuleEntity rule, List<GeofencingDataDto.SubjectDto> subjects) {
        if (subjects == null) {
            return;
        }
        for (GeofencingDataDto.SubjectDto dto : subjects) {
            UserEntity subject = dto.getEmail() == null
                    ? null
                    : userRepository.findByEmailIgnoreCase(dto.getEmail()).orElse(null);
            if (subject == null) {
                continue;
            }
            boolean exists = entityManager.createQuery("""
                            SELECT COUNT(subject) FROM GeofenceRuleSubjectEntity subject
                            WHERE subject.rule.id = :ruleId AND subject.subjectUser.id = :subjectUserId
                            """, Long.class)
                    .setParameter("ruleId", rule.getId())
                    .setParameter("subjectUserId", subject.getId())
                    .getSingleResult() > 0;
            if (!exists) {
                GeofenceRuleSubjectEntity entity = GeofenceRuleSubjectEntity.builder()
                        .id(new GeofenceRuleSubjectId(rule.getId(), subject.getId()))
                        .rule(rule)
                        .subjectUser(subject)
                        .createdAt(defaultInstant(dto.getCreatedAt()))
                        .build();
                entityManager.persist(entity);
            }
        }
    }

    @Transactional
    public void importNotesData(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        NotesDataDto data = objectMapper.readValue(content, NotesDataDto.class);
        UserEntity user = getImportingUser(job);

        List<NotesDataDto.NoteDto> notes = emptyIfNull(data.getNotes());
        for (NotesDataDto.NoteDto dto : notes) {
            if (dto.getContentMarkdown() == null || dto.getEventTime() == null || shouldSkipDueToDateFilter(dto.getEventTime(), job)) {
                continue;
            }
            TimelineNoteEntity entity = entityManager.createQuery("""
                            SELECT note FROM TimelineNoteEntity note
                            WHERE note.user.id = :userId
                              AND note.eventTime = :eventTime
                              AND note.contentMarkdown = :content
                            """, TimelineNoteEntity.class)
                    .setParameter("userId", job.getUserId())
                    .setParameter("eventTime", dto.getEventTime())
                    .setParameter("content", dto.getContentMarkdown())
                    .getResultStream()
                    .findFirst()
                    .orElseGet(TimelineNoteEntity::new);
            entity.setUser(user);
            entity.setTitle(dto.getTitle());
            entity.setContentMarkdown(dto.getContentMarkdown());
            entity.setSnippet(dto.getSnippet());
            entity.setEventTime(dto.getEventTime());
            entity.setLocation(dto.getLatitude() != null && dto.getLongitude() != null
                    ? GeoUtils.createPoint(dto.getLongitude(), dto.getLatitude())
                    : null);
            entity.setLocationSource(parseEnum(NoteLocationSource.class, dto.getLocationSource(), NoteLocationSource.NONE));
            entity.setAnchorType(parseEnum(NoteAnchorType.class, dto.getAnchorType(), NoteAnchorType.TIMESTAMP));
            Long mappedStayId = resolveMappedId(dto.getStayId(), referenceMaps.timelineStayIds);
            Long mappedTripId = resolveMappedId(dto.getTripId(), referenceMaps.timelineTripIds);
            entity.setStay(mappedStayId == null ? null : entityManager.getReference(TimelineStayEntity.class, mappedStayId));
            entity.setTrip(mappedTripId == null ? null : entityManager.getReference(TimelineTripEntity.class, mappedTripId));
            entity.setSourceItemStartTime(dto.getSourceItemStartTime());
            entity.setSourceItemDurationSeconds(dto.getSourceItemDurationSeconds());
            entity.setSourceStartLatitude(dto.getSourceStartLatitude());
            entity.setSourceStartLongitude(dto.getSourceStartLongitude());
            entity.setSourceEndLatitude(dto.getSourceEndLatitude());
            entity.setSourceEndLongitude(dto.getSourceEndLongitude());
            entity.setSourceDistanceMeters(dto.getSourceDistanceMeters());
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entity.setDeletedAt(dto.getDeletedAt());
            entityManager.persist(entity);
        }
        log.info("Imported/updated {} notes for user {}", notes.size(), job.getUserId());
    }

    @Transactional
    public void importWeatherSamplesData(byte[] content, ImportJob job) throws IOException {
        WeatherSamplesDataDto data = objectMapper.readValue(content, WeatherSamplesDataDto.class);
        UserEntity user = getImportingUser(job);

        List<WeatherSamplesDataDto.WeatherSampleDto> samples = emptyIfNull(data.getSamples());
        for (WeatherSamplesDataDto.WeatherSampleDto dto : samples) {
            if (dto.getProvider() == null || dto.getObservedAt() == null || shouldSkipDueToDateFilter(dto.getObservedAt(), job)) {
                continue;
            }
            WeatherSampleEntity entity = entityManager.createQuery("""
                            SELECT sample FROM WeatherSampleEntity sample
                            WHERE sample.user.id = :userId
                              AND sample.provider = :provider
                              AND sample.latitudeBucket = :latBucket
                              AND sample.longitudeBucket = :lonBucket
                              AND sample.observedAt = :observedAt
                            """, WeatherSampleEntity.class)
                    .setParameter("userId", job.getUserId())
                    .setParameter("provider", dto.getProvider())
                    .setParameter("latBucket", defaultDouble(dto.getLatitudeBucket()))
                    .setParameter("lonBucket", defaultDouble(dto.getLongitudeBucket()))
                    .setParameter("observedAt", dto.getObservedAt())
                    .getResultStream()
                    .findFirst()
                    .orElseGet(WeatherSampleEntity::new);
            entity.setUser(user);
            entity.setProvider(dto.getProvider());
            entity.setSource(parseEnum(WeatherTargetSource.class, dto.getSource(), WeatherTargetSource.IMPORT_BACKFILL));
            entity.setRequestedLatitude(defaultDouble(dto.getRequestedLatitude()));
            entity.setRequestedLongitude(defaultDouble(dto.getRequestedLongitude()));
            entity.setProviderLatitude(dto.getProviderLatitude());
            entity.setProviderLongitude(dto.getProviderLongitude());
            entity.setLatitudeBucket(defaultDouble(dto.getLatitudeBucket()));
            entity.setLongitudeBucket(defaultDouble(dto.getLongitudeBucket()));
            entity.setObservedAt(dto.getObservedAt());
            entity.setFetchedAt(defaultInstant(dto.getFetchedAt()));
            entity.setTimezone(dto.getTimezone());
            entity.setWeatherCode(dto.getWeatherCode());
            entity.setTemperature(dto.getTemperature());
            entity.setApparentTemperature(dto.getApparentTemperature());
            entity.setHumidity(dto.getHumidity());
            entity.setPrecipitation(dto.getPrecipitation());
            entity.setRain(dto.getRain());
            entity.setSnowfall(dto.getSnowfall());
            entity.setCloudCover(dto.getCloudCover());
            entity.setWindSpeed(dto.getWindSpeed());
            entity.setWindGust(dto.getWindGust());
            entity.setWindDirection(dto.getWindDirection());
            entity.setPressure(dto.getPressure());
            entity.setRawData(dto.getRawData());
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
        }
        log.info("Imported/updated {} weather samples for user {}", samples.size(), job.getUserId());
    }

    @Transactional
    public void importMapMatchingData(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        MapMatchingDataDto data = objectMapper.readValue(content, MapMatchingDataDto.class);
        UserEntity user = getImportingUser(job);

        List<MapMatchingDataDto.PathMatchDto> pathMatches = emptyIfNull(data.getPathMatches());
        for (MapMatchingDataDto.PathMatchDto dto : pathMatches) {
            if (dto.getProvider() == null
                    || dto.getProfile() == null
                    || dto.getConfigHash() == null
                    || dto.getInputHash() == null
                    || dto.getMatchedSegmentsJson() == null
                    || !MapMatchingStatus.MATCHED.name().equals(dto.getStatus())
                    || shouldSkipDueToDateFilter(dto.getTripTimestamp(), job)) {
                continue;
            }

            TimelineTripPathMatchEntity entity = findExistingMapMatchingPathMatch(job.getUserId(), dto)
                    .orElseGet(TimelineTripPathMatchEntity::new);
            entity.setUser(user);
            Long mappedTripId = resolveMappedId(dto.getTripId(), referenceMaps.timelineTripIds);
            entity.setTrip(mappedTripId == null ? null : entityManager.getReference(TimelineTripEntity.class, mappedTripId));
            entity.setProvider(dto.getProvider());
            entity.setProfile(dto.getProfile());
            entity.setConfigHash(dto.getConfigHash());
            entity.setInputHash(dto.getInputHash());
            entity.setStatus(MapMatchingStatus.MATCHED);
            entity.setAttempts(dto.getAttempts() == null ? 0 : dto.getAttempts());
            entity.setNextAttemptAt(defaultInstant(dto.getNextAttemptAt()));
            entity.setLastAttemptAt(dto.getLastAttemptAt());
            entity.setLockedAt(null);
            entity.setCompletedAt(defaultInstant(dto.getCompletedAt()));
            entity.setLastError(null);
            entity.setMatchedSegmentsJson(dto.getMatchedSegmentsJson());
            entity.setSource(dto.getSource() == null ? "ON_DEMAND" : dto.getSource());
            entity.setPriority(dto.getPriority() == null ? 100 : dto.getPriority());
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
        }
        log.info("Imported/updated {} map matching path matches for user {}", pathMatches.size(), job.getUserId());
    }

    private UserEntity getImportingUser(ImportJob job) {
        UserEntity user = userRepository.findById(job.getUserId());
        if (user == null) {
            throw new IllegalStateException("User not found: " + job.getUserId());
        }
        return user;
    }

    private ReverseGeocodingLocationEntity findExistingUserGeocoding(UUID userId, Point requestCoordinates) {
        if (requestCoordinates == null) {
            return null;
        }
        return entityManager.createQuery("""
                        SELECT location FROM ReverseGeocodingLocationEntity location
                        WHERE location.user.id = :userId
                          AND location.requestCoordinates = :requestCoordinates
                        """, ReverseGeocodingLocationEntity.class)
                .setParameter("userId", userId)
                .setParameter("requestCoordinates", requestCoordinates)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    private Optional<TripEntity> findExistingTrip(UUID userId, TripWorkspaceDataDto.TripDto dto) {
        if (dto.getStartTime() == null) {
            return entityManager.createQuery("""
                            SELECT trip FROM TripEntity trip
                            WHERE trip.user.id = :userId
                              AND trip.name = :name
                              AND trip.startTime IS NULL
                            """, TripEntity.class)
                    .setParameter("userId", userId)
                    .setParameter("name", dto.getName())
                    .getResultStream()
                    .findFirst();
        }
        return entityManager.createQuery("""
                        SELECT trip FROM TripEntity trip
                        WHERE trip.user.id = :userId
                          AND trip.name = :name
                          AND trip.startTime = :startTime
                        """, TripEntity.class)
                .setParameter("userId", userId)
                .setParameter("name", dto.getName())
                .setParameter("startTime", dto.getStartTime())
                .getResultStream()
                .findFirst();
    }

    private Optional<TimelineTripPathMatchEntity> findExistingMapMatchingPathMatch(UUID userId,
                                                                                   MapMatchingDataDto.PathMatchDto dto) {
        return entityManager.createQuery("""
                SELECT pathMatch FROM TimelineTripPathMatchEntity pathMatch
                WHERE pathMatch.user.id = :userId
                  AND pathMatch.provider = :provider
                  AND pathMatch.profile = :profile
                  AND pathMatch.configHash = :configHash
                  AND pathMatch.inputHash = :inputHash
                """, TimelineTripPathMatchEntity.class)
                .setParameter("userId", userId)
                .setParameter("provider", dto.getProvider())
                .setParameter("profile", dto.getProfile())
                .setParameter("configHash", dto.getConfigHash())
                .setParameter("inputHash", dto.getInputHash())
                .getResultStream()
                .findFirst();
    }

    @Transactional
    public void importFriendsData(byte[] content, ImportJob job) throws IOException {
        FriendsDataDto data = objectMapper.readValue(content, FriendsDataDto.class);
        int imported = 0;
        for (FriendsDataDto.FriendDto dto : emptyIfNull(data.getFriends())) {
            UserEntity user = resolveUser(dto.getUserId(), dto.getUserEmail()).orElse(null);
            UserEntity friend = resolveUser(dto.getFriendId(), dto.getFriendEmail()).orElse(null);
            if (user == null || friend == null || user.getId().equals(friend.getId())) {
                continue;
            }
            if (!friendshipRepository.existsFriendship(user.getId(), friend.getId())) {
                UserFriendEntity entity = new UserFriendEntity();
                entity.setUser(user);
                entity.setFriend(friend);
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
                entityManager.persist(entity);
                imported++;
            }
        }
        log.info("Imported {} directed friend relationships", imported);
    }

    @Transactional
    public void importFriendPermissionsData(byte[] content, ImportJob job) throws IOException {
        FriendPermissionsDataDto data = objectMapper.readValue(content, FriendPermissionsDataDto.class);
        int importedOrUpdated = 0;
        for (FriendPermissionsDataDto.FriendPermissionDto dto : emptyIfNull(data.getPermissions())) {
            UserEntity user = resolveUser(dto.getUserId(), dto.getUserEmail()).orElse(null);
            UserEntity friend = resolveUser(dto.getFriendId(), dto.getFriendEmail()).orElse(null);
            if (user == null || friend == null || user.getId().equals(friend.getId())) {
                continue;
            }

            UserFriendPermissionEntity entity = friendPermissionRepository
                    .findByUserIdAndFriendId(user.getId(), friend.getId())
                    .orElseGet(UserFriendPermissionEntity::new);
            entity.setUser(user);
            entity.setFriend(friend);
            entity.setShareTimeline(Boolean.TRUE.equals(dto.getShareTimeline()));
            entity.setShareLiveLocation(Boolean.TRUE.equals(dto.getShareLiveLocation()));
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
            importedOrUpdated++;
        }
        log.info("Imported/updated {} friend permission records", importedOrUpdated);
    }

    private Optional<UserEntity> resolveUser(UUID userId, String email) {
        if (userId != null) {
            UserEntity user = userRepository.findById(userId);
            if (user != null) {
                return Optional.of(user);
            }
        }
        return userRepository.findByEmailIgnoreCase(email);
    }

    private Long resolveMappedId(Long oldId, Map<Long, Long> mappedIds) {
        if (oldId == null) {
            return null;
        }
        return mappedIds.get(oldId);
    }

    private long durationSeconds(Instant start, Instant end, Long fallback) {
        if (fallback != null) {
            return Math.max(0L, fallback);
        }
        if (start != null && end != null && end.isAfter(start)) {
            return end.getEpochSecond() - start.getEpochSecond();
        }
        return 0L;
    }

    private Instant defaultInstant(Instant value) {
        return value == null ? Instant.now() : value;
    }

    private double defaultDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown {} value '{}', using fallback {}", enumClass.getSimpleName(), value, fallback);
            return fallback;
        }
    }

    private <T> List<T> emptyIfNull(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private static class ImportReferenceMaps {
        private final Map<Long, Long> periodTagIds = new HashMap<>();
        private final Map<Long, Long> favoriteIds = new HashMap<>();
        private final Map<Long, Long> geocodingIds = new HashMap<>();
        private final Map<Long, Long> timelineStayIds = new HashMap<>();
        private final Map<Long, Long> timelineTripIds = new HashMap<>();
        private final Map<Long, Long> timelineDataGapIds = new HashMap<>();
        private final Map<Long, Long> tripIds = new HashMap<>();
        private final Map<Long, Long> notificationTemplateIds = new HashMap<>();
    }

    /**
     * Converts a list of coordinate pairs to a JTS LineString
     *
     * @param pathCoordinates List of coordinate pairs in [longitude, latitude] format
     * @return LineString geometry or null if input is null or empty
     */
    private LineString convertPathToLineString(List<List<Double>> pathCoordinates) {
        if (pathCoordinates == null || pathCoordinates.isEmpty()) {
            return null;
        }

        Coordinate[] coordinates = pathCoordinates.stream()
                .filter(coord -> coord != null && coord.size() >= 2)
                .map(coord -> new Coordinate(coord.get(0), coord.get(1))) // [longitude, latitude]
                .toArray(Coordinate[]::new);

        if (coordinates.length < 2) {
            return null; // LineString needs at least 2 points
        }

        return geometryFactory.createLineString(coordinates);
    }

    private boolean shouldSkipDueToDateFilter(java.time.Instant timestamp, ImportJob job) {
        if (timestamp == null || job.getOptions().getDateRangeFilter() == null) {
            return false;
        }
        return timestamp.isBefore(job.getOptions().getDateRangeFilter().getStartDate()) ||
                timestamp.isAfter(job.getOptions().getDateRangeFilter().getEndDate());
    }

    /**
     * Clear existing GPS data before GeoPulse import based on the date ranges in the import file.
     * Normal imports let timeline regeneration replace derived timeline rows; snapshot
     * restores clear timeline rows up front and import them directly from the backup.
     */
    private void clearExistingDataBeforeImport(Map<String, byte[]> fileContents, ImportJob job) throws IOException {
        log.info("Clearing existing GPS data before GeoPulse import for user {}", job.getUserId());
        
        // Calculate deletion range for GPS data if present
        if (fileContents.containsKey(ExportImportConstants.FileNames.RAW_GPS_DATA)) {
            clearGpsDataForImport(fileContents.get(ExportImportConstants.FileNames.RAW_GPS_DATA), job);
        }

        if (job.getOptions().isSnapshotRestore()
                && fileContents.containsKey(ExportImportConstants.FileNames.TIMELINE_DATA)) {
            clearTimelineSnapshotData(job.getUserId());
        }
    }

    private void clearTimelineSnapshotData(UUID userId) {
        log.info("Clearing existing timeline snapshot data for user {}", userId);
        executeUserDelete("DELETE FROM timeline_notes WHERE user_id = :userId", userId);
        executeUserDelete("DELETE FROM timeline_trip_path_matches WHERE user_id = :userId", userId);
        executeUserDelete("DELETE FROM map_matching_reconciliations WHERE user_id = :userId", userId);
        executeUserDelete("DELETE FROM timeline_trip_movement_overrides WHERE user_id = :userId", userId);
        executeUserDelete("DELETE FROM timeline_data_gap_stay_overrides WHERE user_id = :userId", userId);
        executeUserDelete("DELETE FROM timeline_stays WHERE user_id = :userId", userId);
        executeUserDelete("DELETE FROM timeline_trips WHERE user_id = :userId", userId);
        executeUserDelete("DELETE FROM timeline_data_gaps WHERE user_id = :userId", userId);
    }

    private void executeUserDelete(String sql, UUID userId) {
        int deleted = entityManager.createNativeQuery(sql)
                .setParameter("userId", userId)
                .executeUpdate();
        log.debug("Deleted {} rows with snapshot cleanup SQL: {}", deleted, sql);
    }
    
    private void clearGpsDataForImport(byte[] content, ImportJob job) throws IOException {
        try {
            RawGpsDataDto gpsData = objectMapper.readValue(content, RawGpsDataDto.class);
            
            if (gpsData.getPoints().isEmpty()) {
                return;
            }
            
            // Extract date range from GPS data
            Instant minTimestamp = gpsData.getPoints().stream()
                .map(RawGpsDataDto.GpsPointDto::getTimestamp)
                .filter(timestamp -> timestamp != null)
                .min(Instant::compareTo)
                .orElse(null);
                
            Instant maxTimestamp = gpsData.getPoints().stream()
                .map(RawGpsDataDto.GpsPointDto::getTimestamp)
                .filter(timestamp -> timestamp != null)
                .max(Instant::compareTo)
                .orElse(null);
            
            if (minTimestamp != null && maxTimestamp != null) {
                ImportDataClearingService.DateRange fileDataRange = 
                    new ImportDataClearingService.DateRange(minTimestamp, maxTimestamp);
                
                ImportDataClearingService.DateRange deletionRange = 
                    dataClearingService.calculateDeletionRange(job, fileDataRange);
                
                if (deletionRange != null) {
                    int deletedCount = dataClearingService.clearGpsDataInRange(job.getUserId(), deletionRange);
                    log.info("Cleared {} existing GPS points before GeoPulse import", deletedCount);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to clear GPS data before import: {}", e.getMessage());
        }
    }
}

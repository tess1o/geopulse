package org.github.tess1o.geopulse.importdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.dto.*;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.importdata.mapper.ImportDataMapper;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.shared.exportimport.NativeSqlImportTemplates;
import org.github.tess1o.geopulse.shared.exportimport.SequenceResetService;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

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

        try (ByteArrayInputStream bais = new ByteArrayInputStream(job.getZipData());
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
                        // Timeline data will be regenerated from GPS data - skip detection
                        log.debug("Timeline data found in export - will be regenerated from GPS data");
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
        job.setProgress(100);
    }

    private Map<String, byte[]> extractZipContents(ImportJob job) throws IOException {
        Map<String, byte[]> fileContents = new HashMap<>();

        try (ByteArrayInputStream bais = new ByteArrayInputStream(job.getZipData());
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

        // 1. Import reverse geocoding locations first (no dependencies)
        if (fileContents.containsKey(ExportImportConstants.FileNames.REVERSE_GEOCODING)) {
            importReverseGeocodingData(fileContents.get(ExportImportConstants.FileNames.REVERSE_GEOCODING), job);
            totalProgress += 5;
            job.setProgress(totalProgress);
        }

        // 2. Import favorites (no dependencies)
        if (fileContents.containsKey(ExportImportConstants.FileNames.FAVORITES)) {
            importFavoritesData(fileContents.get(ExportImportConstants.FileNames.FAVORITES), job);
            totalProgress += 10;
            job.setProgress(totalProgress);
        }

        // Check if GPS data exists for import
        boolean hasGpsData = fileContents.containsKey(ExportImportConstants.FileNames.RAW_GPS_DATA);

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

        // 4. Always regenerate timeline after GPS import (skipping timeline/gaps data in export)
        if (hasGpsData && firstGpsTimestamp != null) {
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
    }

    private void validateMetadata(ZipInputStream zis, ImportJob job) throws IOException {
        byte[] content = zis.readAllBytes();
        ExportMetadataDto metadata = objectMapper.readValue(content, ExportMetadataDto.class);

        if (!ExportImportConstants.Versions.CURRENT.equals(metadata.getVersion())) {
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

    // Timeline import removed - timeline is now always regenerated from GPS data

    // Data gaps import removed - data gaps are now regenerated during timeline generation

    @Transactional
    public void importFavoritesData(byte[] content, ImportJob job) throws IOException {
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
                    importedFavorites++;
                } else {
                    // Update existing favorite with potentially better data
                    FavoritesEntity existing = duplicates.get(0);
                    updateFavoriteIfNecessary(existing, pointDto.getCity(), pointDto.getCountry());
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
                    importedFavorites++;
                } else {
                    // Update existing favorite with potentially better data
                    FavoritesEntity existing = duplicates.get(0);
                    updateFavoriteIfNecessary(existing, areaDto.getCity(), areaDto.getCountry());
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

                // Check for duplicates by user + username + type
                List<GpsSourceConfigEntity> duplicates = gpsSourceRepository.findByUserAndUsernameAndType(
                        job.getUserId(), sourceDto.getUsername(), sourceType);

                if (duplicates.isEmpty()) {
                    // Create new GPS source configuration
                    GpsSourceConfigEntity sourceConfig = new GpsSourceConfigEntity();
                    sourceConfig.setUser(user);
                    sourceConfig.setUsername(sourceDto.getUsername());
                    sourceConfig.setSourceType(sourceType);
                    sourceConfig.setActive(sourceDto.isActive());
                    
                    // Set connection type
                    GpsSourceConfigEntity.ConnectionType connectionType = 
                        sourceDto.getConnectionType() != null ? 
                        GpsSourceConfigEntity.ConnectionType.valueOf(sourceDto.getConnectionType()) : 
                        GpsSourceConfigEntity.ConnectionType.HTTP;
                    sourceConfig.setConnectionType(connectionType);

                    gpsSourceRepository.persist(sourceConfig);
                    imported++;
                } else {
                    // Update existing source configuration with potentially better data
                    GpsSourceConfigEntity existing = duplicates.get(0);
                    updateSourceConfigIfNecessary(existing, sourceDto.isActive(), 
                        sourceDto.getConnectionType());
                    skipped++;
                }
            } catch (Exception e) {
                log.warn("Failed to import GPS source '{}': {}", sourceDto.getUsername(), e.getMessage());
            }
        }

        log.info("Successfully imported {} GPS sources using duplicate detection (skipped {} duplicates)", imported, skipped);
    }

    /**
     * Update existing GPS source configuration with better data if available
     */
    private void updateSourceConfigIfNecessary(GpsSourceConfigEntity existing, boolean newActive, String newConnectionType) {
        boolean updated = false;

        // Update active status to true if new source is active and existing is not
        if (newActive && !existing.isActive()) {
            existing.setActive(true);
            updated = true;
        }

        // Update connection type if it's provided and different
        if (newConnectionType != null) {
            try {
                GpsSourceConfigEntity.ConnectionType newType = 
                    GpsSourceConfigEntity.ConnectionType.valueOf(newConnectionType);
                if (!newType.equals(existing.getConnectionType())) {
                    existing.setConnectionType(newType);
                    updated = true;
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid connection type: {}", newConnectionType);
            }
        }

        if (updated) {
            gpsSourceRepository.persist(existing);
        }
    }

    @Transactional
    public void importReverseGeocodingData(byte[] content, ImportJob job) throws IOException {
        ReverseGeocodingDataDto geocodingData = objectMapper.readValue(content, ReverseGeocodingDataDto.class);
        log.info("Importing {} reverse geocoding locations for user {} using native SQL",
                geocodingData.getLocations().size(), job.getUserId());

        int imported = 0;
        for (ReverseGeocodingDataDto.ReverseGeocodingLocationDto locationDto : geocodingData.getLocations()) {
            try {
                // Create geometry objects for coordinates and bounding box
                org.locationtech.jts.geom.Point requestCoordinates = null;
                if (locationDto.getRequestLatitude() != null && locationDto.getRequestLongitude() != null) {
                    requestCoordinates = GeoUtils.createPoint(
                            locationDto.getRequestLongitude(), locationDto.getRequestLatitude());
                }

                org.locationtech.jts.geom.Point resultCoordinates = null;
                if (locationDto.getResultLatitude() != null && locationDto.getResultLongitude() != null) {
                    resultCoordinates = GeoUtils.createPoint(
                            locationDto.getResultLongitude(), locationDto.getResultLatitude());
                }

                org.locationtech.jts.geom.Polygon boundingBox = null;
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

                entityManager.createNativeQuery(NativeSqlImportTemplates.REVERSE_GEOCODING_LOCATION_UPSERT)
                        .setParameter(1, locationDto.getId())
                        .setParameter(2, requestCoordinates)
                        .setParameter(3, resultCoordinates)
                        .setParameter(4, boundingBox)
                        .setParameter(5, locationDto.getDisplayName())
                        .setParameter(6, locationDto.getProviderName())
                        .setParameter(7, locationDto.getCreatedAt())
                        .setParameter(8, locationDto.getLastAccessedAt())
                        .setParameter(9, locationDto.getCity())
                        .setParameter(10, locationDto.getCountry())
                        .executeUpdate();
                imported++;
            } catch (Exception e) {
                log.warn("Failed to import reverse geocoding location with ID {}: {}", locationDto.getId(), e.getMessage());
            }
        }

        log.info("Successfully imported {} reverse geocoding locations using native SQL", imported);
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
        if (job.getOptions().getDateRangeFilter() == null) {
            return false;
        }
        return timestamp.isBefore(job.getOptions().getDateRangeFilter().getStartDate()) ||
                timestamp.isAfter(job.getOptions().getDateRangeFilter().getEndDate());
    }

    /**
     * Clear existing GPS data before GeoPulse import based on the date ranges in the import file.
     * Timeline data will be cleared automatically during timeline regeneration.
     */
    private void clearExistingDataBeforeImport(Map<String, byte[]> fileContents, ImportJob job) throws IOException {
        log.info("Clearing existing GPS data before GeoPulse import for user {} (timeline will be regenerated)", job.getUserId());
        
        // Calculate deletion range for GPS data if present
        if (fileContents.containsKey(ExportImportConstants.FileNames.RAW_GPS_DATA)) {
            clearGpsDataForImport(fileContents.get(ExportImportConstants.FileNames.RAW_GPS_DATA), job);
        }
        
        // Timeline data clearing is not needed - regeneration process handles it
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
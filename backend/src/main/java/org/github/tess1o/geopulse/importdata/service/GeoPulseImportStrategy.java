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
import org.github.tess1o.geopulse.importdata.mapper.ImportDataMapper;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.shared.exportimport.NativeSqlImportTemplates;
import org.github.tess1o.geopulse.shared.exportimport.SequenceResetService;
import org.github.tess1o.geopulse.timeline.model.TimelineStayEntity;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    org.github.tess1o.geopulse.timeline.service.TimelineVersionService timelineVersionService;
    
    @Inject
    org.github.tess1o.geopulse.timeline.service.TimelineInvalidationService timelineInvalidationService;
    
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
                        detectedDataTypes.add(ExportImportConstants.DataTypes.TIMELINE);
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
            totalProgress += 10;
            job.setProgress(totalProgress);
        }
        
        // 2. Import favorites (no dependencies)
        if (fileContents.containsKey(ExportImportConstants.FileNames.FAVORITES)) {
            importFavoritesData(fileContents.get(ExportImportConstants.FileNames.FAVORITES), job);
            totalProgress += 15;
            job.setProgress(totalProgress);
        }
        
        // Track whether timeline data is being imported
        boolean hasTimelineData = fileContents.containsKey(ExportImportConstants.FileNames.TIMELINE_DATA);
        boolean hasGpsData = fileContents.containsKey(ExportImportConstants.FileNames.RAW_GPS_DATA);
        
        // 3. Import timeline data (depends on favorites and reverse geocoding)
        if (hasTimelineData) {
            importTimelineData(fileContents.get(ExportImportConstants.FileNames.TIMELINE_DATA), job);
            totalProgress += 20;
            job.setProgress(totalProgress);
        }
        
        // 4. Import GPS data
        if (hasGpsData) {
            importRawGpsData(fileContents.get(ExportImportConstants.FileNames.RAW_GPS_DATA), job);
            totalProgress += 25;
            job.setProgress(totalProgress);
        }
        
        // LAYER 3 FIX: Handle GPS-only imports (timeline generation needed)
        if (hasGpsData && !hasTimelineData) {
            log.info("GeoPulse import contains GPS data but no timeline data - will trigger timeline generation");
            triggerTimelineGenerationForImportedGpsData(job);
            totalProgress += 10;
            job.setProgress(totalProgress);
        }
        
        // 5. Import user info
        if (fileContents.containsKey(ExportImportConstants.FileNames.USER_INFO)) {
            importUserInfoData(fileContents.get(ExportImportConstants.FileNames.USER_INFO), job);
            totalProgress += 10;
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
    public void importRawGpsData(byte[] content, ImportJob job) throws IOException {
        RawGpsDataDto gpsData = objectMapper.readValue(content, RawGpsDataDto.class);
        log.info("Importing {} GPS points for user {} using native SQL", gpsData.getPoints().size(), job.getUserId());

        int imported = 0;
        for (RawGpsDataDto.GpsPointDto pointDto : gpsData.getPoints()) {
            // Apply date range filter if specified
            if (job.getOptions().getDateRangeFilter() != null) {
                if (pointDto.getTimestamp().isBefore(job.getOptions().getDateRangeFilter().getStartDate()) ||
                        pointDto.getTimestamp().isAfter(job.getOptions().getDateRangeFilter().getEndDate())) {
                    continue;
                }
            }

            try {
                // Use native SQL UPSERT to preserve IDs and avoid duplicates
                org.locationtech.jts.geom.Point coordinates = null;
                if (pointDto.getLatitude() != null && pointDto.getLongitude() != null) {
                    coordinates = org.github.tess1o.geopulse.shared.geo.GeoUtils.createPoint(
                        pointDto.getLongitude(), pointDto.getLatitude());
                }
                
                entityManager.createNativeQuery(NativeSqlImportTemplates.GPS_POINTS_UPSERT)
                    .setParameter(1, pointDto.getId())
                    .setParameter(2, job.getUserId())
                    .setParameter(3, pointDto.getTimestamp())
                    .setParameter(4, coordinates)
                    .setParameter(5, pointDto.getAccuracy())
                    .setParameter(6, pointDto.getAltitude())
                    .setParameter(7, pointDto.getSpeed())
                    .setParameter(8, pointDto.getBattery())
                    .setParameter(9, pointDto.getDeviceId())
                    .setParameter(10, pointDto.getSource())
                    .setParameter(11, pointDto.getTimestamp()) // created_at - use same as timestamp
                    .executeUpdate();
                imported++;
            } catch (Exception e) {
                log.warn("Failed to import GPS point with ID {}: {}", pointDto.getId(), e.getMessage());
            }
        }
        
        log.info("Successfully imported {} GPS points using native SQL", imported);
    }

    @Transactional
    public void importTimelineData(byte[] content, ImportJob job) throws IOException {
        TimelineDataDto timelineData = objectMapper.readValue(content, TimelineDataDto.class);
        log.info("Importing {} stays and {} trips for user {} using native SQL",
                timelineData.getStays().size(), timelineData.getTrips().size(), job.getUserId());

        int importedStays = 0;
        int importedTrips = 0;

        // Import stays using native SQL UPSERT
        for (TimelineDataDto.StayDto stayDto : timelineData.getStays()) {
            if (shouldSkipDueToDateFilter(stayDto.getTimestamp(), job)) {
                continue;
            }

            try {
                // LAYER 3 FIX: Generate proper version hash instead of hardcoded "imported"
                String versionHash = timelineVersionService.generateTimelineVersion(job.getUserId(), stayDto.getTimestamp());
                
                entityManager.createNativeQuery(NativeSqlImportTemplates.TIMELINE_STAYS_UPSERT)
                    .setParameter(1, stayDto.getId())
                    .setParameter(2, job.getUserId())
                    .setParameter(3, stayDto.getTimestamp())
                    .setParameter(4, stayDto.getLatitude())
                    .setParameter(5, stayDto.getLongitude())
                    .setParameter(6, stayDto.getDuration() != null ? stayDto.getDuration() / 60 : null)
                    .setParameter(7, stayDto.getAddress())
                    .setParameter(8, "HISTORICAL")
                    .setParameter(9, stayDto.getFavoriteId())
                    .setParameter(10, stayDto.getGeocodingId())
                    .setParameter(11, versionHash) // Proper version hash
                    .setParameter(12, false) // is_stale = false for GeoPulse format (pre-computed timeline data)
                    .executeUpdate();
                importedStays++;
            } catch (Exception e) {
                log.warn("Failed to import timeline stay with ID {}: {}", stayDto.getId(), e.getMessage());
            }
        }

        // Import trips using native SQL UPSERT
        for (TimelineDataDto.TripDto tripDto : timelineData.getTrips()) {
            if (shouldSkipDueToDateFilter(tripDto.getTimestamp(), job)) {
                continue;
            }

            try {
                LineString pathGeometry = convertPathToLineString(tripDto.getPath());
                
                // LAYER 3 FIX: Generate proper version hash instead of hardcoded "imported"
                String versionHash = timelineVersionService.generateTimelineVersion(job.getUserId(), tripDto.getTimestamp());
                
                entityManager.createNativeQuery(NativeSqlImportTemplates.TIMELINE_TRIPS_UPSERT)
                    .setParameter(1, tripDto.getId())
                    .setParameter(2, job.getUserId())
                    .setParameter(3, tripDto.getTimestamp())
                    .setParameter(4, tripDto.getStartLatitude())
                    .setParameter(5, tripDto.getStartLongitude())
                    .setParameter(6, tripDto.getEndLatitude())
                    .setParameter(7, tripDto.getEndLongitude())
                    .setParameter(8, tripDto.getDistance() != null ? tripDto.getDistance() / 1000.0 : null)
                    .setParameter(9, tripDto.getDuration() != null ? tripDto.getDuration() / 60 : null)
                    .setParameter(10, tripDto.getTransportMode())
                    .setParameter(11, pathGeometry)
                    .setParameter(12, versionHash) // Proper version hash
                    .setParameter(13, false) // is_stale = false for GeoPulse format (pre-computed timeline data)
                    .executeUpdate();
                importedTrips++;
            } catch (Exception e) {
                log.warn("Failed to import timeline trip with ID {}: {}", tripDto.getId(), e.getMessage());
            }
        }
        
        log.info("Successfully imported {} stays and {} trips using native SQL", importedStays, importedTrips);
    }

    @Transactional
    public void importFavoritesData(byte[] content, ImportJob job) throws IOException {
        FavoritesDataDto favoritesData = objectMapper.readValue(content, FavoritesDataDto.class);
        log.info("Importing {} favorite points and {} favorite areas for user {} using native SQL",
                favoritesData.getPoints().size(), favoritesData.getAreas().size(), job.getUserId());

        int importedFavorites = 0;

        // Import favorite points
        for (FavoritesDataDto.FavoritePointDto pointDto : favoritesData.getPoints()) {
            try {
                entityManager.createNativeQuery(NativeSqlImportTemplates.FAVORITES_UPSERT)
                    .setParameter(1, pointDto.getId())
                    .setParameter(2, job.getUserId())
                    .setParameter(3, pointDto.getName())
                    .setParameter(4, pointDto.getCity())
                    .setParameter(5, pointDto.getCountry())
                    .setParameter(6, "POINT")
                    .setParameter(7, importDataMapper.createPointFromCoordinates(pointDto.getLongitude(), pointDto.getLatitude()))
                    .executeUpdate();
                importedFavorites++;
            } catch (Exception e) {
                log.warn("Failed to import favorite point with ID {}: {}", pointDto.getId(), e.getMessage());
            }
        }

        // Import favorite areas
        for (FavoritesDataDto.FavoriteAreaDto areaDto : favoritesData.getAreas()) {
            try {
                entityManager.createNativeQuery(NativeSqlImportTemplates.FAVORITES_UPSERT)
                    .setParameter(1, areaDto.getId())
                    .setParameter(2, job.getUserId())
                    .setParameter(3, areaDto.getName())
                    .setParameter(4, areaDto.getCity())
                    .setParameter(5, areaDto.getCountry())
                    .setParameter(6, "AREA")
                    .setParameter(7, importDataMapper.createPolygonFromCoordinates(areaDto))
                    .executeUpdate();
                importedFavorites++;
            } catch (Exception e) {
                log.warn("Failed to import favorite area with ID {}: {}", areaDto.getId(), e.getMessage());
            }
        }
        
        log.info("Successfully imported {} favorites using native SQL", importedFavorites);
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
        log.info("Importing {} location sources for user {} using native SQL", sourcesData.getSources().size(), job.getUserId());

        int imported = 0;
        for (LocationSourcesDataDto.SourceDto sourceDto : sourcesData.getSources()) {
            try {
                entityManager.createNativeQuery(NativeSqlImportTemplates.GPS_SOURCE_CONFIG_UPSERT)
                    .setParameter(1, sourceDto.getId())
                    .setParameter(2, job.getUserId())
                    .setParameter(3, sourceDto.getUsername())
                    .setParameter(4, sourceDto.getType())
                    .setParameter(5, sourceDto.isActive())
                    .executeUpdate();
                imported++;
            } catch (Exception e) {
                log.warn("Failed to import GPS source with ID {}: {}", sourceDto.getId(), e.getMessage());
            }
        }
        
        log.info("Successfully imported {} GPS sources using native SQL", imported);
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
                    requestCoordinates = org.github.tess1o.geopulse.shared.geo.GeoUtils.createPoint(
                        locationDto.getRequestLongitude(), locationDto.getRequestLatitude());
                }
                
                org.locationtech.jts.geom.Point resultCoordinates = null;
                if (locationDto.getResultLatitude() != null && locationDto.getResultLongitude() != null) {
                    resultCoordinates = org.github.tess1o.geopulse.shared.geo.GeoUtils.createPoint(
                        locationDto.getResultLongitude(), locationDto.getResultLatitude());
                }
                
                org.locationtech.jts.geom.Polygon boundingBox = null;
                if (locationDto.getBoundingBoxNorthEastLatitude() != null && 
                    locationDto.getBoundingBoxNorthEastLongitude() != null &&
                    locationDto.getBoundingBoxSouthWestLatitude() != null && 
                    locationDto.getBoundingBoxSouthWestLongitude() != null) {
                    boundingBox = org.github.tess1o.geopulse.shared.geo.GeoUtils.buildBoundingBoxPolygon(
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
     * LAYER 3 FIX: Trigger timeline generation for GPS-only imports.
     * When GeoPulse format contains GPS data but no pre-computed timeline data,
     * we need to generate timeline from the imported GPS points.
     */
    private void triggerTimelineGenerationForImportedGpsData(ImportJob job) {
        log.info("Triggering timeline generation for GPS-only GeoPulse import for user {}", job.getUserId());
        
        try {
            // Create a dummy timeline stay entity to trigger full user regeneration
            // This uses the same pattern as TimelineInvalidationService for full user regeneration
            UserEntity user = userRepository.findById(job.getUserId());
            if (user == null) {
                log.error("User not found for GPS timeline generation: {}", job.getUserId());
                return;
            }
            
            // Create a temporary stay entity to represent the need for timeline generation
            // We'll use a very old timestamp to ensure it gets processed
            TimelineStayEntity dummyStay = new TimelineStayEntity();
            dummyStay.setUser(user);
            dummyStay.setTimestamp(Instant.EPOCH); // Special marker for full regeneration
            dummyStay.setLatitude(0.0);
            dummyStay.setLongitude(0.0);
            dummyStay.setLocationName("GPS Import Trigger");
            dummyStay.setStayDuration(0);
            dummyStay.setLocationSource(org.github.tess1o.geopulse.timeline.model.LocationSource.HISTORICAL);
            dummyStay.setTimelineVersion("gps-import-trigger");
            dummyStay.setIsStale(true);
            dummyStay.setCreatedAt(Instant.now());
            dummyStay.setLastUpdated(Instant.now());
            
            // Use the invalidation service to queue timeline generation
            timelineInvalidationService.markStaleAndQueue(List.of(dummyStay));
            
            log.info("Successfully queued timeline generation for GPS-only import for user {}", job.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to trigger timeline generation for GPS-only import for user {}: {}", 
                     job.getUserId(), e.getMessage(), e);
            // Don't fail the entire import - GPS data is still imported successfully
        }
    }
}
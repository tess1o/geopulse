package org.github.tess1o.geopulse.importdata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.favorites.model.AddAreaToFavoritesDto;
import org.github.tess1o.geopulse.favorites.model.AddPointToFavoritesDto;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.importdata.model.DebugImportRequest;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.user.model.UpdateTimelinePreferencesRequest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@ApplicationScoped
@Slf4j
public class DebugImportService {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    TimelineTripRepository timelineTripRepository;

    @Inject
    FavoriteLocationService favoriteLocationService;

    @Inject
    UserService userService;

    @Inject
    GpsPointMapper gpsPointMapper;

    @Inject
    StreamingTimelineGenerationService timelineGenerationService;

    @Inject
    EntityManager entityManager;

    @Inject
    org.github.tess1o.geopulse.user.mapper.TimelinePreferencesMapper timelinePreferencesMapper;

    /**
     * Import debug data from ZIP file.
     * This is an all-or-nothing operation - uses transaction to rollback on any failure.
     */
    @Transactional
    public void importDebugData(UUID userId, byte[] zipData, DebugImportRequest request) throws IOException {
        log.info("Starting debug import for user {}", userId);

        // 1. Validate ZIP and extract contents
        DebugImportData data = extractZipContents(zipData);

        // 2. Optionally clear existing data
        if (request.isClearExistingData()) {
            clearUserData(userId);
        }

        // 3. Import GPS points
        importGpsPoints(userId, data.gpsData);

        // 4. Import favorite locations
        importFavoriteLocations(userId, data.favoriteLocations);

        // 5. Import favorite areas
        importFavoriteAreas(userId, data.favoriteAreas);

        // 6. Optionally update timeline configuration
        if (request.isUpdateTimelineConfig() && data.timelineConfig != null) {
            updateTimelineConfig(userId, data.timelineConfig);
        }

        // 7. Regenerate timeline
        timelineGenerationService.regenerateFullTimeline(userId);

        log.info("Debug import completed successfully for user {}", userId);
    }

    private DebugImportData extractZipContents(byte[] zipData) throws IOException {
        DebugImportData data = new DebugImportData();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String fileName = entry.getName();
                byte[] content = zis.readAllBytes();

                switch (fileName) {
                    case "metadata.json":
                        data.metadata = objectMapper.readTree(content);
                        log.info("Extracted metadata: {} GPS points, {} locations, {} areas",
                                data.metadata.get("gps_point_count").asLong(),
                                data.metadata.get("favorite_locations_count").asInt(),
                                data.metadata.get("favorite_areas_count").asInt());
                        break;
                    case "gps_data.json":
                        data.gpsData = objectMapper.readTree(content);
                        log.info("Extracted GPS data: {} points", data.gpsData.size());
                        break;
                    case "timeline_config.json":
                        data.timelineConfig = objectMapper.readValue(content, TimelineConfig.class);
                        log.info("Extracted timeline configuration");
                        break;
                    case "favorite_locations.json":
                        data.favoriteLocations = objectMapper.readTree(content);
                        log.info("Extracted favorite locations: {}", data.favoriteLocations.size());
                        break;
                    case "favorite_areas.json":
                        data.favoriteAreas = objectMapper.readTree(content);
                        log.info("Extracted favorite areas: {}", data.favoriteAreas.size());
                        break;
                    default:
                        log.warn("Unknown file in ZIP: {}", fileName);
                }

                zis.closeEntry();
            }
        }

        // Validate required files are present
        if (data.gpsData == null) {
            throw new IllegalArgumentException("ZIP file missing required gps_data.json");
        }

        return data;
    }

    private void clearUserData(UUID userId) {
        log.info("Clearing existing data for user {}", userId);

        // Delete in correct order to respect foreign key constraints
        timelineTripRepository.deleteByUserId(userId);
        timelineStayRepository.deleteByUserId(userId);
        gpsPointRepository.deleteByUserId(userId);

        log.info("Existing data cleared for user {}", userId);
    }

    private void importGpsPoints(UUID userId, JsonNode gpsData) {
        log.info("Importing {} GPS points", gpsData.size());

        int imported = 0;
        UserEntity user = entityManager.getReference(UserEntity.class, userId);
        for (JsonNode point : gpsData) {
            try {
                // Parse OwnTracks message
                OwnTracksLocationMessage message = objectMapper.treeToValue(point, OwnTracksLocationMessage.class);
                GpsPointEntity entity = gpsPointMapper.toEntity(message, user, message.getTid(), GpsSourceType.OWNTRACKS);
                gpsPointRepository.persist(entity);

                imported++;

                if (imported % 1000 == 0) {
                    log.info("Imported {} / {} GPS points", imported, gpsData.size());
                }
            } catch (Exception e) {
                log.error("Failed to import GPS point: {}", point, e);
                throw new RuntimeException("Failed to import GPS point", e);
            }
        }

        log.info("Successfully imported {} GPS points", imported);
    }

    private void importFavoriteLocations(UUID userId, JsonNode locations) {
        if (locations == null || locations.isEmpty()) {
            log.info("No favorite locations to import");
            return;
        }

        log.info("Importing {} favorite locations", locations.size());

        for (JsonNode location : locations) {
            try {
                String name = location.get("name").asText();
                double latitude = location.get("latitude").asDouble();
                double longitude = location.get("longitude").asDouble();

                AddPointToFavoritesDto addPointToFavoritesDto = new AddPointToFavoritesDto(name, latitude, longitude);

                favoriteLocationService.addFavorite(userId, addPointToFavoritesDto);

            } catch (Exception e) {
                log.error("Failed to import favorite location: {}", location, e);
                throw new RuntimeException("Failed to import favorite location", e);
            }
        }

        log.info("Successfully imported {} favorite locations", locations.size());
    }

    private void importFavoriteAreas(UUID userId, JsonNode areas) {
        if (areas == null || areas.isEmpty()) {
            log.info("No favorite areas to import");
            return;
        }

        log.info("Importing {} favorite areas", areas.size());

        for (JsonNode area : areas) {
            try {
                String name = area.get("name").asText();
                double northEastLat = area.get("northEastLat").asDouble();
                double northEastLon = area.get("northEastLon").asDouble();
                double southWestLat = area.get("southWestLat").asDouble();
                double southWestLon = area.get("southWestLon").asDouble();

                AddAreaToFavoritesDto areaToFavoritesDto = new AddAreaToFavoritesDto(
                        name, northEastLat, northEastLon, southWestLat, southWestLon
                );

                favoriteLocationService.addFavorite(userId, areaToFavoritesDto);

            } catch (Exception e) {
                log.error("Failed to import favorite area: {}", area, e);
                throw new RuntimeException("Failed to import favorite area", e);
            }
        }

        log.info("Successfully imported {} favorite areas", areas.size());
    }

    private void updateTimelineConfig(UUID userId, TimelineConfig config) {
        log.info("Updating timeline configuration for user {}", userId);

        // Use MapStruct to convert TimelineConfig to UpdateTimelinePreferencesRequest
        var request = timelinePreferencesMapper.configToRequest(config);

        userService.updateTimelinePreferences(userId, request);
        log.info("Timeline configuration updated");
    }

    /**
     * Helper class to hold extracted ZIP contents
     */
    private static class DebugImportData {
        JsonNode metadata;
        JsonNode gpsData;
        TimelineConfig timelineConfig;
        JsonNode favoriteLocations;
        JsonNode favoriteAreas;
    }
}

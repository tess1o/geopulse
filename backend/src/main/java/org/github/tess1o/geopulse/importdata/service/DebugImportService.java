package org.github.tess1o.geopulse.importdata.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.export.dto.TimelineDataDto;
import org.github.tess1o.geopulse.favorites.mapper.FavoriteLocationMapper;
import org.github.tess1o.geopulse.favorites.model.AddAreaToFavoritesDto;
import org.github.tess1o.geopulse.favorites.model.AddPointToFavoritesDto;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.importdata.model.DebugImportReport;
import org.github.tess1o.geopulse.importdata.model.DebugImportReport.Warning;
import org.github.tess1o.geopulse.importdata.model.DebugImportRequest;
import org.github.tess1o.geopulse.importdata.model.DebugTimelineComparison;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.trips.GpsPointEnvironmentService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.user.service.UserService;

import java.io.IOException;
import java.io.InputStream;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@ApplicationScoped
@Slf4j
public class DebugImportService {

    private static final String LEGACY_VELOCITY_MS_EXPORT_VERSION = "1.0";
    private static final String GPS_DATA_FILE = "gps_data.json";
    /** Points persisted between flushes; keeps the persistence context small for large exports. */
    private static final int GPS_BATCH_SIZE = 500;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    FavoriteLocationMapper favoriteLocationMapper;

    @Inject
    UserService userService;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointMapper gpsPointMapper;

    @Inject
    StreamingTimelineGenerationService timelineGenerationService;

    @Inject
    GeoPulseTimelineSnapshotImporter timelineSnapshotImporter;

    @Inject
    GeoPulsePlacesImporter placesImporter;

    @Inject
    GeoPulseTimelineDetailsImporter timelineDetailsImporter;

    @Inject
    GpsPointEnvironmentService gpsPointEnvironmentService;

    @Inject
    DebugTimelineComparisonService comparisonService;

    @Inject
    EntityManager entityManager;

    @Inject
    org.github.tess1o.geopulse.user.mapper.TimelinePreferencesMapper timelinePreferencesMapper;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "unknown")
    String applicationVersion;

    /**
     * Import debug data from an in-memory ZIP file.
     * This is an all-or-nothing operation - uses transaction to rollback on any failure.
     */
    @Transactional
    public DebugImportReport importDebugData(UUID userId, byte[] zipData, DebugImportRequest request) throws IOException {
        return importDebugData(new ImportJob(userId, new ImportOptions(), "debug-import.zip", zipData), request);
    }

    /**
     * Checks that the job's file is a debug export before it is queued for import.
     */
    public void validate(ImportJob job) throws IOException {
        boolean hasGpsData = false;
        try (InputStream input = job.getDataStream(); ZipInputStream zis = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (GPS_DATA_FILE.equals(entry.getName())) {
                    hasGpsData = true;
                }
            }
        } catch (java.util.zip.ZipException e) {
            throw new IllegalArgumentException("File is not a valid ZIP archive", e);
        }
        if (!hasGpsData) {
            throw new IllegalArgumentException("ZIP file missing required gps_data.json");
        }
    }

    /**
     * Import a debug export, regenerate the timeline from it and compare the result with the timeline the user saw.
     * This is an all-or-nothing operation - uses transaction to rollback on any failure.
     */
    @Transactional
    public DebugImportReport importDebugData(ImportJob job, DebugImportRequest request) throws IOException {
        UUID userId = job.getUserId();
        log.info("Starting debug import for user {}", userId);

        // 1. Read every file except the GPS points, which are streamed later
        progress(job, 30, "Reading debug export...");
        DebugImportData data = readSmallEntries(job);

        // 2. Optionally clear existing data
        if (request.isClearExistingData()) {
            clearUserData(userId);
        }

        // Exported ids of favorites and geocoding rows are mapped to the new rows, so overrides can point at them
        ImportReferenceMaps referenceMaps = new ImportReferenceMaps();

        // 3. Import favorite locations and areas
        int favoriteLocations = importFavoriteLocations(userId, data.favoriteLocations, referenceMaps);
        int favoriteAreas = importFavoriteAreas(userId, data.favoriteAreas, referenceMaps);

        // 4. Import the geocoding rows stays were resolved to, as rows of this user: they win over the shared cache,
        // so regenerated stays get the same place identity, and therefore merge, as on the exporting server
        if (data.reverseGeocoding != null) {
            placesImporter.importReverseGeocodingDataInTransaction(data.reverseGeocoding, job, referenceMaps);
        }

        // 5. Import GPS points, with the water flags the exporting server classified them with
        progress(job, 40, "Importing GPS points...");
        String waterDatasetVersion = gpsPointEnvironmentService.getCurrentEnvironmentDatasetVersion();
        GpsImportResult gps = importGpsPoints(job, data, !request.isClearExistingData(), waterDatasetVersion);

        // 6. Import manual overrides; regeneration re-applies them
        if (data.timelineOverrides != null) {
            timelineDetailsImporter.importTimelineOverridesData(data.timelineOverrides, job, referenceMaps);
        }

        // 7. Optionally update timeline configuration and timezone
        boolean configApplied = request.isUpdateTimelineConfig() && data.timelineConfig != null;
        if (configApplied) {
            updateTimelineConfig(userId, data.timelineConfig, data.metadata);
        }
        String timezone = text(data.metadata, "timezone");
        boolean timezoneApplied = request.isUpdateTimezone() && updateTimezone(userId, timezone);

        // 8. Regenerate timeline
        progress(job, 80, "Regenerating timeline...");
        entityManager.flush();
        timelineGenerationService.regenerateFullTimeline(userId);

        // 9. Compare it with the timeline the user saw
        progress(job, 95, "Comparing timelines...");
        DebugImportReport report = buildReport(userId, request, data, gps, waterDatasetVersion,
                favoriteLocations, favoriteAreas, referenceMaps.geocodingIds.size(), timezone, timezoneApplied,
                configApplied);
        job.setGpsDataImported(gps.imported() > 0);

        log.info("Debug import completed successfully for user {}: timeline {}", userId,
                report.comparison().available() ? (report.comparison().matches() ? "matches" : "differs") : "not compared");
        return report;
    }

    private DebugImportData readSmallEntries(ImportJob job) throws IOException {
        DebugImportData data = new DebugImportData();

        try (InputStream input = job.getDataStream(); ZipInputStream zis = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String fileName = entry.getName();
                if (GPS_DATA_FILE.equals(fileName)) {
                    data.hasGpsData = true;
                    zis.closeEntry();
                    continue;
                }
                byte[] content = zis.readAllBytes();

                switch (fileName) {
                    case "metadata.json":
                        data.metadata = objectMapper.readTree(content);
                        log.info("Extracted metadata: export version {}, {} GPS points",
                                data.metadata.path("export_version").asText("1.0"),
                                data.metadata.path("gps_point_count").asLong());
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
                    case "reverse_geocoding.json":
                        data.reverseGeocoding = content;
                        log.info("Extracted reverse geocoding locations");
                        break;
                    case "timeline_overrides.json":
                        data.timelineOverrides = content;
                        log.info("Extracted timeline overrides");
                        break;
                    case "timeline.json":
                        // The exporting server's timeline: the expected result a regeneration is compared with, not
                        // imported as this user's timeline
                        data.exportedTimeline = objectMapper.readValue(content, TimelineDataDto.class);
                        log.info("Extracted exported timeline");
                        break;
                    default:
                        log.warn("Unknown file in ZIP: {}", fileName);
                }

                zis.closeEntry();
            }
        }

        // Validate required files are present
        if (!data.hasGpsData) {
            throw new IllegalArgumentException("ZIP file missing required gps_data.json");
        }

        return data;
    }

    private void clearUserData(UUID userId) {
        log.info("Clearing existing data for user {}", userId);

        // Timeline and everything attached to it first (overrides, notes and path matches reference timeline rows),
        // then the rows the timeline references
        timelineSnapshotImporter.clearTimelineSnapshotData(userId);
        gpsPointRepository.deleteByUserId(userId);
        entityManager.createNativeQuery("DELETE FROM favorite_locations WHERE user_id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM reverse_geocoding_location WHERE user_id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();

        log.info("Existing data cleared for user {}", userId);
    }

    /**
     * Streams the GPS points from the archive and stores them in batches.
     *
     * @param checkDuplicates false when the user's points were just cleared, so none can already exist
     */
    private GpsImportResult importGpsPoints(ImportJob job, DebugImportData data, boolean checkDuplicates,
                                            String waterDatasetVersion) throws IOException {
        UUID userId = job.getUserId();
        long expected = data.metadata != null ? data.metadata.path("gps_point_count").asLong(0) : 0;
        GpsImportResult result = new GpsImportResult();
        List<GpsPointEntity> batch = new ArrayList<>();
        List<Boolean> batchOnWater = new ArrayList<>();

        try (InputStream input = job.getDataStream(); ZipInputStream zis = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!GPS_DATA_FILE.equals(entry.getName())) {
                    continue;
                }
                JsonParser parser = objectMapper.getFactory().createParser(zis);
                parser.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
                if (parser.nextToken() != JsonToken.START_ARRAY) {
                    throw new IllegalArgumentException("gps_data.json must contain a JSON array");
                }
                UserEntity user = entityManager.getReference(UserEntity.class, userId);
                while (parser.nextToken() == JsonToken.START_OBJECT) {
                    JsonNode point = objectMapper.readTree(parser);
                    GpsPointEntity entity = toEntity(point, user);
                    result.read++;
                    result.latestPoint = latest(result.latestPoint, entity.getTimestamp());
                    if (checkDuplicates && gpsPointRepository.findByUniqueKey(userId, entity.getTimestamp(),
                            entity.getCoordinates()).isPresent()) {
                        log.debug("Skipping duplicate GPS point: {}", point);
                        continue;
                    }
                    gpsPointRepository.persist(entity);
                    batch.add(entity);
                    batchOnWater.add(point.hasNonNull("on_water") ? point.get("on_water").asBoolean() : null);
                    if (batch.size() >= GPS_BATCH_SIZE) {
                        result.waterFlags += flushBatch(batch, batchOnWater, waterDatasetVersion);
                        result.imported += batch.size();
                        batch.clear();
                        batchOnWater.clear();
                        user = entityManager.getReference(UserEntity.class, userId);
                        if (expected > 0) {
                            progress(job, 40 + (int) Math.min(35, result.read * 35 / expected),
                                    "Importing GPS points " + result.read + " / " + expected + "...");
                        }
                    }
                }
                break;
            }
        }
        result.waterFlags += flushBatch(batch, batchOnWater, waterDatasetVersion);
        result.imported += batch.size();

        log.info("Successfully imported {} of {} GPS points ({} water flags)", result.imported, result.read,
                result.waterFlags);
        return result;
    }

    private GpsPointEntity toEntity(JsonNode point, UserEntity user) {
        try {
            // Parse OwnTracks message. Velocity is exported as stored (km/h), so it is read without the
            // per-source unit conversion and the source type is applied afterwards.
            OwnTracksLocationMessage message = objectMapper.treeToValue(point, OwnTracksLocationMessage.class);
            GpsPointEntity entity = gpsPointMapper.toEntity(message, user, message.getTid(), GpsSourceType.OWNTRACKS);
            applyDebugFields(entity, point);
            return entity;
        } catch (Exception e) {
            log.error("Failed to import GPS point: {}", point, e);
            throw new IllegalArgumentException("Failed to import GPS point " + point, e);
        }
    }

    /**
     * Writes the batch and its water flags, then clears the persistence context.
     *
     * @return water flags stored
     */
    private int flushBatch(List<GpsPointEntity> batch, List<Boolean> onWater, String waterDatasetVersion) {
        if (batch.isEmpty()) {
            return 0;
        }
        entityManager.flush();
        int flags = storeWaterFlags(batch, onWater, waterDatasetVersion);
        entityManager.clear();
        return flags;
    }

    /**
     * Stores the exported water flags as this server's classification of the imported points. The coordinates are
     * shifted, so classifying them here would describe a different place. The flags are stamped with this server's
     * water dataset version, so the timeline uses them and the background classification leaves them alone.
     */
    private int storeWaterFlags(List<GpsPointEntity> batch, List<Boolean> onWater, String waterDatasetVersion) {
        if (waterDatasetVersion == null) {
            return 0;
        }
        StringBuilder values = new StringBuilder();
        int count = 0;
        for (int i = 0; i < batch.size(); i++) {
            Boolean flag = onWater.get(i);
            if (flag == null) {
                continue;
            }
            // Ids and flags are numbers and booleans, so they can be written into the statement directly
            values.append(count == 0 ? "" : ",").append('(').append(batch.get(i).getId().longValue()).append(", ")
                    .append(":version, ").append(flag.booleanValue()).append(", 'debug-import', NOW())");
            count++;
        }
        if (count == 0) {
            return 0;
        }
        entityManager.createNativeQuery("""
                        INSERT INTO gps_point_environment (
                            gps_point_id, environment_dataset_version, on_water, water_source, classified_at
                        ) VALUES\s""" + values + """
                        ON CONFLICT (gps_point_id) DO UPDATE SET
                            environment_dataset_version = EXCLUDED.environment_dataset_version,
                            on_water = EXCLUDED.on_water,
                            water_source = EXCLUDED.water_source,
                            classified_at = EXCLUDED.classified_at
                        """)
                .setParameter("version", waterDatasetVersion)
                .executeUpdate();
        return count;
    }

    /**
     * Restores what OwnTracks fields cannot carry: the millisecond timestamp, when the exporting server received the
     * point (exports before 1.2 only have it in seconds), and the point's source.
     */
    private static void applyDebugFields(GpsPointEntity entity, JsonNode point) {
        if (point.hasNonNull("tst_ms")) {
            entity.setTimestamp(Instant.ofEpochMilli(point.get("tst_ms").asLong()));
        }
        if (point.hasNonNull("created_at_ms")) {
            entity.setCreatedAt(Instant.ofEpochMilli(point.get("created_at_ms").asLong()));
        } else if (point.hasNonNull("created_at")) {
            entity.setCreatedAt(Instant.ofEpochSecond(point.get("created_at").asLong()));
        }
        if (point.hasNonNull("source_type")) {
            try {
                entity.setSourceType(GpsSourceType.valueOf(point.get("source_type").asText()));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown GPS source type {}, keeping {}", point.get("source_type").asText(),
                        entity.getSourceType());
            }
        }
    }

    private int importFavoriteLocations(UUID userId, JsonNode locations, ImportReferenceMaps referenceMaps) {
        if (locations == null || locations.isEmpty()) {
            log.info("No favorite locations to import");
            return 0;
        }

        log.info("Importing {} favorite locations", locations.size());

        for (JsonNode location : locations) {
            try {
                String name = location.get("name").asText();
                double latitude = location.get("latitude").asDouble();
                double longitude = location.get("longitude").asDouble();

                FavoritesEntity entity = favoriteLocationMapper.toEntity(
                        new AddPointToFavoritesDto(name, latitude, longitude), userId);
                persistFavorite(entity, location, referenceMaps);

            } catch (Exception e) {
                log.error("Failed to import favorite location: {}", location, e);
                throw new RuntimeException("Failed to import favorite location", e);
            }
        }

        log.info("Successfully imported {} favorite locations", locations.size());
        return locations.size();
    }

    private int importFavoriteAreas(UUID userId, JsonNode areas, ImportReferenceMaps referenceMaps) {
        if (areas == null || areas.isEmpty()) {
            log.info("No favorite areas to import");
            return 0;
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

                persistFavorite(favoriteLocationMapper.toEntity(areaToFavoritesDto, userId), area, referenceMaps);

            } catch (Exception e) {
                log.error("Failed to import favorite area: {}", area, e);
                throw new RuntimeException("Failed to import favorite area", e);
            }
        }

        log.info("Successfully imported {} favorite areas", areas.size());
        return areas.size();
    }

    /**
     * Stores the favorite as exported. It is not reverse geocoded: the coordinates are shifted, so a provider would
     * only return an unrelated place.
     */
    private void persistFavorite(FavoritesEntity entity, JsonNode exported, ImportReferenceMaps referenceMaps) {
        favoritesRepository.persist(entity);
        if (exported.hasNonNull("id")) {
            favoritesRepository.flush();
            referenceMaps.favoriteIds.put(exported.get("id").asLong(), entity.getId());
        }
    }

    private void updateTimelineConfig(UUID userId, TimelineConfig config, JsonNode metadata) {
        log.info("Updating timeline configuration for user {}", userId);

        // Exports before version 1.1 hold the stay velocity threshold in m/s; it is in km/h now
        String exportVersion = metadata != null && metadata.hasNonNull("export_version")
                ? metadata.get("export_version").asText()
                : LEGACY_VELOCITY_MS_EXPORT_VERSION;
        if (LEGACY_VELOCITY_MS_EXPORT_VERSION.equals(exportVersion) && config.getStaypointVelocityThreshold() != null) {
            config.setStaypointVelocityThreshold(config.getStaypointVelocityThreshold() * 3.6);
        }

        // Use MapStruct to convert TimelineConfig to UpdateTimelinePreferencesRequest
        var request = timelinePreferencesMapper.configToRequest(config);

        userService.updateTimelinePreferences(userId, request);
        log.info("Timeline configuration updated");
    }

    /**
     * Uses the exporting user's timezone, so days start and end where theirs do.
     *
     * @return true when it was applied
     */
    private boolean updateTimezone(UUID userId, String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return false;
        }
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException e) {
            log.warn("Ignoring invalid exported timezone {}", timezone);
            return false;
        }
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            return false;
        }
        user.setTimezone(timezone);
        log.info("Timezone of user {} set to {}", userId, timezone);
        return true;
    }

    private DebugImportReport buildReport(UUID userId, DebugImportRequest request, DebugImportData data,
                                          GpsImportResult gps, String waterDatasetVersion,
                                          int favoriteLocations, int favoriteAreas, int geocodingLocations,
                                          String timezone, boolean timezoneApplied, boolean configApplied) {
        JsonNode metadata = data.metadata != null ? data.metadata : objectMapper.createObjectNode();
        String exportVersion = metadata.path("export_version").asText(LEGACY_VELOCITY_MS_EXPORT_VERSION);
        String exporterVersion = text(metadata, "app_version");

        DebugImportReport.Range exportedRange = range(metadata.has("exported_range")
                ? metadata.get("exported_range") : metadata.get("original_date_range"));
        DebugImportReport.Range requestedRange = metadata.has("requested_range")
                ? range(metadata.get("requested_range")) : exportedRange;

        DebugTimelineComparison comparison;
        Instant from = requestedRange != null ? requestedRange.start() : null;
        Instant to = requestedRange != null ? requestedRange.end() : null;
        if (data.exportedTimeline != null && from != null && to != null) {
            comparison = comparisonService.compare(
                    comparisonService.exportedEvents(data.exportedTimeline, from, to, gps.latestPoint),
                    comparisonService.currentEvents(userId, from, to, gps.latestPoint),
                    from, to);
        } else {
            comparison = DebugTimelineComparison.unavailable(from, to);
        }

        JsonNode waterNode = metadata.path("water");
        TimelineConfig config = data.timelineConfig;
        boolean boatEnabled = waterNode.has("boat_enabled")
                ? waterNode.get("boat_enabled").asBoolean()
                : config != null && Boolean.TRUE.equals(config.getBoatEnabled());
        DebugImportReport.Water water = new DebugImportReport.Water(boatEnabled,
                waterNode.path("dataset_available").asBoolean(false), waterDatasetVersion != null, gps.waterFlags);

        List<Map<String, Object>> gpsSources = metadata.has("gps_sources")
                ? objectMapper.convertValue(metadata.get("gps_sources"), new TypeReference<>() {})
                : List.of();
        Map<String, Object> timelineStatus = metadata.has("timeline_status")
                ? objectMapper.convertValue(metadata.get("timeline_status"), new TypeReference<>() {})
                : null;

        JsonNode issueNode = metadata.path("issue");
        DebugImportReport.Issue issue = new DebugImportReport.Issue(text(issueNode, "description"),
                instant(text(issueNode, "time")));

        List<Warning> warnings = warnings(request, exportVersion, exporterVersion, comparison, gpsSources,
                timelineStatus, water, configApplied, data);

        return new DebugImportReport(exportVersion, exporterVersion, applicationVersion,
                instant(text(metadata, "export_date")), timezone, timezoneApplied, configApplied, issue,
                requestedRange, exportedRange,
                new DebugImportReport.Imported(gps.imported(), favoriteLocations, favoriteAreas, geocodingLocations,
                        data.timelineOverrides != null),
                gpsSources, timelineStatus, water, comparison, warnings);
    }

    /**
     * Reasons the regenerated timeline may differ from the user's, other than a bug in timeline generation.
     */
    private List<Warning> warnings(DebugImportRequest request, String exportVersion, String exporterVersion,
                                   DebugTimelineComparison comparison, List<Map<String, Object>> gpsSources,
                                   Map<String, Object> timelineStatus, DebugImportReport.Water water,
                                   boolean configApplied, DebugImportData data) {
        List<Warning> warnings = new ArrayList<>();
        if (!comparison.available()) {
            warnings.add(Warning.of("TIMELINE_NOT_IN_EXPORT"));
        } else if (!comparison.matches()) {
            warnings.add(new Warning("TIMELINE_DIFFERS", Map.of(
                    "different", comparison.different(),
                    "missing", comparison.missing(),
                    "extra", comparison.extra())));
        }
        if (compareVersions(exportVersion, "1.2") < 0) {
            warnings.add(new Warning("OLD_EXPORT_FORMAT", Map.of("version", exportVersion)));
        }
        if (exporterVersion != null && !exporterVersion.equals(applicationVersion)) {
            warnings.add(new Warning("APP_VERSION_DIFFERS", Map.of(
                    "exporterVersion", exporterVersion, "importerVersion", applicationVersion)));
        }
        if (timelineStatus != null) {
            Object pointsAfterBuild = timelineStatus.get("points_received_after_last_build");
            if (pointsAfterBuild instanceof Number number && number.longValue() > 0) {
                warnings.add(new Warning("TIMELINE_STALE", Map.of("points", number.longValue())));
            }
            if (timelineStatus.get("pending_rebuild") instanceof Map<?, ?> rebuild) {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("from", String.valueOf(rebuild.get("rebuild_from")));
                params.put("error", rebuild.get("last_error") == null ? "" : String.valueOf(rebuild.get("last_error")));
                warnings.add(new Warning(rebuild.get("last_error") != null ? "REBUILD_FAILING" : "REBUILD_PENDING",
                        params));
            }
        }
        boolean filtersActive = gpsSources.stream().anyMatch(source -> Boolean.TRUE.equals(source.get("active"))
                && (Boolean.TRUE.equals(source.get("filter_inaccurate_data"))
                || Boolean.TRUE.equals(source.get("duplicate_detection_enabled"))));
        if (filtersActive) {
            warnings.add(Warning.of("GPS_FILTERS_ACTIVE"));
        }
        if (water.boatEnabled() && !water.exporterDatasetAvailable()) {
            warnings.add(Warning.of("WATER_FLAGS_NOT_EXPORTED"));
        } else if (water.boatEnabled() && !water.importerDatasetAvailable()) {
            warnings.add(Warning.of("WATER_DATASET_MISSING"));
        }
        if (!configApplied) {
            warnings.add(Warning.of(data.timelineConfig == null ? "TIMELINE_CONFIG_NOT_IN_EXPORT"
                    : "TIMELINE_CONFIG_NOT_APPLIED"));
        }
        if (!request.isClearExistingData()) {
            warnings.add(Warning.of("EXISTING_DATA_KEPT"));
        }
        return warnings;
    }

    private static int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? parseIntOrZero(a[i]) : 0;
            int y = i < b.length ? parseIntOrZero(b[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static DebugImportReport.Range range(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        Instant start = instant(text(node, "start"));
        Instant end = instant(text(node, "end"));
        return start != null && end != null ? new DebugImportReport.Range(start, end) : null;
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static Instant instant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static Instant latest(Instant current, Instant candidate) {
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    private static void progress(ImportJob job, int progress, String message) {
        job.updateProgress(progress, message);
    }

    /**
     * Helper class to hold extracted ZIP contents
     */
    private static class DebugImportData {
        JsonNode metadata;
        boolean hasGpsData;
        TimelineConfig timelineConfig;
        JsonNode favoriteLocations;
        JsonNode favoriteAreas;
        byte[] reverseGeocoding;
        byte[] timelineOverrides;
        TimelineDataDto exportedTimeline;
    }

    private static class GpsImportResult {
        long read;
        long imported;
        long waterFlags;
        Instant latestPoint;

        long imported() {
            return imported;
        }
    }
}

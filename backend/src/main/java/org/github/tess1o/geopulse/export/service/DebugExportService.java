package org.github.tess1o.geopulse.export.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.export.dto.ReverseGeocodingDataDto;
import org.github.tess1o.geopulse.export.dto.TimelineDataDto;
import org.github.tess1o.geopulse.export.dto.TimelineOverridesDataDto;
import org.github.tess1o.geopulse.export.mapper.ExportDataMapper;
import org.github.tess1o.geopulse.export.model.DebugExportRequest;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationsDto;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapStayOverrideEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripMovementOverrideEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripStaySplitOverrideEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.trips.GpsPointEnvironmentService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service for generating debug exports with privacy-preserving coordinate shifts.
 * <p>
 * A debug export must let the timeline be regenerated exactly as the user's server built it, so besides the GPS
 * points it carries every other input the timeline engine reads: the timeline settings, favorites, the reverse
 * geocoding rows stays were resolved to (stays merge by geocoding identity) and the user's manual overrides (they are
 * re-applied after every regeneration). The user's current timeline is included as the expected result.
 * All coordinates are shifted by the same offset and all names are replaced by stable placeholders.
 */
@ApplicationScoped
@Slf4j
public class DebugExportService {

    /**
     * 1.1: staypointVelocityThreshold in timeline_config.json is in km/h (it was m/s in 1.0).
     * 1.2: millisecond timestamps, arrival time, source type and water flag on GPS points; favorites carry their id;
     * adds timeline.json, reverse_geocoding.json and timeline_overrides.json; metadata adds the problem report, GPS
     * source filters and the state of the timeline; the range is widened to whole timeline events.
     */
    public static final String EXPORT_VERSION = "1.2";

    public static final String METADATA_FILE = "metadata.json";
    public static final String GPS_DATA_FILE = "gps_data.json";
    public static final String TIMELINE_CONFIG_FILE = "timeline_config.json";
    public static final String FAVORITE_LOCATIONS_FILE = "favorite_locations.json";
    public static final String FAVORITE_AREAS_FILE = "favorite_areas.json";
    public static final String TIMELINE_FILE = "timeline.json";
    public static final String REVERSE_GEOCODING_FILE = "reverse_geocoding.json";
    public static final String TIMELINE_OVERRIDES_FILE = "timeline_overrides.json";

    /**
     * Points exported before the first and after the last timeline event touching the requested range, so the events
     * at the edges are regenerated from the same lead-in and lead-out points the user's server saw.
     */
    static final Duration EDGE_CONTEXT = Duration.ofHours(1);

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    TimelineConfigurationProvider timelineConfigurationProvider;

    @Inject
    CoordinateShiftService coordinateShiftService;

    @Inject
    StreamingExportService streamingExportService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    FavoriteLocationService favoriteLocationService;

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    TimelineTripRepository timelineTripRepository;

    @Inject
    TimelineDataGapRepository timelineDataGapRepository;

    @Inject
    ExportDataCollectorService dataCollectorService;

    @Inject
    ExportDataMapper exportDataMapper;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointEnvironmentService gpsPointEnvironmentService;

    @Inject
    ExportTempFileService tempFileService;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "unknown")
    String applicationVersion;

    @ConfigProperty(name = "geocoding.cache.spatial-tolerance-meters", defaultValue = "25")
    double geocodingSpatialToleranceMeters;

    @ConfigProperty(name = "geocoding.cache.max-bbox-area-km2", defaultValue = "5000")
    double geocodingMaxBboxAreaKm2;

    @ConfigProperty(name = "geopulse.favorites.max-distance-from-area", defaultValue = "15")
    int favoritesMaxDistanceFromArea;

    /**
     * Runs a debug export job: writes the archive to a temp file and records it on the job for download.
     */
    public void generateDebugExport(ExportJob job) throws IOException {
        job.updateProgress(5, "initializingExport", "Initializing export...");
        Path tempFile = tempFileService.createTempFile(job.getJobId(), ".zip");
        try (OutputStream output = Files.newOutputStream(tempFile)) {
            writeDebugExport(job.getUserId(), job.getDebugRequest(), output, job);
        }
        job.setTempFilePath(tempFile.toString());
        job.setFileExtension(".zip");
        job.setContentType("application/zip");
        job.setFileSizeBytes(Files.size(tempFile));
        job.updateProgress(95, "exportCompleted", "Export completed");
    }

    /**
     * Generates a debug export in memory. Meant for small ranges, such as in tests.
     */
    public byte[] generateDebugExport(UUID userId, DebugExportRequest request) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeDebugExport(userId, request, output, null);
        return output.toByteArray();
    }

    /**
     * Writes a debug export with shifted coordinates and anonymized names. The ZIP contains:
     * - metadata.json: Export metadata, the user's problem report, GPS source filters and the state of the timeline
     * - gps_data.json: OwnTracks format with shifted coordinates
     * - timeline_config.json: User's timeline configuration
     * - favorite_locations.json: Anonymized favorite locations with shifted coordinates
     * - favorite_areas.json: Anonymized favorite areas with shifted boundaries
     * - reverse_geocoding.json: Anonymized geocoding rows the exported stays were resolved to
     * - timeline_overrides.json: The user's manual timeline overrides in the exported range
     * - timeline.json: The user's current timeline in the exported range (if selected)
     *
     * @param progressJob job to report progress on, or null
     */
    void writeDebugExport(UUID userId, DebugExportRequest request, OutputStream output, ExportJob progressJob)
            throws IOException {
        log.info("Starting debug export for user {} from {} to {}",
                userId, request.getStartTime(), request.getEndTime());

        ExportRange range = resolveExportRange(userId, request);
        log.info("Debug export range widened to whole timeline events: {} to {}", range.start(), range.end());

        // Validate shift won't push coordinates too far out of bounds
        validateCoordinateShift(userId, request, range);

        Shift shift = new Shift(request.getLatitudeShift(), request.getLongitudeShift());
        Anonymizer anonymizer = new Anonymizer();
        progress(progressJob, 10, "collectingTimeline", "Collecting timeline data...");

        // Everything except GPS points is mapped to DTOs before the points are streamed: streaming clears the
        // persistence context to stay memory-bounded.
        FavoriteLocationsDto favoriteLocations = favoriteLocationService.getFavorites(userId);
        byte[] locationsData = generateShiftedFavoriteLocationsData(favoriteLocations, shift, anonymizer);
        byte[] areasData = generateShiftedFavoriteAreasData(favoriteLocations, shift, anonymizer);

        List<TimelineStayEntity> stays = timelineStayRepository.findByUserIdAndTimeRangeWithExpansion(
                userId, range.start(), range.end());
        List<TimelineTripEntity> trips = timelineTripRepository.findByUserIdAndTimeRangeWithExpansion(
                userId, range.start(), range.end());
        List<TimelineDataGapEntity> dataGaps = timelineDataGapRepository.findByUserIdAndTimeRangeWithExpansion(
                userId, range.start(), range.end());
        TimelineOverridesDataDto overrides = collectOverrides(userId, range);

        Set<Long> geocodingIds = new LinkedHashSet<>();
        stays.stream()
                .filter(stay -> stay.getGeocodingLocation() != null)
                .forEach(stay -> geocodingIds.add(stay.getGeocodingLocation().getId()));
        overrides.getDataGapStayOverrides().stream()
                .filter(override -> override.getSelectedGeocodingId() != null)
                .forEach(override -> geocodingIds.add(override.getSelectedGeocodingId()));
        overrides.getTripStaySplitOverrides().stream()
                .filter(override -> override.getGeocodingId() != null)
                .forEach(override -> geocodingIds.add(override.getGeocodingId()));
        ReverseGeocodingDataDto geocoding = exportDataMapper.toReverseGeocodingDataDto(
                dataCollectorService.collectReverseGeocodingLocations(geocodingIds));
        shiftAndAnonymize(geocoding, shift, anonymizer);
        shiftAndAnonymize(overrides, shift, anonymizer);

        TimelineDataDto timeline = null;
        if (request.isIncludeTimeline()) {
            timeline = TimelineDataDto.builder()
                    .dataType(ExportImportConstants.DataTypes.TIMELINE)
                    .exportDate(Instant.now())
                    .startDate(range.start())
                    .endDate(range.end())
                    .stays(stays.stream().map(exportDataMapper::toStayDto).toList())
                    .trips(trips.stream().map(exportDataMapper::toTripDto).toList())
                    .dataGaps(dataGaps.stream().map(exportDataMapper::toDataGapDto).toList())
                    .build();
            shiftAndAnonymize(timeline, shift, anonymizer);
        }

        UserEntity user = userRepository.findById(userId);
        String timezone = user != null ? user.getTimezone() : null;
        TimelineConfig config = timelineConfigurationProvider.getConfigurationForUser(userId);
        String waterDatasetVersion = gpsPointEnvironmentService.getCurrentEnvironmentDatasetVersion();

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("favorite_locations_count", sizeOf(favoriteLocations != null ? favoriteLocations.getPoints() : null));
        counts.put("favorite_areas_count", sizeOf(favoriteLocations != null ? favoriteLocations.getAreas() : null));
        counts.put("reverse_geocoding_count", geocoding.getLocations().size());
        counts.put("trip_movement_override_count", overrides.getTripMovementOverrides().size());
        counts.put("data_gap_stay_override_count", overrides.getDataGapStayOverrides().size());
        counts.put("trip_stay_split_override_count", overrides.getTripStaySplitOverrides().size());
        if (timeline != null) {
            counts.put("timeline_stay_count", timeline.getStays().size());
            counts.put("timeline_trip_count", timeline.getTrips().size());
            counts.put("timeline_data_gap_count", timeline.getDataGaps().size());
        }

        Map<String, Object> metadata = generateMetadata(request, range, timezone, counts);
        metadata.put("gps_point_count", gpsPointRepository.countByUserIdAndTimePeriod(userId, range.start(), range.end()));
        metadata.put("gps_sources", gpsSourceSettings(userId));
        metadata.put("timeline_status", timelineStatus(userId, range, stays, trips));
        Map<String, Object> water = new LinkedHashMap<>();
        water.put("boat_enabled", Boolean.TRUE.equals(config.getBoatEnabled()));
        water.put("dataset_available", waterDatasetVersion != null);
        metadata.put("water", water);

        ZipOutputStream zipOut = new ZipOutputStream(output);
        addEntry(zipOut, METADATA_FILE, writeJson(metadata));
        if (request.isIncludeConfiguration()) {
            addEntry(zipOut, TIMELINE_CONFIG_FILE, writeJson(config));
        }
        addEntry(zipOut, FAVORITE_LOCATIONS_FILE, locationsData);
        addEntry(zipOut, FAVORITE_AREAS_FILE, areasData);
        addEntry(zipOut, REVERSE_GEOCODING_FILE, writeJson(geocoding));
        addEntry(zipOut, TIMELINE_OVERRIDES_FILE, writeJson(overrides));
        if (timeline != null) {
            addEntry(zipOut, TIMELINE_FILE, writeJson(timeline));
        }
        progress(progressJob, 20, "exportingGpsPoints", "Exporting GPS points...");
        long totalPoints = ((Number) metadata.get("gps_point_count")).longValue();
        zipOut.putNextEntry(new ZipEntry(GPS_DATA_FILE));
        writeShiftedGpsData(userId, range, shift, waterDatasetVersion, zipOut, progressJob, totalPoints);
        zipOut.closeEntry();
        zipOut.finish();
        zipOut.flush();

        log.info("Completed debug export for user {}", userId);
    }

    /**
     * Widens the requested range to the timeline events that touch it, plus {@link #EDGE_CONTEXT} on both sides. A stay
     * that began before the requested start would otherwise be regenerated with a shorter duration.
     */
    ExportRange resolveExportRange(UUID userId, DebugExportRequest request) {
        Instant start = request.getStartTime();
        Instant end = request.getEndTime();

        for (TimelineStayEntity stay : timelineStayRepository.findByUserIdAndTimeRangeWithExpansion(userId, start, end)) {
            start = min(start, stay.getTimestamp());
            end = max(end, stay.getTimestamp().plusSeconds(stay.getStayDuration()));
        }
        for (TimelineTripEntity trip : timelineTripRepository.findByUserIdAndTimeRangeWithExpansion(userId, start, end)) {
            start = min(start, trip.getTimestamp());
            end = max(end, trip.getTimestamp().plusSeconds(trip.getTripDuration()));
        }
        for (TimelineDataGapEntity gap : timelineDataGapRepository.findByUserIdAndTimeRangeWithExpansion(userId, start, end)) {
            start = min(start, gap.getStartTime());
            end = max(end, gap.getEndTime());
        }

        return new ExportRange(request.getStartTime(), request.getEndTime(),
                start.minus(EDGE_CONTEXT), end.plus(EDGE_CONTEXT));
    }

    private TimelineOverridesDataDto collectOverrides(UUID userId, ExportRange range) {
        List<TimelineTripMovementOverrideEntity> tripOverrides = dataCollectorService.collectTripMovementOverrides(userId)
                .stream()
                .filter(override -> range.overlaps(override.getSourceTripTimestamp(),
                        override.getSourceTripTimestamp().plusSeconds(override.getSourceTripDurationSeconds())))
                .toList();
        List<TimelineDataGapStayOverrideEntity> gapOverrides = dataCollectorService.collectDataGapStayOverrides(userId)
                .stream()
                .filter(override -> range.overlaps(override.getSourceGapStartTime(), override.getSourceGapEndTime()))
                .toList();
        List<TimelineTripStaySplitOverrideEntity> staySplitOverrides = dataCollectorService.collectTripStaySplitOverrides(userId)
                .stream()
                .filter(override -> range.overlaps(override.getSourceTripTimestamp(),
                        override.getSourceTripTimestamp().plusSeconds(override.getSourceTripDurationSeconds())))
                .toList();
        return exportDataMapper.toTimelineOverridesDataDto(tripOverrides, gapOverrides, staySplitOverrides);
    }

    /**
     * GPS points in OwnTracks format with shifted coordinates, written straight into the archive. Besides the
     * OwnTracks keys each point carries what OwnTracks cannot express: the timestamp in milliseconds ({@code tst} is
     * whole seconds), when the server received the point, its source, and whether it lies on water according to the
     * server's current water dataset. The coordinates are shifted, so the importing server cannot work the last one
     * out itself. Telemetry and the device id are left out: the timeline does not read them and they may hold
     * private values.
     */
    private void writeShiftedGpsData(UUID userId, ExportRange range, Shift shift, String waterDatasetVersion,
                                     OutputStream output, ExportJob progressJob, long totalPoints) throws IOException {
        JsonGenerator generator = objectMapper.getFactory().createGenerator(output);
        generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        generator.writeStartArray();
        long[] written = {0};
        try {
            gpsPointRepository.streamByUserAndDateRangeForExport(userId, range.start(), range.end(),
                    streamingExportService.getBatchSize(), batch -> {
                        Map<Long, Boolean> onWater = onWaterFlags(batch, waterDatasetVersion);
                        try {
                            for (GpsPointEntity point : batch) {
                                generator.writeObject(toShiftedDebugPoint(point, shift, onWater.get(point.getId())));
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        written[0] += batch.size();
                        if (totalPoints > 0) {
                            progress(progressJob, 20 + (int) Math.min(70, written[0] * 70 / totalPoints),
                                    "streamingRecordsProgress", "Exporting GPS points " + written[0] + " / " + totalPoints,
                                    Map.of("written", written[0], "total", totalPoints));
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause(); // NOPMD - deliberately rethrow the original checked cause
        }
        generator.writeEndArray();
        generator.flush();
    }

    /**
     * The "on water" flags of the batch's points that are classified against the current water dataset. Flags from an
     * older dataset are left out, as the timeline engine ignores them too.
     */
    private Map<Long, Boolean> onWaterFlags(List<GpsPointEntity> batch, String waterDatasetVersion) {
        if (waterDatasetVersion == null || batch.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = batch.stream().map(GpsPointEntity::getId).toList();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = gpsPointRepository.getEntityManager().createNativeQuery("""
                        SELECT gps_point_id, on_water FROM gps_point_environment
                        WHERE gps_point_id IN (:ids) AND environment_dataset_version = :version
                        """)
                .setParameter("ids", ids)
                .setParameter("version", waterDatasetVersion)
                .getResultList();
        Map<Long, Boolean> flags = new HashMap<>();
        for (Object[] row : rows) {
            flags.put(((Number) row[0]).longValue(), (Boolean) row[1]);
        }
        return flags;
    }

    private Map<String, Object> toShiftedDebugPoint(GpsPointEntity point, Shift shift, Boolean onWater) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("_type", "location");
        message.put("lat", shift.latitude(point.getLatitude()));
        message.put("lon", shift.longitude(point.getLongitude()));
        message.put("tst", point.getTimestamp().getEpochSecond());
        message.put("tst_ms", point.getTimestamp().toEpochMilli());
        message.put("acc", point.getAccuracy());
        message.put("vel", point.getVelocity());
        message.put("alt", point.getAltitude());
        message.put("batt", point.getBattery());
        if (point.getCreatedAt() != null) {
            message.put("created_at", point.getCreatedAt().getEpochSecond());
            message.put("created_at_ms", point.getCreatedAt().toEpochMilli());
        }
        message.put("source_type", point.getSourceType() != null ? point.getSourceType().name() : null);
        if (onWater != null) {
            message.put("on_water", onWater);
        }
        return message;
    }

    /**
     * Filter settings of the user's GPS sources. Points they rejected were never stored, so a missing stay can be
     * caused by them and no export could show those points. Credentials and device ids are left out.
     */
    private List<Map<String, Object>> gpsSourceSettings(UUID userId) {
        return dataCollectorService.collectLocationSources(userId).stream()
                .map(source -> {
                    Map<String, Object> settings = new LinkedHashMap<>();
                    settings.put("source_type", source.getSourceType() != null ? source.getSourceType().name() : null);
                    settings.put("active", source.isActive());
                    settings.put("connection_type", source.getConnectionType() != null ? source.getConnectionType().name() : null);
                    settings.put("filter_inaccurate_data", source.isFilterInaccurateData());
                    settings.put("max_allowed_accuracy_meters", source.getMaxAllowedAccuracy());
                    settings.put("max_allowed_speed_kmh", source.getMaxAllowedSpeed());
                    settings.put("duplicate_detection_enabled", source.isEnableDuplicateDetection());
                    settings.put("duplicate_detection_threshold_minutes", source.getDuplicateDetectionThresholdMinutes());
                    return settings;
                })
                .toList();
    }

    /**
     * When the exported timeline was built and whether it is up to date. If points arrived after it was built, or a
     * rebuild is pending or failing, the user saw a stale timeline and a fresh regeneration may already differ.
     */
    private Map<String, Object> timelineStatus(UUID userId, ExportRange range, List<TimelineStayEntity> stays,
                                               List<TimelineTripEntity> trips) {
        Instant lastBuiltAt = null;
        for (TimelineStayEntity stay : stays) {
            lastBuiltAt = latest(lastBuiltAt, stay.getLastUpdated());
        }
        for (TimelineTripEntity trip : trips) {
            lastBuiltAt = latest(lastBuiltAt, trip.getLastUpdated());
        }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("last_built_at", lastBuiltAt != null ? lastBuiltAt.toString() : null);
        if (lastBuiltAt != null) {
            status.put("points_received_after_last_build", ((Number) gpsPointRepository.getEntityManager()
                    .createNativeQuery("""
                            SELECT COUNT(*) FROM gps_points
                            WHERE user_id = :userId AND timestamp >= :start AND timestamp <= :end
                              AND created_at > :lastBuiltAt
                            """)
                    .setParameter("userId", userId)
                    .setParameter("start", range.start())
                    .setParameter("end", range.end())
                    .setParameter("lastBuiltAt", lastBuiltAt)
                    .getSingleResult()).longValue());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rebuildRows = gpsPointRepository.getEntityManager().createNativeQuery("""
                        SELECT rebuild_from, failed_attempts, last_failed_at, last_error, created_at
                        FROM timeline_rebuild_requests WHERE user_id = :userId
                        """)
                .setParameter("userId", userId)
                .getResultList();
        if (!rebuildRows.isEmpty()) {
            Object[] row = rebuildRows.getFirst();
            Map<String, Object> rebuild = new LinkedHashMap<>();
            rebuild.put("rebuild_from", instantText(row[0]));
            rebuild.put("failed_attempts", row[1]);
            rebuild.put("last_failed_at", instantText(row[2]));
            rebuild.put("last_error", row[3]);
            rebuild.put("requested_at", instantText(row[4]));
            status.put("pending_rebuild", rebuild);
        } else {
            status.put("pending_rebuild", null);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> campaignRows = gpsPointRepository.getEntityManager().createNativeQuery("""
                        SELECT c.campaign_key, c.reason, cu.status, cu.attempts, cu.last_error, cu.completed_at,
                               cu.created_at
                        FROM timeline_regeneration_campaign_users cu
                        JOIN timeline_regeneration_campaigns c ON c.id = cu.campaign_id
                        WHERE cu.user_id = :userId
                        ORDER BY cu.created_at DESC
                        LIMIT 5
                        """)
                .setParameter("userId", userId)
                .getResultList();
        status.put("regeneration_campaigns", campaignRows.stream()
                .map(row -> {
                    Map<String, Object> campaign = new LinkedHashMap<>();
                    campaign.put("campaign_key", row[0]);
                    campaign.put("reason", row[1]);
                    campaign.put("status", row[2]);
                    campaign.put("attempts", row[3]);
                    campaign.put("last_error", row[4]);
                    campaign.put("completed_at", instantText(row[5]));
                    campaign.put("created_at", instantText(row[6]));
                    return campaign;
                })
                .toList());
        return status;
    }

    private static String instantText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant().toString();
        }
        return value.toString();
    }

    private static Instant latest(Instant current, Instant candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    /**
     * Validate that coordinate shift won't push ANY points out of valid range.
     * Checks ALL GPS points in the date range using efficient min/max queries.
     */
    private void validateCoordinateShift(UUID userId, DebugExportRequest request, ExportRange range) {
        // Get min/max latitude from ALL points in range using native SQL query
        // Uses PostGIS ST_Y function to extract latitude from coordinates geometry
        Object[] bounds = (Object[]) gpsPointRepository.getEntityManager()
                .createNativeQuery(
                        "SELECT MIN(ST_Y(coordinates)), MAX(ST_Y(coordinates)) " +
                                "FROM gps_points " +
                                "WHERE user_id = :userId " +
                                "AND timestamp >= :startDate " +
                                "AND timestamp <= :endDate"
                )
                .setParameter("userId", userId)
                .setParameter("startDate", range.start())
                .setParameter("endDate", range.end())
                .getSingleResult();

        if (bounds[0] == null || bounds[1] == null) {
            log.warn("No GPS points found for validation");
            return;
        }

        Double minLat = ((Number) bounds[0]).doubleValue();
        Double maxLat = ((Number) bounds[1]).doubleValue();

        // Check if shift would push ANY point out of bounds
        double shiftedMinLat = minLat + request.getLatitudeShift();
        double shiftedMaxLat = maxLat + request.getLatitudeShift();

        if (shiftedMinLat < -90.0 || shiftedMaxLat > 90.0) {
            // Calculate safe shift range
            double maxAllowedPositiveShift = 90.0 - maxLat;
            double maxAllowedNegativeShift = -90.0 - minLat;

            throw new IllegalArgumentException(
                    String.format(
                            "Invalid coordinate shift. Your GPS data spans latitude %.6f° to %.6f°. " +
                                    "With latitude shift of %.6f°, points would be shifted to %.6f° to %.6f°, " +
                                    "which exceeds valid range [-90°, 90°]. " +
                                    "Please use a latitude shift between %.6f° and %.6f°.",
                            minLat,
                            maxLat,
                            request.getLatitudeShift(),
                            shiftedMinLat,
                            shiftedMaxLat,
                            maxAllowedNegativeShift,
                            maxAllowedPositiveShift
                    )
            );
        }

        log.info("Coordinate shift validation passed");
    }

    /**
     * Export metadata, including the user's description of the problem.
     */
    private Map<String, Object> generateMetadata(DebugExportRequest request, ExportRange range, String timezone,
                                                 Map<String, Object> counts) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("export_version", EXPORT_VERSION);
        metadata.put("export_date", Instant.now().toString());
        metadata.put("app_version", applicationVersion);
        metadata.put("coordinate_shift_applied", true);
        metadata.put("anonymization_applied", true);
        metadata.put("timezone", timezone);

        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("description", blankToNull(request.getIssueDescription()));
        issue.put("time", request.getIssueTime() != null ? request.getIssueTime().toString() : null);
        metadata.put("issue", issue);

        metadata.put("requested_range", dateRange(range.requestedStart(), range.requestedEnd()));
        metadata.put("exported_range", dateRange(range.start(), range.end()));
        // Kept for importers that predate exported_range
        metadata.put("original_date_range", dateRange(range.start(), range.end()));
        metadata.put("include_timeline", request.isIncludeTimeline());
        metadata.put("include_configuration", request.isIncludeConfiguration());

        metadata.putAll(counts);

        // Server settings outside the timeline configuration that decide how stays are matched to places
        Map<String, Object> serverSettings = new LinkedHashMap<>();
        serverSettings.put("geocoding_cache_spatial_tolerance_meters", geocodingSpatialToleranceMeters);
        serverSettings.put("geocoding_cache_max_bbox_area_km2", geocodingMaxBboxAreaKm2);
        serverSettings.put("favorites_max_distance_from_area_meters", favoritesMaxDistanceFromArea);
        metadata.put("server_settings", serverSettings);

        return metadata;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static void progress(ExportJob job, int progress, String stepKey, String fallback) {
        if (job != null) {
            job.updateProgress(progress, stepKey, fallback);
        }
    }

    private static void progress(ExportJob job, int progress, String stepKey, String fallback, Map<String, Object> params) {
        if (job != null) {
            job.updateProgress(progress, stepKey, fallback, params);
        }
    }

    private static Map<String, String> dateRange(Instant start, Instant end) {
        Map<String, String> dateRange = new LinkedHashMap<>();
        dateRange.put("start", start.toString());
        dateRange.put("end", end.toString());
        return dateRange;
    }

    /**
     * Generate shifted and anonymized favorite locations JSON.
     */
    private byte[] generateShiftedFavoriteLocationsData(FavoriteLocationsDto favoriteLocations, Shift shift,
                                                        Anonymizer anonymizer) throws IOException {
        if (favoriteLocations == null || favoriteLocations.getPoints() == null || favoriteLocations.getPoints().isEmpty()) {
            return "[]".getBytes();
        }

        // Anonymize and shift favorite locations
        AtomicInteger counter = new AtomicInteger(1);
        List<Map<String, Object>> anonymizedLocations = favoriteLocations.getPoints().stream()
                .map(location -> {
                    String name = "Location " + counter.getAndIncrement();  // Anonymized name
                    anonymizer.favoriteNames.put(location.getId(), name);
                    Map<String, Object> shifted = new LinkedHashMap<>();
                    shifted.put("id", location.getId());
                    shifted.put("name", name);
                    shifted.put("type", location.getType());
                    shifted.put("latitude", shift.latitude(location.getLatitude()));
                    shifted.put("longitude", shift.longitude(location.getLongitude()));
                    return shifted;
                })
                .toList();

        return writeJson(anonymizedLocations);
    }

    /**
     * Generate shifted and anonymized favorite areas JSON.
     */
    private byte[] generateShiftedFavoriteAreasData(FavoriteLocationsDto favoriteLocations, Shift shift,
                                                    Anonymizer anonymizer) throws IOException {
        if (favoriteLocations == null || favoriteLocations.getAreas() == null || favoriteLocations.getAreas().isEmpty()) {
            return "[]".getBytes();
        }

        // Anonymize and shift favorite areas
        AtomicInteger counter = new AtomicInteger(1);
        List<Map<String, Object>> anonymizedAreas = favoriteLocations.getAreas().stream()
                .map(area -> {
                    String name = "Area " + counter.getAndIncrement();  // Anonymized name
                    anonymizer.favoriteNames.put(area.getId(), name);
                    Map<String, Object> shifted = new LinkedHashMap<>();
                    shifted.put("id", area.getId());
                    shifted.put("name", name);
                    shifted.put("type", area.getType());

                    // Shift all 4 boundary coordinates
                    shifted.put("northEastLat", shift.latitude(area.getNorthEastLat()));
                    shifted.put("northEastLon", shift.longitude(area.getNorthEastLon()));
                    shifted.put("southWestLat", shift.latitude(area.getSouthWestLat()));
                    shifted.put("southWestLon", shift.longitude(area.getSouthWestLon()));

                    // City/country will be wrong due to shift - set to null
                    shifted.put("city", null);
                    shifted.put("country", null);

                    return shifted;
                })
                .toList();

        return writeJson(anonymizedAreas);
    }

    private void shiftAndAnonymize(ReverseGeocodingDataDto geocoding, Shift shift, Anonymizer anonymizer) {
        for (ReverseGeocodingDataDto.ReverseGeocodingLocationDto location : geocoding.getLocations()) {
            location.setRequestLatitude(shift.latitude(location.getRequestLatitude()));
            location.setRequestLongitude(shift.longitude(location.getRequestLongitude()));
            location.setResultLatitude(shift.latitude(location.getResultLatitude()));
            location.setResultLongitude(shift.longitude(location.getResultLongitude()));
            location.setBoundingBoxNorthEastLatitude(shift.latitude(location.getBoundingBoxNorthEastLatitude()));
            location.setBoundingBoxNorthEastLongitude(shift.longitude(location.getBoundingBoxNorthEastLongitude()));
            location.setBoundingBoxSouthWestLatitude(shift.latitude(location.getBoundingBoxSouthWestLatitude()));
            location.setBoundingBoxSouthWestLongitude(shift.longitude(location.getBoundingBoxSouthWestLongitude()));

            String placeName = anonymizer.place(location.getDisplayName());
            anonymizer.geocodingNames.put(location.getId(), placeName);
            location.setDisplayName(placeName);
            location.setCity(anonymizer.city(location.getCity()));
            location.setCountry(anonymizer.country(location.getCountry()));
        }
    }

    private void shiftAndAnonymize(TimelineOverridesDataDto overrides, Shift shift, Anonymizer anonymizer) {
        for (TimelineOverridesDataDto.TripMovementOverrideDto override : overrides.getTripMovementOverrides()) {
            override.setSourceStartLatitude(shift.latitude(override.getSourceStartLatitude()));
            override.setSourceStartLongitude(shift.longitude(override.getSourceStartLongitude()));
            override.setSourceEndLatitude(shift.latitude(override.getSourceEndLatitude()));
            override.setSourceEndLongitude(shift.longitude(override.getSourceEndLongitude()));
        }
        for (TimelineOverridesDataDto.DataGapStayOverrideDto override : overrides.getDataGapStayOverrides()) {
            override.setSelectedLatitude(shift.latitude(override.getSelectedLatitude()));
            override.setSelectedLongitude(shift.longitude(override.getSelectedLongitude()));
            override.setSourceBeforeLatitude(shift.latitude(override.getSourceBeforeLatitude()));
            override.setSourceBeforeLongitude(shift.longitude(override.getSourceBeforeLongitude()));
            override.setSourceAfterLatitude(shift.latitude(override.getSourceAfterLatitude()));
            override.setSourceAfterLongitude(shift.longitude(override.getSourceAfterLongitude()));
            override.setSelectedLocationName(anonymizer.location(override.getSelectedFavoriteId(),
                    override.getSelectedGeocodingId(), override.getSelectedLocationName()));
        }
        for (TimelineOverridesDataDto.TripStaySplitOverrideDto override : overrides.getTripStaySplitOverrides()) {
            override.setStayLatitude(shift.latitude(override.getStayLatitude()));
            override.setStayLongitude(shift.longitude(override.getStayLongitude()));
            override.setSourceStartLatitude(shift.latitude(override.getSourceStartLatitude()));
            override.setSourceStartLongitude(shift.longitude(override.getSourceStartLongitude()));
            override.setSourceEndLatitude(shift.latitude(override.getSourceEndLatitude()));
            override.setSourceEndLongitude(shift.longitude(override.getSourceEndLongitude()));
            override.setStayLocationName(anonymizer.location(override.getFavoriteId(), override.getGeocodingId(),
                    override.getStayLocationName()));
        }
    }

    private void shiftAndAnonymize(TimelineDataDto timeline, Shift shift, Anonymizer anonymizer) {
        for (TimelineDataDto.StayDto stay : timeline.getStays()) {
            stay.setLatitude(shift.latitude(stay.getLatitude()));
            stay.setLongitude(shift.longitude(stay.getLongitude()));
            stay.setAddress(anonymizer.location(stay.getFavoriteId(), stay.getGeocodingId(), stay.getAddress()));
        }
        for (TimelineDataDto.TripDto trip : timeline.getTrips()) {
            trip.setStartLatitude(shift.latitude(trip.getStartLatitude()));
            trip.setStartLongitude(shift.longitude(trip.getStartLongitude()));
            trip.setEndLatitude(shift.latitude(trip.getEndLatitude()));
            trip.setEndLongitude(shift.longitude(trip.getEndLongitude()));
        }
    }

    private byte[] writeJson(Object value) throws IOException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
    }

    private static void addEntry(ZipOutputStream zipOut, String name, byte[] data) throws IOException {
        zipOut.putNextEntry(new ZipEntry(name));
        zipOut.write(data);
        zipOut.closeEntry();
        log.info("Added {} to archive: {} bytes", name, data.length);
    }

    private static int sizeOf(List<?> values) {
        return values != null ? values.size() : 0;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    /**
     * The range the user asked for and the wider range actually exported.
     */
    record ExportRange(Instant requestedStart, Instant requestedEnd, Instant start, Instant end) {
        boolean overlaps(Instant from, Instant to) {
            return from != null && to != null && !from.isAfter(end) && !to.isBefore(start);
        }
    }

    private final class Shift {
        private final double latitudeShift;
        private final double longitudeShift;

        private Shift(double latitudeShift, double longitudeShift) {
            this.latitudeShift = latitudeShift;
            this.longitudeShift = longitudeShift;
        }

        Double latitude(Double latitude) {
            return latitude == null ? null : coordinateShiftService.shiftLatitude(latitude, latitudeShift);
        }

        Double longitude(Double longitude) {
            return longitude == null ? null : coordinateShiftService.shiftLongitude(longitude, longitudeShift);
        }
    }

    /**
     * Replaces names with numbered placeholders. Equal names get equal placeholders, so stays that the engine merges or
     * keeps apart by name still are after anonymization.
     */
    private static final class Anonymizer {
        final Map<Long, String> favoriteNames = new HashMap<>();
        final Map<Long, String> geocodingNames = new HashMap<>();
        private final Map<String, String> places = new HashMap<>();
        private final Map<String, String> cities = new HashMap<>();
        private final Map<String, String> countries = new HashMap<>();

        String place(String name) {
            return label(places, "Place", name);
        }

        String city(String name) {
            return label(cities, "City", name);
        }

        String country(String name) {
            return label(countries, "Country", name);
        }

        /**
         * The placeholder of the favorite or geocoding row a location points to, else of its own name.
         */
        String location(Long favoriteId, Long geocodingId, String name) {
            if (favoriteId != null && favoriteNames.containsKey(favoriteId)) {
                return favoriteNames.get(favoriteId);
            }
            if (geocodingId != null && geocodingNames.containsKey(geocodingId)) {
                return geocodingNames.get(geocodingId);
            }
            return place(name);
        }

        private static String label(Map<String, String> labels, String prefix, String name) {
            if (name == null) {
                return null;
            }
            return labels.computeIfAbsent(name, ignored -> prefix + " " + (labels.size() + 1));
        }
    }
}

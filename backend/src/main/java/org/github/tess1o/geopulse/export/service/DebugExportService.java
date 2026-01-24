package org.github.tess1o.geopulse.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.model.DebugExportRequest;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationsDto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service for generating debug exports with privacy-preserving coordinate shifts.
 * Exports GPS data in OwnTracks format and timeline configuration in a ZIP archive.
 */
@ApplicationScoped
@Slf4j
public class DebugExportService {

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    GpsPointMapper gpsPointMapper;

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

    /**
     * Generate debug export with shifted GPS coordinates, timeline configuration, and favorite locations.
     * Returns a ZIP file containing:
     * - metadata.json: Export metadata
     * - gps_data.json: OwnTracks format with shifted coordinates
     * - timeline_config.json: User's timeline configuration
     * - favorite_locations.json: Anonymized favorite locations with shifted coordinates
     * - favorite_areas.json: Anonymized favorite areas with shifted boundaries
     *
     * @param userId  User ID
     * @param request Debug export request with date range and coordinate shifts
     * @return ZIP archive as byte array
     * @throws IOException if export fails
     */
    public byte[] generateDebugExport(UUID userId, DebugExportRequest request) throws IOException {
        log.info("Starting debug export for user {} from {} to {}",
                userId, request.getStartDate(), request.getEndDate());

        log.info("Using coordinate shift: lat={}, lon={}",
                request.getLatitudeShift(), request.getLongitudeShift());

        // Validate shift won't push coordinates too far out of bounds
        validateCoordinateShift(userId, request);

        // Count GPS points for metadata
        long gpsPointCount = gpsPointRepository.countByUser(userId);

        // Get favorite locations for metadata
        FavoriteLocationsDto favoriteLocations = favoriteLocationService.getFavorites(userId);
        int locationsCount = favoriteLocations != null && favoriteLocations.getPoints() != null
                ? favoriteLocations.getPoints().size() : 0;
        int areasCount = favoriteLocations != null && favoriteLocations.getAreas() != null
                ? favoriteLocations.getAreas().size() : 0;

        // Create ZIP archive
        ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
        try (ZipOutputStream zipOut = new ZipOutputStream(zipBaos)) {

            // 1. Add metadata.json
            zipOut.putNextEntry(new ZipEntry("metadata.json"));
            byte[] metadataData = generateMetadata(userId, request, gpsPointCount, locationsCount, areasCount);
            zipOut.write(metadataData);
            zipOut.closeEntry();
            log.info("Added metadata.json to archive: {} bytes", metadataData.length);

            // 2. Add gps_data.json (OwnTracks format with shifted coordinates)
            zipOut.putNextEntry(new ZipEntry("gps_data.json"));
            byte[] gpsData = generateShiftedOwnTracksData(userId, request);
            zipOut.write(gpsData);
            zipOut.closeEntry();
            log.info("Added gps_data.json to archive: {} bytes", gpsData.length);

            // 3. Add timeline_config.json (timeline configuration)
            if (request.isIncludeConfiguration()) {
                zipOut.putNextEntry(new ZipEntry("timeline_config.json"));
                byte[] configData = generateTimelineConfigData(userId);
                zipOut.write(configData);
                zipOut.closeEntry();
                log.info("Added timeline_config.json to archive: {} bytes", configData.length);
            }

            // 4. Add favorite_locations.json (shifted + anonymized)
            zipOut.putNextEntry(new ZipEntry("favorite_locations.json"));
            byte[] locationsData = generateShiftedFavoriteLocationsData(userId, request);
            zipOut.write(locationsData);
            zipOut.closeEntry();
            log.info("Added favorite_locations.json to archive: {} bytes", locationsData.length);

            // 5. Add favorite_areas.json (shifted + anonymized)
            zipOut.putNextEntry(new ZipEntry("favorite_areas.json"));
            byte[] areasData = generateShiftedFavoriteAreasData(userId, request);
            zipOut.write(areasData);
            zipOut.closeEntry();
            log.info("Added favorite_areas.json to archive: {} bytes", areasData.length);

            zipOut.finish();
        }

        byte[] result = zipBaos.toByteArray();
        log.info("Completed debug export: {} bytes total", result.length);

        return result;
    }

    /**
     * Generate OwnTracks JSON with shifted coordinates.
     */
    private byte[] generateShiftedOwnTracksData(UUID userId, DebugExportRequest request) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Stream OwnTracks messages with shifted coordinates
        var batchSize = streamingExportService.getBatchSize();
        streamingExportService.streamJsonArray(
                baos,
                // Fetch batch function
                page -> gpsPointRepository.findByUserAndDateRange(
                        userId,
                        request.getStartDate(),
                        request.getEndDate(),
                        page,
                        batchSize,
                        "timestamp",
                        "asc"
                ),
                // Convert GPS point to OwnTracks message with shifted coordinates
                gpsPoint -> {
                    OwnTracksLocationMessage msg = gpsPointMapper.toOwnTracksLocationMessage(gpsPoint);
                    if (msg != null && msg.getLat() != null && msg.getLon() != null) {
                        // Apply coordinate shift
                        msg.setLat(coordinateShiftService.shiftLatitude(msg.getLat(), request.getLatitudeShift()));
                        msg.setLon(coordinateShiftService.shiftLongitude(msg.getLon(), request.getLongitudeShift()));
                    }
                    return msg;
                },
                // No progress tracking for debug export
                null,
                -1,
                0,
                100,
                "Exporting GPS points"
        );

        return baos.toByteArray();
    }

    /**
     * Generate timeline configuration JSON.
     */
    private byte[] generateTimelineConfigData(UUID userId) throws IOException {
        TimelineConfig config = timelineConfigurationProvider.getConfigurationForUser(userId);
        return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(config);
    }

    /**
     * Validate that coordinate shift won't push ANY points out of valid range.
     * Checks ALL GPS points in the date range using efficient min/max queries.
     */
    private void validateCoordinateShift(UUID userId, DebugExportRequest request) {
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
                .setParameter("startDate", request.getStartDate())
                .setParameter("endDate", request.getEndDate())
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

        log.info("Coordinate shift validation passed. Data range: [{}, {}], shifted range: [{}, {}]",
                minLat, maxLat, shiftedMinLat, shiftedMaxLat);
    }

    /**
     * Generate metadata JSON.
     */
    private byte[] generateMetadata(UUID userId, DebugExportRequest request,
                                    long gpsPointCount, int locationsCount, int areasCount) throws IOException {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("export_version", "1.0");
        metadata.put("export_date", Instant.now().toString());
        metadata.put("coordinate_shift_applied", true);
        metadata.put("anonymization_applied", true);

        Map<String, String> dateRange = new HashMap<>();
        dateRange.put("start", request.getStartDate().toString());
        dateRange.put("end", request.getEndDate().toString());
        metadata.put("original_date_range", dateRange);

        metadata.put("gps_point_count", gpsPointCount);
        metadata.put("favorite_locations_count", locationsCount);
        metadata.put("favorite_areas_count", areasCount);

        return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(metadata);
    }

    /**
     * Generate shifted and anonymized favorite locations JSON.
     */
    private byte[] generateShiftedFavoriteLocationsData(UUID userId, DebugExportRequest request) throws IOException {
        FavoriteLocationsDto favoriteLocations = favoriteLocationService.getFavorites(userId);

        if (favoriteLocations == null || favoriteLocations.getPoints() == null || favoriteLocations.getPoints().isEmpty()) {
            return "[]".getBytes();
        }

        // Anonymize and shift favorite locations
        AtomicInteger counter = new AtomicInteger(1);
        List<Map<String, Object>> anonymizedLocations = favoriteLocations.getPoints().stream()
                .map(location -> {
                    Map<String, Object> shifted = new HashMap<>();
                    shifted.put("name", "Location " + counter.getAndIncrement());  // Anonymized name
                    shifted.put("type", location.getType());
                    shifted.put("latitude", coordinateShiftService.shiftLatitude(
                            location.getLatitude(), request.getLatitudeShift()));
                    shifted.put("longitude", coordinateShiftService.shiftLongitude(
                            location.getLongitude(), request.getLongitudeShift()));
                    return shifted;
                })
                .toList();

        return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(anonymizedLocations);
    }

    /**
     * Generate shifted and anonymized favorite areas JSON.
     */
    private byte[] generateShiftedFavoriteAreasData(UUID userId, DebugExportRequest request) throws IOException {
        FavoriteLocationsDto favoriteLocations = favoriteLocationService.getFavorites(userId);

        if (favoriteLocations == null || favoriteLocations.getAreas() == null || favoriteLocations.getAreas().isEmpty()) {
            return "[]".getBytes();
        }

        // Anonymize and shift favorite areas
        AtomicInteger counter = new AtomicInteger(1);
        List<Map<String, Object>> anonymizedAreas = favoriteLocations.getAreas().stream()
                .map(area -> {
                    Map<String, Object> shifted = new HashMap<>();
                    shifted.put("name", "Area " + counter.getAndIncrement());  // Anonymized name
                    shifted.put("type", area.getType());

                    // Shift all 4 boundary coordinates
                    shifted.put("northEastLat", coordinateShiftService.shiftLatitude(
                            area.getNorthEastLat(), request.getLatitudeShift()));
                    shifted.put("northEastLon", coordinateShiftService.shiftLongitude(
                            area.getNorthEastLon(), request.getLongitudeShift()));
                    shifted.put("southWestLat", coordinateShiftService.shiftLatitude(
                            area.getSouthWestLat(), request.getLatitudeShift()));
                    shifted.put("southWestLon", coordinateShiftService.shiftLongitude(
                            area.getSouthWestLon(), request.getLongitudeShift()));

                    // City/country will be wrong due to shift - set to null
                    shifted.put("city", null);
                    shifted.put("country", null);

                    return shifted;
                })
                .toList();

        return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(anonymizedAreas);
    }
}

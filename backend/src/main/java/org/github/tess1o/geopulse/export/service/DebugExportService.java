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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
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

    /**
     * Generate debug export with shifted GPS coordinates and timeline configuration.
     * Returns a ZIP file containing:
     * - gps_data.json: OwnTracks format with shifted coordinates
     * - timeline_config.json: User's timeline configuration
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

        // Create ZIP archive
        ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
        try (ZipOutputStream zipOut = new ZipOutputStream(zipBaos)) {

            // 1. Add gps_data.json (OwnTracks format with shifted coordinates)
            zipOut.putNextEntry(new ZipEntry("gps_data.json"));
            byte[] gpsData = generateShiftedOwnTracksData(userId, request);
            zipOut.write(gpsData);
            zipOut.closeEntry();

            log.info("Added gps_data.json to archive: {} bytes", gpsData.length);

            // 2. Add timeline_config.json (timeline configuration)
            if (request.isIncludeConfiguration()) {
                zipOut.putNextEntry(new ZipEntry("timeline_config.json"));
                byte[] configData = generateTimelineConfigData(userId);
                zipOut.write(configData);
                zipOut.closeEntry();

                log.info("Added timeline_config.json to archive: {} bytes", configData.length);
            }

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
        streamingExportService.streamJsonArray(
                baos,
                // Fetch batch function
                page -> gpsPointRepository.findByUserAndDateRange(
                        userId,
                        request.getStartDate(),
                        request.getEndDate(),
                        page,
                        StreamingExportService.DEFAULT_BATCH_SIZE,
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
}

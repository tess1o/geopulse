package org.github.tess1o.geopulse.importdata.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.dto.*;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.IMPORT_JSON;

/**
 * GeoPulse import: raw GPS points, and clearing the GPS points an import will replace.
 */
@ApplicationScoped
@Slf4j
public class GeoPulseGpsImporter {

    @Inject
    UserRepository userRepository;

    @Inject
    BatchProcessor batchProcessor;

    @Inject
    ImportDataClearingService dataClearingService;

    @Transactional
    public Instant importRawGpsData(byte[] content, ImportJob job) throws IOException {
        RawGpsDataDto gpsData = IMPORT_JSON.readValue(content, RawGpsDataDto.class);
        log.info("Importing {} GPS points for user {} using BatchProcessor", gpsData.getPoints().size(), job.getUserId());

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
            if (job.getOptions().isOutsideTimeRange(pointDto.getTimestamp())) {
                continue;
            }

            try {
                GpsPointEntity gpsEntity = new GpsPointEntity();
                gpsEntity.setUser(user);
                gpsEntity.setDeviceId(pointDto.getDeviceId() != null ? pointDto.getDeviceId() : "geopulse-import");
                gpsEntity.setCoordinates(GeoUtils.createPoint(pointDto.getLongitude(), pointDto.getLatitude()));
                gpsEntity.setTimestamp(pointDto.getTimestamp());
                // Use original source type from export data
                try {
                    GpsSourceType sourceType = GpsSourceType.valueOf(pointDto.getSource());
                    gpsEntity.setSourceType(sourceType);
                } catch (IllegalArgumentException e) {
                    // Fallback to GPX if original source is invalid/unknown
                    log.warn("Unknown source type '{}' for GPS point, using GPX as fallback", pointDto.getSource());
                    gpsEntity.setSourceType(GpsSourceType.GPX);
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

    public void clearGpsDataForImport(byte[] content, ImportJob job) throws IOException {
        try {
            RawGpsDataDto gpsData = IMPORT_JSON.readValue(content, RawGpsDataDto.class);

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

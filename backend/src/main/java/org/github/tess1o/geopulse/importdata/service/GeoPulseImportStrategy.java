package org.github.tess1o.geopulse.importdata.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.dto.ExportMetadataDto;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportPhase;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.shared.exportimport.SequenceResetService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.IMPORT_JSON;

/**
 * Import strategy for GeoPulse ZIP format exports. Reads the archive and imports its files in dependency order;
 * each data type is handled by one of the GeoPulse*Importer beans.
 */
@ApplicationScoped
@Slf4j
public class GeoPulseImportStrategy implements ImportStrategy {

    @Inject
    SequenceResetService sequenceResetService;

    @Inject
    TimelineImportHelper timelineImportHelper;

    @Inject
    GeoPulseGpsImporter gpsImporter;

    @Inject
    GeoPulseTimelineSnapshotImporter timelineSnapshotImporter;

    @Inject
    GeoPulseTimelineDetailsImporter timelineDetailsImporter;

    @Inject
    GeoPulsePlacesImporter placesImporter;

    @Inject
    GeoPulseAccountImporter accountImporter;

    @Inject
    GeoPulsePlanningImporter planningImporter;

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
                    case ExportImportConstants.FileNames.TIMELINE_LABELS:
                        detectedDataTypes.add(ExportImportConstants.DataTypes.TIMELINE_LABELS);
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
                List<String> requestedDataTypes = job.getOptions().getDataTypes();
                if (dataType != null && requestedDataTypes != null && !requestedDataTypes.contains(dataType)) {
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
            placesImporter.importReverseGeocodingData(fileContents.get(ExportImportConstants.FileNames.REVERSE_GEOCODING), job, referenceMaps);
            totalProgress += 5;
            job.setProgress(totalProgress);
        }

        // 2. Import favorites (no dependencies)
        if (fileContents.containsKey(ExportImportConstants.FileNames.FAVORITES)) {
            placesImporter.importFavoritesData(fileContents.get(ExportImportConstants.FileNames.FAVORITES), job, referenceMaps);
            totalProgress += 10;
            job.setProgress(totalProgress);
        }

        // Check if GPS data exists for import
        boolean hasGpsData = fileContents.containsKey(ExportImportConstants.FileNames.RAW_GPS_DATA);
        boolean hasTimelineData = fileContents.containsKey(ExportImportConstants.FileNames.TIMELINE_DATA);
        boolean snapshotRestore = job.getOptions().isSnapshotRestore();

        // Handle data clearing before import if requested
        if (job.getOptions().isClearDataBeforeImport()) {
            job.setPhase(ImportPhase.CLEARING_EXISTING_DATA);
            clearExistingDataBeforeImport(fileContents, job);
            totalProgress += 10;
            job.setProgress(totalProgress);
            job.setPhase(ImportPhase.IMPORTING);
        }

        // 3. Import GPS data first
        Instant firstGpsTimestamp = null;
        if (hasGpsData) {
            firstGpsTimestamp = gpsImporter.importRawGpsData(fileContents.get(ExportImportConstants.FileNames.RAW_GPS_DATA), job);
            log.info("Successfully imported raw GPS data for user {} - first timestamp: {}", job.getUserId(), firstGpsTimestamp);
            totalProgress += 30;
            job.setProgress(totalProgress);
        }

        // 4. Full backup restores are snapshots: import stored timeline rows instead of regenerating.
        if (snapshotRestore && hasTimelineData) {
            timelineSnapshotImporter.importTimelineDataSnapshot(fileContents.get(ExportImportConstants.FileNames.TIMELINE_DATA), job, referenceMaps);
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
            accountImporter.importUserInfoData(fileContents.get(ExportImportConstants.FileNames.USER_INFO), job,
                    readExportVersion(fileContents));
            totalProgress += 5;
            job.setProgress(totalProgress);
        }

        // 6. Import location sources
        if (fileContents.containsKey(ExportImportConstants.FileNames.LOCATION_SOURCES)) {
            accountImporter.importLocationSourcesData(fileContents.get(ExportImportConstants.FileNames.LOCATION_SOURCES), job);
            totalProgress += 5;
            job.setProgress(totalProgress);
        }

        if (fileContents.containsKey(ExportImportConstants.FileNames.TIMELINE_LABELS)) {
            timelineDetailsImporter.importTimelineLabelsData(fileContents.get(ExportImportConstants.FileNames.TIMELINE_LABELS), job, referenceMaps);
        }

        if (fileContents.containsKey(ExportImportConstants.FileNames.TIMELINE_OVERRIDES)) {
            timelineDetailsImporter.importTimelineOverridesData(fileContents.get(ExportImportConstants.FileNames.TIMELINE_OVERRIDES), job, referenceMaps);
        }

        if (fileContents.containsKey(ExportImportConstants.FileNames.NOTIFICATION_TEMPLATES)) {
            planningImporter.importNotificationTemplatesData(fileContents.get(ExportImportConstants.FileNames.NOTIFICATION_TEMPLATES), job, referenceMaps);
        }

        if (fileContents.containsKey(ExportImportConstants.FileNames.TRIP_WORKSPACE)) {
            planningImporter.importTripWorkspaceData(fileContents.get(ExportImportConstants.FileNames.TRIP_WORKSPACE), job, referenceMaps);
        }

        if (fileContents.containsKey(ExportImportConstants.FileNames.GEOFENCING)) {
            planningImporter.importGeofencingData(fileContents.get(ExportImportConstants.FileNames.GEOFENCING), job, referenceMaps);
        }

        if (fileContents.containsKey(ExportImportConstants.FileNames.NOTES)) {
            timelineDetailsImporter.importNotesData(fileContents.get(ExportImportConstants.FileNames.NOTES), job, referenceMaps);
        }

        if (fileContents.containsKey(ExportImportConstants.FileNames.WEATHER_SAMPLES)) {
            timelineDetailsImporter.importWeatherSamplesData(fileContents.get(ExportImportConstants.FileNames.WEATHER_SAMPLES), job);
        }

        if (fileContents.containsKey(ExportImportConstants.FileNames.MAP_MATCHING)) {
            timelineDetailsImporter.importMapMatchingData(fileContents.get(ExportImportConstants.FileNames.MAP_MATCHING), job, referenceMaps);
        }

        if (fileContents.containsKey(ExportImportConstants.FileNames.FRIENDS)) {
            accountImporter.importFriendsData(fileContents.get(ExportImportConstants.FileNames.FRIENDS), job);
        }

        if (fileContents.containsKey(ExportImportConstants.FileNames.FRIEND_PERMISSIONS)) {
            accountImporter.importFriendPermissionsData(fileContents.get(ExportImportConstants.FileNames.FRIEND_PERMISSIONS), job);
        }
    }

    private String readExportVersion(Map<String, byte[]> fileContents) throws IOException {
        byte[] metadata = fileContents.get(ExportImportConstants.FileNames.METADATA);
        return metadata != null ? IMPORT_JSON.readValue(metadata, ExportMetadataDto.class).getVersion() : null;
    }

    private void validateMetadata(ZipInputStream zis, ImportJob job) throws IOException {
        byte[] content = zis.readAllBytes();
        ExportMetadataDto metadata = IMPORT_JSON.readValue(content, ExportMetadataDto.class);

        if (!ExportImportConstants.Versions.CURRENT.equals(metadata.getVersion()) &&
                !ExportImportConstants.Versions.V1_1.equals(metadata.getVersion()) &&
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
            case ExportImportConstants.FileNames.TIMELINE_LABELS:
                return ExportImportConstants.DataTypes.TIMELINE_LABELS;
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

    /**
     * Clear existing GPS data before GeoPulse import based on the date ranges in the import file.
     * Normal imports let timeline regeneration replace derived timeline rows; snapshot
     * restores clear timeline rows up front and import them directly from the backup.
     */
    private void clearExistingDataBeforeImport(Map<String, byte[]> fileContents, ImportJob job) throws IOException {
        log.info("Clearing existing GPS data before GeoPulse import for user {}", job.getUserId());

        // Calculate deletion range for GPS data if present
        if (fileContents.containsKey(ExportImportConstants.FileNames.RAW_GPS_DATA)) {
            gpsImporter.clearGpsDataForImport(fileContents.get(ExportImportConstants.FileNames.RAW_GPS_DATA), job);
        }

        if (job.getOptions().isSnapshotRestore()
                && fileContents.containsKey(ExportImportConstants.FileNames.TIMELINE_DATA)) {
            timelineSnapshotImporter.clearTimelineSnapshotData(job.getUserId());
        }
    }
}

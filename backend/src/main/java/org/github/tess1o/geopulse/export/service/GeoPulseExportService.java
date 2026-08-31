package org.github.tess1o.geopulse.export.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.dto.*;
import org.github.tess1o.geopulse.export.mapper.ExportDataMapper;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

@ApplicationScoped
@Slf4j
public class GeoPulseExportService {

    @Inject
    ExportDependencyResolver dependencyResolver;

    @Inject
    ExportTempFileService tempFileService;

    @Inject
    ExportDataMapper exportDataMapper;

    @Inject
    StreamingZipExportService streamingZipExportService;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    TimelineTripRepository timelineTripRepository;

    @Inject
    TimelineDataGapRepository timelineDataGapRepository;

    @Inject
    ExportDataCollectorService dataCollectorService;

    @Inject
    StreamingExportService streamingExportService;

    /**
     * Generates a GeoPulse native format export (ZIP archive containing multiple data types).
     * Uses STREAMING approach to write directly to a temporary file to avoid memory issues.
     * <p>
     * This is the "geopulse" format export that can include:
     * - Raw GPS data
     * - Timeline (trips, stays, data gaps)
     * - Favorites (locations and areas)
     * - User info and location sources
     * - Reverse geocoding data
     *
     * @param job the export job specification
     * @throws IOException if an I/O error occurs
     */
    public void generateGeoPulseNativeExport(ExportJob job) throws IOException {
        log.info("Starting GeoPulse native format export for user {}", job.getUserId());

        job.updateProgress(5, "Initializing export...");

        // Create temp file
        java.nio.file.Path tempFile = tempFileService.createTempFile(job.getJobId(), ".zip");

        try (java.io.OutputStream os = java.nio.file.Files.newOutputStream(tempFile);
             ZipOutputStream zos = new ZipOutputStream(os)) {

            job.updateProgress(10, "Adding metadata...");

            // Add metadata file (small, no streaming needed)
            addMetadataFile(zos, job);

            // Collect dependencies if timeline is being exported
            Set<String> actualDataTypes = new HashSet<>(job.getDataTypes());
            if (job.getDataTypes().contains(ExportImportConstants.DataTypes.TIMELINE)) {
                dependencyResolver.collectTimelineDependencies(job, actualDataTypes);
            }

            // Calculate progress segments for each data type
            int progressPerType = 70 / actualDataTypes.size(); // Reserve 70% for data export
            int currentProgress = 15;

            // Export dependencies first (order matters for import)
            if (actualDataTypes.contains(ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION)) {
                addReverseGeocodingData(zos, job, currentProgress);
                currentProgress += progressPerType;
            }

            if (actualDataTypes.contains(ExportImportConstants.DataTypes.FAVORITES)) {
                addFavoritesData(zos, job, currentProgress);
                currentProgress += progressPerType;
            }

            // Add requested data types using streaming
            for (String dataType : job.getDataTypes()) {
                switch (dataType.toLowerCase()) {
                    case ExportImportConstants.DataTypes.RAW_GPS:
                        addRawGpsDataStreaming(zos, job, currentProgress, currentProgress + progressPerType);
                        currentProgress += progressPerType;
                        break;
                    case ExportImportConstants.DataTypes.TIMELINE:
                        addTimelineDataStreaming(zos, job, currentProgress, currentProgress + progressPerType);
                        currentProgress += progressPerType;
                        break;
                    case ExportImportConstants.DataTypes.DATA_GAPS:
                        addDataGapsData(zos, job, currentProgress);
                        currentProgress += progressPerType;
                        break;
                    case ExportImportConstants.DataTypes.USER_INFO:
                        addUserInfoData(zos, job);
                        currentProgress += progressPerType;
                        break;
                    case ExportImportConstants.DataTypes.LOCATION_SOURCES:
                        addLocationSourcesData(zos, job);
                        currentProgress += progressPerType;
                        break;
                    case ExportImportConstants.DataTypes.PERIOD_TAGS:
                        addPeriodTagsData(zos, job, currentProgress);
                        currentProgress += progressPerType;
                        break;
                    case ExportImportConstants.DataTypes.TIMELINE_OVERRIDES:
                        addTimelineOverridesData(zos, job, currentProgress);
                        currentProgress += progressPerType;
                        break;
                    case ExportImportConstants.DataTypes.TRIP_WORKSPACE:
                        addTripWorkspaceData(zos, job, currentProgress);
                        currentProgress += progressPerType;
                        break;
                    case ExportImportConstants.DataTypes.NOTIFICATION_TEMPLATES:
                        addNotificationTemplatesData(zos, job, currentProgress);
                        currentProgress += progressPerType;
                        break;
                    case ExportImportConstants.DataTypes.GEOFENCING:
                        addGeofencingData(zos, job, currentProgress);
                        currentProgress += progressPerType;
                        break;
                    case ExportImportConstants.DataTypes.NOTES:
                        addNotesData(zos, job, currentProgress);
                        currentProgress += progressPerType;
                        break;
                    case ExportImportConstants.DataTypes.WEATHER_SAMPLES:
                        addWeatherSamplesData(zos, job, currentProgress);
                        currentProgress += progressPerType;
                        break;
                    case ExportImportConstants.DataTypes.MAP_MATCHING:
                        addMapMatchingData(zos, job, currentProgress);
                        currentProgress += progressPerType;
                        break;
                    case ExportImportConstants.DataTypes.FRIENDS:
                        addFriendsData(zos, job, currentProgress);
                        currentProgress += progressPerType;
                        break;
                    case ExportImportConstants.DataTypes.FRIEND_PERMISSIONS:
                        addFriendPermissionsData(zos, job, currentProgress);
                        currentProgress += progressPerType;
                        break;
                    case ExportImportConstants.DataTypes.FAVORITES:
                    case ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION:
                        // Already handled above to ensure proper dependency order
                        break;
                    default:
                        log.warn("Unknown data type requested: {}", dataType);
                }
            }

            job.updateProgress(90, "Finalizing ZIP archive...");

            zos.finish();

            // Update job with file info
            job.setTempFilePath(tempFile.toString());
            job.setFileExtension(".zip");
            job.setContentType("application/zip");
            job.setFileSizeBytes(java.nio.file.Files.size(tempFile));

            job.updateProgress(95, "Export completed");
            log.info("Completed GeoPulse native format export");
        }
    }

    // ========================================
    // Private helper methods for ZIP export using STREAMING
    // ========================================

    private void addMetadataFile(ZipOutputStream zos, ExportJob job) throws IOException {
        ExportMetadataDto metadata = exportDataMapper.toMetadataDto(job);
        streamingZipExportService.addSimpleJsonFileToZip(zos, ExportImportConstants.FileNames.METADATA, metadata);
    }

    /**
     * Adds raw GPS data to ZIP using STREAMING to avoid memory issues.
     */
    private void addRawGpsDataStreaming(ZipOutputStream zos, ExportJob job, int progressStart, int progressEnd)
            throws IOException {
        log.debug("Streaming raw GPS data export for user {}", job.getUserId());

        job.updateProgress(progressStart, "Exporting GPS data...");

        int batchSize = streamingExportService.getBatchSize();

        streamingZipExportService.<GpsPointEntity, RawGpsDataDto.GpsPointDto>addStreamingJsonFileToZip(
                zos,
                ExportImportConstants.FileNames.RAW_GPS_DATA,
                // Write metadata fields
                (gen, mapper) -> {
                    try {
                        gen.writeStringField("dataType", "rawGps");
                        gen.writeStringField("exportDate", java.time.Instant.now().toString());
                        gen.writeStringField("startDate", job.getDateRange().getStartDate().toString());
                        gen.writeStringField("endDate", job.getDateRange().getEndDate().toString());
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to write GPS metadata", e);
                    }
                },
                // Array field name
                "points",
                batchConsumer -> gpsPointRepository.streamByUserAndDateRangeForExport(
                        job.getUserId(),
                        job.getDateRange().getStartDate(),
                        job.getDateRange().getEndDate(),
                        batchSize,
                        batchConsumer),
                // Convert entity to DTO
                gpsPoint -> exportDataMapper.toGpsPointDto(gpsPoint),
                // Progress tracking
                job,
                progressStart,
                progressEnd,
                "Exporting GPS points");

        log.debug("Completed streaming raw GPS data export");
    }

    /**
     * Adds timeline data to ZIP using STREAMING.
     * Timeline data is smaller (simplified trips/stays) so we can use a hybrid
     * approach.
     */
    private void addTimelineDataStreaming(ZipOutputStream zos, ExportJob job, int progressStart, int progressEnd)
            throws IOException {
        log.debug("Streaming timeline data export for user {}", job.getUserId());

        job.updateProgress(progressStart, "Exporting timeline data...");

        // Timeline data is already aggregated/simplified, so it's usually small enough
        // But we'll still stream it for consistency
        streamingZipExportService.<TimelineDataGapEntity, TimelineDataDto.DataGapDto>addStreamingJsonFileToZip(
                zos,
                ExportImportConstants.FileNames.TIMELINE_DATA,
                // Write metadata fields
                (gen, mapper) -> {
                    try {
                        gen.writeStringField("dataType", "timeline");
                        gen.writeStringField("exportDate", java.time.Instant.now().toString());
                        gen.writeStringField("startDate", job.getDateRange().getStartDate().toString());
                        gen.writeStringField("endDate", job.getDateRange().getEndDate().toString());

                        // Write stays array
                        gen.writeArrayFieldStart("stays");
                        var stays = timelineStayRepository.findByUserAndDateRange(
                                job.getUserId(),
                                job.getDateRange().getStartDate(),
                                job.getDateRange().getEndDate());
                        for (var stay : stays) {
                            gen.writeObject(exportDataMapper.toStayDto(stay));
                        }
                        gen.writeEndArray();

                        // Write trips array (within the same JSON file)
                        // Will be written before dataGaps array
                        gen.writeArrayFieldStart("trips");
                        var trips = timelineTripRepository.findByUserAndDateRange(
                                job.getUserId(),
                                job.getDateRange().getStartDate(),
                                job.getDateRange().getEndDate());
                        for (var trip : trips) {
                            gen.writeObject(exportDataMapper.toTripDto(trip));
                        }
                        gen.writeEndArray();

                    } catch (IOException e) {
                        throw new RuntimeException("Failed to write timeline metadata", e);
                    }
                },
                // Array field name for data gaps
                "dataGaps",
                // Fetch data gaps batch
                batchConsumer -> batchConsumer.accept(
                        timelineDataGapRepository.findByUserIdAndTimeRange(
                            job.getUserId(),
                            job.getDateRange().getStartDate(),
                            job.getDateRange().getEndDate())),
                // Convert entity to DTO
                gap -> exportDataMapper.toDataGapDto(gap),
                // Progress tracking
                job,
                progressStart,
                progressEnd,
                "Exporting timeline");

        log.debug("Completed streaming timeline data export");
    }

    private void addDataGapsData(ZipOutputStream zos, ExportJob job, int progressStart)
            throws IOException {
        log.debug("Exporting data gaps for user {}", job.getUserId());

        job.updateProgress(progressStart, "Exporting data gaps...");

        var dataGaps = timelineDataGapRepository.findByUserIdAndTimeRange(
                job.getUserId(),
                job.getDateRange().getStartDate(),
                job.getDateRange().getEndDate());

        DataGapsDataDto dataGapsData = DataGapsDataDto.builder()
                .dataType("dataGaps")
                .exportDate(java.time.Instant.now())
                .startDate(job.getDateRange().getStartDate())
                .endDate(job.getDateRange().getEndDate())
                .dataGaps(dataGaps.stream()
                        .map(exportDataMapper::toDataGapDto)
                        .collect(java.util.stream.Collectors.toList()))
                .build();

        streamingZipExportService.addSimpleJsonFileToZip(zos, ExportImportConstants.FileNames.DATA_GAPS, dataGapsData);

        log.debug("Exported {} data gaps", dataGaps.size());
    }

    private void addFavoritesData(ZipOutputStream zos, ExportJob job, int progressStart)
            throws IOException {
        log.debug("Exporting favorites data for user {}", job.getUserId());

        job.updateProgress(progressStart, "Exporting favorites...");

        var favorites = dataCollectorService.collectFavorites(job.getUserId());
        FavoritesDataDto favoritesData = exportDataMapper.toFavoritesDataDto(favorites);
        streamingZipExportService.addSimpleJsonFileToZip(zos, ExportImportConstants.FileNames.FAVORITES, favoritesData);

        log.debug("Exported {} favorite points and {} favorite areas",
                favoritesData.getPoints().size(), favoritesData.getAreas().size());
    }

    private void addUserInfoData(ZipOutputStream zos, ExportJob job) throws IOException {
        log.debug("Exporting user info for user {}", job.getUserId());

        var user = dataCollectorService.collectUserInfo(job.getUserId());
        UserInfoDataDto userInfoData = exportDataMapper.toUserInfoDataDto(user);
        streamingZipExportService.addSimpleJsonFileToZip(zos, ExportImportConstants.FileNames.USER_INFO, userInfoData);

        log.debug("Exported user info for user {}", user.getEmail());
    }

    private void addLocationSourcesData(ZipOutputStream zos, ExportJob job) throws IOException {
        log.debug("Exporting location sources for user {}", job.getUserId());

        var sources = dataCollectorService.collectLocationSources(job.getUserId());
        LocationSourcesDataDto sourcesData = exportDataMapper.toLocationSourcesDataDto(sources);
        streamingZipExportService.addSimpleJsonFileToZip(zos, ExportImportConstants.FileNames.LOCATION_SOURCES,
                sourcesData);

        log.debug("Exported {} location sources", sources.size());
    }

    private void addReverseGeocodingData(ZipOutputStream zos, ExportJob job, int progressStart)
            throws IOException {
        log.debug("Exporting reverse geocoding data for user {}", job.getUserId());

        job.updateProgress(progressStart, "Exporting reverse geocoding data...");

        var stays = timelineStayRepository.findByUserAndDateRange(
                job.getUserId(),
                job.getDateRange().getStartDate(),
                job.getDateRange().getEndDate());

        Set<Long> geocodingIds = dependencyResolver.extractGeocodingIds(stays);

        if (geocodingIds.isEmpty()) {
            log.debug("No reverse geocoding locations to export");
            return;
        }

        var geocodingLocations = dataCollectorService.collectReverseGeocodingLocations(geocodingIds);
        ReverseGeocodingDataDto geocodingData = exportDataMapper.toReverseGeocodingDataDto(geocodingLocations);
        streamingZipExportService.addSimpleJsonFileToZip(zos, ExportImportConstants.FileNames.REVERSE_GEOCODING,
                geocodingData);

        log.debug("Exported {} reverse geocoding locations", geocodingLocations.size());
    }

    private void addPeriodTagsData(ZipOutputStream zos, ExportJob job, int progressStart) throws IOException {
        job.updateProgress(progressStart, "Exporting period tags...");
        var tags = dataCollectorService.collectPeriodTags(job);
        streamingZipExportService.addSimpleJsonFileToZip(
                zos,
                ExportImportConstants.FileNames.PERIOD_TAGS,
                exportDataMapper.toPeriodTagsDataDto(tags, job)
        );
        log.debug("Exported {} period tags", tags.size());
    }

    private void addTimelineOverridesData(ZipOutputStream zos, ExportJob job, int progressStart) throws IOException {
        job.updateProgress(progressStart, "Exporting timeline overrides...");
        var tripOverrides = dataCollectorService.collectTripMovementOverrides(job.getUserId());
        var gapOverrides = dataCollectorService.collectDataGapStayOverrides(job.getUserId());
        streamingZipExportService.addSimpleJsonFileToZip(
                zos,
                ExportImportConstants.FileNames.TIMELINE_OVERRIDES,
                exportDataMapper.toTimelineOverridesDataDto(tripOverrides, gapOverrides)
        );
        log.debug("Exported {} trip movement overrides and {} data-gap stay overrides",
                tripOverrides.size(), gapOverrides.size());
    }

    private void addTripWorkspaceData(ZipOutputStream zos, ExportJob job, int progressStart) throws IOException {
        job.updateProgress(progressStart, "Exporting trip workspace...");
        var trips = dataCollectorService.collectTrips(job.getUserId());
        List<Long> tripIds = trips.stream().map(trip -> trip.getId()).toList();
        Map<Long, List<org.github.tess1o.geopulse.trips.model.entity.TripPlanItemEntity>> planItemsByTripId =
                dataCollectorService.collectTripPlanItems(tripIds).stream()
                        .collect(Collectors.groupingBy(item -> item.getTrip().getId()));
        Map<Long, List<org.github.tess1o.geopulse.trips.model.entity.TripCollaboratorEntity>> collaboratorsByTripId =
                dataCollectorService.collectTripCollaborators(tripIds).stream()
                        .collect(Collectors.groupingBy(collaborator -> collaborator.getTrip().getId()));

        streamingZipExportService.addSimpleJsonFileToZip(
                zos,
                ExportImportConstants.FileNames.TRIP_WORKSPACE,
                exportDataMapper.toTripWorkspaceDataDto(trips, planItemsByTripId, collaboratorsByTripId)
        );
        log.debug("Exported {} trip workspace records", trips.size());
    }

    private void addNotificationTemplatesData(ZipOutputStream zos, ExportJob job, int progressStart) throws IOException {
        job.updateProgress(progressStart, "Exporting notification templates...");
        var templates = dataCollectorService.collectNotificationTemplates(job.getUserId());
        streamingZipExportService.addSimpleJsonFileToZip(
                zos,
                ExportImportConstants.FileNames.NOTIFICATION_TEMPLATES,
                exportDataMapper.toNotificationTemplatesDataDto(templates)
        );
        log.debug("Exported {} notification templates", templates.size());
    }

    private void addGeofencingData(ZipOutputStream zos, ExportJob job, int progressStart) throws IOException {
        job.updateProgress(progressStart, "Exporting geofencing rules...");
        var rules = dataCollectorService.collectGeofenceRules(job.getUserId());
        streamingZipExportService.addSimpleJsonFileToZip(
                zos,
                ExportImportConstants.FileNames.GEOFENCING,
                exportDataMapper.toGeofencingDataDto(rules)
        );
        log.debug("Exported {} geofence rules", rules.size());
    }

    private void addNotesData(ZipOutputStream zos, ExportJob job, int progressStart) throws IOException {
        job.updateProgress(progressStart, "Exporting notes...");
        var notes = dataCollectorService.collectNotes(job);
        streamingZipExportService.addSimpleJsonFileToZip(
                zos,
                ExportImportConstants.FileNames.NOTES,
                exportDataMapper.toNotesDataDto(notes, job)
        );
        log.debug("Exported {} notes", notes.size());
    }

    private void addWeatherSamplesData(ZipOutputStream zos, ExportJob job, int progressStart) throws IOException {
        job.updateProgress(progressStart, "Exporting weather samples...");
        var samples = dataCollectorService.collectWeatherSamples(job);
        streamingZipExportService.addSimpleJsonFileToZip(
                zos,
                ExportImportConstants.FileNames.WEATHER_SAMPLES,
                exportDataMapper.toWeatherSamplesDataDto(samples, job)
        );
        log.debug("Exported {} weather samples", samples.size());
    }

    private void addMapMatchingData(ZipOutputStream zos, ExportJob job, int progressStart) throws IOException {
        job.updateProgress(progressStart, "Exporting map matching data...");
        var pathMatches = dataCollectorService.collectMapMatchingPathMatches(job);
        streamingZipExportService.addSimpleJsonFileToZip(
                zos,
                ExportImportConstants.FileNames.MAP_MATCHING,
                exportDataMapper.toMapMatchingDataDto(pathMatches, job)
        );
        log.debug("Exported {} map matching path matches", pathMatches.size());
    }

    private void addFriendsData(ZipOutputStream zos, ExportJob job, int progressStart) throws IOException {
        job.updateProgress(progressStart, "Exporting friends...");
        var friends = dataCollectorService.collectFriends(job.getUserId());
        streamingZipExportService.addSimpleJsonFileToZip(
                zos,
                ExportImportConstants.FileNames.FRIENDS,
                exportDataMapper.toFriendsDataDto(friends)
        );
        log.debug("Exported {} directed friend relationships", friends.size());
    }

    private void addFriendPermissionsData(ZipOutputStream zos, ExportJob job, int progressStart) throws IOException {
        job.updateProgress(progressStart, "Exporting friend permissions...");
        var permissions = dataCollectorService.collectFriendPermissions(job.getUserId());
        streamingZipExportService.addSimpleJsonFileToZip(
                zos,
                ExportImportConstants.FileNames.FRIEND_PERMISSIONS,
                exportDataMapper.toFriendPermissionsDataDto(permissions)
        );
        log.debug("Exported {} friend permission records", permissions.size());
    }
}

package org.github.tess1o.geopulse.export.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.dto.*;
import org.github.tess1o.geopulse.export.mapper.ExportDataMapper;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipOutputStream;

/**
 * Main orchestrator for export operations using STREAMING approach.
 * Delegates to specialized services for format-specific exports and data collection.
 *
 */
@ApplicationScoped
@Slf4j
public class ExportDataGenerator {

    @Inject
    ExportDependencyResolver dependencyResolver;

    @Inject
    GpxExportService gpxExportService;

    @Inject
    GeoJsonExportService geoJsonExportService;

    @Inject
    OwnTracksExportService ownTracksExportService;

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

    /**
     * Generates a ZIP archive containing exported data in native format using STREAMING.
     * Memory-efficient: processes data in batches without loading everything into memory.
     *
     * @param job the export job specification
     * @return the ZIP archive as bytes
     * @throws IOException if an I/O error occurs
     */
    public byte[] generateExportZip(ExportJob job) throws IOException {
        log.info("Starting streaming ZIP export for user {}", job.getUserId());

        job.updateProgress(5, "Initializing export...");

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

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
                addReverseGeocodingData(zos, job, currentProgress, currentProgress + progressPerType);
                currentProgress += progressPerType;
            }

            if (actualDataTypes.contains(ExportImportConstants.DataTypes.FAVORITES)) {
                addFavoritesData(zos, job, currentProgress, currentProgress + progressPerType);
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
                        addDataGapsData(zos, job, currentProgress, currentProgress + progressPerType);
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
            byte[] result = baos.toByteArray();

            job.updateProgress(95, "Export completed");
            log.info("Completed streaming ZIP export: {} bytes", result.length);

            return result;
        }
    }

    /**
     * Generates an OwnTracks format export.
     */
    public byte[] generateOwnTracksExport(ExportJob job) throws IOException {
        return ownTracksExportService.generateOwnTracksExport(job);
    }

    /**
     * Generates a GeoJSON format export.
     */
    public byte[] generateGeoJsonExport(ExportJob job) throws IOException {
        return geoJsonExportService.generateGeoJsonExport(job);
    }

    /**
     * Generates a GPX format export.
     */
    public byte[] generateGpxExport(ExportJob job, boolean zipPerTrip, String zipGroupBy) throws IOException {
        return gpxExportService.generateGpxExport(job, zipPerTrip, zipGroupBy);
    }

    /**
     * Generates a GPX file for a single trip.
     */
    public byte[] generateSingleTripGpx(java.util.UUID userId, Long tripId) throws IOException {
        return gpxExportService.generateSingleTripGpx(userId, tripId);
    }

    /**
     * Generates a GPX file for a single stay.
     */
    public byte[] generateSingleStayGpx(java.util.UUID userId, Long stayId) throws IOException {
        return gpxExportService.generateSingleStayGpx(userId, stayId);
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

        streamingZipExportService.addStreamingJsonFileToZip(
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
            // Fetch batch function
            page -> gpsPointRepository.findByUserAndDateRange(
                job.getUserId(),
                job.getDateRange().getStartDate(),
                job.getDateRange().getEndDate(),
                page,
                StreamingExportService.DEFAULT_BATCH_SIZE,
                "timestamp",
                "asc"
            ),
            // Convert entity to DTO
            gpsPoint -> exportDataMapper.toGpsPointDto(gpsPoint),
            // Progress tracking
            job,
            progressStart,
            progressEnd,
            "Exporting GPS points"
        );

        log.debug("Completed streaming raw GPS data export");
    }

    /**
     * Adds timeline data to ZIP using STREAMING.
     * Timeline data is smaller (simplified trips/stays) so we can use a hybrid approach.
     */
    private void addTimelineDataStreaming(ZipOutputStream zos, ExportJob job, int progressStart, int progressEnd)
            throws IOException {
        log.debug("Streaming timeline data export for user {}", job.getUserId());

        job.updateProgress(progressStart, "Exporting timeline data...");

        // Timeline data is already aggregated/simplified, so it's usually small enough
        // But we'll still stream it for consistency
        streamingZipExportService.addStreamingJsonFileToZip(
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
                        job.getDateRange().getEndDate()
                    );
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
                        job.getDateRange().getEndDate()
                    );
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
            page -> {
                if (page > 0) return java.util.Collections.emptyList(); // Only one batch
                return timelineDataGapRepository.findByUserIdAndTimeRange(
                    job.getUserId(),
                    job.getDateRange().getStartDate(),
                    job.getDateRange().getEndDate()
                );
            },
            // Convert entity to DTO
            gap -> exportDataMapper.toDataGapDto(gap),
            // Progress tracking
            job,
            progressStart,
            progressEnd,
            "Exporting timeline"
        );

        log.debug("Completed streaming timeline data export");
    }

    private void addDataGapsData(ZipOutputStream zos, ExportJob job, int progressStart, int progressEnd)
            throws IOException {
        log.debug("Exporting data gaps for user {}", job.getUserId());

        job.updateProgress(progressStart, "Exporting data gaps...");

        var dataGaps = timelineDataGapRepository.findByUserIdAndTimeRange(
            job.getUserId(),
            job.getDateRange().getStartDate(),
            job.getDateRange().getEndDate()
        );

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

    private void addFavoritesData(ZipOutputStream zos, ExportJob job, int progressStart, int progressEnd)
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
        streamingZipExportService.addSimpleJsonFileToZip(zos, ExportImportConstants.FileNames.LOCATION_SOURCES, sourcesData);

        log.debug("Exported {} location sources", sources.size());
    }

    private void addReverseGeocodingData(ZipOutputStream zos, ExportJob job, int progressStart, int progressEnd)
            throws IOException {
        log.debug("Exporting reverse geocoding data for user {}", job.getUserId());

        job.updateProgress(progressStart, "Exporting reverse geocoding data...");

        var stays = timelineStayRepository.findByUserAndDateRange(
            job.getUserId(),
            job.getDateRange().getStartDate(),
            job.getDateRange().getEndDate()
        );

        Set<Long> geocodingIds = dependencyResolver.extractGeocodingIds(stays);

        if (geocodingIds.isEmpty()) {
            log.debug("No reverse geocoding locations to export");
            return;
        }

        var geocodingLocations = dataCollectorService.collectReverseGeocodingLocations(geocodingIds);
        ReverseGeocodingDataDto geocodingData = exportDataMapper.toReverseGeocodingDataDto(geocodingLocations);
        streamingZipExportService.addSimpleJsonFileToZip(zos, ExportImportConstants.FileNames.REVERSE_GEOCODING, geocodingData);

        log.debug("Exported {} reverse geocoding locations", geocodingLocations.size());
    }
}

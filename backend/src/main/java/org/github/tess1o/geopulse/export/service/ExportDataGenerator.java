package org.github.tess1o.geopulse.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.dto.*;
import org.github.tess1o.geopulse.export.mapper.ExportDataMapper;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipOutputStream;

/**
 * Main orchestrator for export operations.
 * Delegates to specialized services for format-specific exports and data collection.
 */
@ApplicationScoped
@Slf4j
public class ExportDataGenerator {

    @Inject
    ExportDataCollectorService dataCollectorService;

    @Inject
    ExportDependencyResolver dependencyResolver;

    @Inject
    ZipFileService zipFileService;

    @Inject
    GpxExportService gpxExportService;

    @Inject
    GeoJsonExportService geoJsonExportService;

    @Inject
    OwnTracksExportService ownTracksExportService;

    @Inject
    ExportDataMapper exportDataMapper;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    /**
     * Generates a ZIP archive containing exported data in native format.
     *
     * @param job the export job specification
     * @return the ZIP archive as bytes
     * @throws IOException if an I/O error occurs
     */
    public byte[] generateExportZip(ExportJob job) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            // Add metadata file
            addMetadataFile(zos, job);

            // Collect dependencies if timeline is being exported
            Set<String> actualDataTypes = new HashSet<>(job.getDataTypes());
            if (job.getDataTypes().contains(ExportImportConstants.DataTypes.TIMELINE)) {
                dependencyResolver.collectTimelineDependencies(job, actualDataTypes);
            }

            // Export dependencies first (order matters for import)
            if (actualDataTypes.contains(ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION)) {
                addReverseGeocodingData(zos, job);
            }
            if (actualDataTypes.contains(ExportImportConstants.DataTypes.FAVORITES)) {
                addFavoritesData(zos, job);
            }

            // Add requested data types
            for (String dataType : job.getDataTypes()) {
                switch (dataType.toLowerCase()) {
                    case ExportImportConstants.DataTypes.RAW_GPS:
                        addRawGpsData(zos, job);
                        break;
                    case ExportImportConstants.DataTypes.TIMELINE:
                        addTimelineData(zos, job);
                        break;
                    case ExportImportConstants.DataTypes.DATA_GAPS:
                        addDataGapsData(zos, job);
                        break;
                    case ExportImportConstants.DataTypes.USER_INFO:
                        addUserInfoData(zos, job);
                        break;
                    case ExportImportConstants.DataTypes.LOCATION_SOURCES:
                        addLocationSourcesData(zos, job);
                        break;
                    case ExportImportConstants.DataTypes.FAVORITES:
                    case ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION:
                        // Already handled above to ensure proper dependency order
                        break;
                    default:
                        log.warn("Unknown data type requested: {}", dataType);
                }
            }

            zos.finish();
            return baos.toByteArray();
        }
    }

    /**
     * Generates an OwnTracks format export.
     *
     * @param job the export job
     * @return the OwnTracks JSON as bytes
     * @throws IOException if an I/O error occurs
     */
    public byte[] generateOwnTracksExport(ExportJob job) throws IOException {
        return ownTracksExportService.generateOwnTracksExport(job);
    }

    /**
     * Generates a GeoJSON format export.
     *
     * @param job the export job
     * @return the GeoJSON as bytes
     * @throws IOException if an I/O error occurs
     */
    public byte[] generateGeoJsonExport(ExportJob job) throws IOException {
        return geoJsonExportService.generateGeoJsonExport(job);
    }

    /**
     * Generates a GPX format export.
     *
     * @param job        the export job
     * @param zipPerTrip if true, creates separate GPX files per trip/stay in a ZIP
     * @return the GPX file or ZIP archive as bytes
     * @throws IOException if an I/O error occurs
     */
    public byte[] generateGpxExport(ExportJob job, boolean zipPerTrip) throws IOException {
        return gpxExportService.generateGpxExport(job, zipPerTrip);
    }

    /**
     * Generates a GPX file for a single trip.
     *
     * @param userId the user ID
     * @param tripId the trip ID
     * @return the GPX file as bytes
     * @throws IOException if an I/O error occurs
     */
    public byte[] generateSingleTripGpx(java.util.UUID userId, Long tripId) throws IOException {
        return gpxExportService.generateSingleTripGpx(userId, tripId);
    }

    /**
     * Generates a GPX file for a single stay.
     *
     * @param userId the user ID
     * @param stayId the stay ID
     * @return the GPX file as bytes
     * @throws IOException if an I/O error occurs
     */
    public byte[] generateSingleStayGpx(java.util.UUID userId, Long stayId) throws IOException {
        return gpxExportService.generateSingleStayGpx(userId, stayId);
    }

    // ========================================
    // Private helper methods for ZIP export
    // ========================================

    private void addMetadataFile(ZipOutputStream zos, ExportJob job) throws IOException {
        ExportMetadataDto metadata = exportDataMapper.toMetadataDto(job);
        zipFileService.addJsonFileToZip(zos, ExportImportConstants.FileNames.METADATA, metadata);
    }

    private void addRawGpsData(ZipOutputStream zos, ExportJob job) throws IOException {
        log.debug("Exporting raw GPS data for user {}", job.getUserId());

        var allPoints = dataCollectorService.collectGpsPoints(job);
        RawGpsDataDto gpsData = exportDataMapper.toRawGpsDataDto(allPoints, job);
        zipFileService.addJsonFileToZip(zos, ExportImportConstants.FileNames.RAW_GPS_DATA, gpsData);

        log.debug("Exported {} GPS points", allPoints.size());
    }

    private void addTimelineData(ZipOutputStream zos, ExportJob job) throws IOException {
        log.debug("Exporting timeline data for user {}", job.getUserId());

        var stays = dataCollectorService.collectTimelineStays(job);
        var trips = dataCollectorService.collectTimelineTrips(job);
        var dataGaps = dataCollectorService.collectDataGaps(job);

        TimelineDataDto timelineData = exportDataMapper.toTimelineDataDto(stays, trips, dataGaps, job);
        zipFileService.addJsonFileToZip(zos, ExportImportConstants.FileNames.TIMELINE_DATA, timelineData);

        log.debug("Exported {} stays, {} trips and {} data gaps", stays.size(), trips.size(), dataGaps.size());
    }

    private void addDataGapsData(ZipOutputStream zos, ExportJob job) throws IOException {
        log.debug("Exporting data gaps for user {}", job.getUserId());

        var dataGaps = dataCollectorService.collectDataGaps(job);

        DataGapsDataDto dataGapsData = DataGapsDataDto.builder()
                .dataType("dataGaps")
                .exportDate(java.time.Instant.now())
                .startDate(job.getDateRange().getStartDate())
                .endDate(job.getDateRange().getEndDate())
                .dataGaps(dataGaps.stream()
                        .map(exportDataMapper::toDataGapDto)
                        .collect(java.util.stream.Collectors.toList()))
                .build();

        zipFileService.addJsonFileToZip(zos, ExportImportConstants.FileNames.DATA_GAPS, dataGapsData);

        log.debug("Exported {} data gaps", dataGaps.size());
    }

    private void addFavoritesData(ZipOutputStream zos, ExportJob job) throws IOException {
        log.debug("Exporting favorites data for user {}", job.getUserId());

        var favorites = dataCollectorService.collectFavorites(job.getUserId());
        FavoritesDataDto favoritesData = exportDataMapper.toFavoritesDataDto(favorites);
        zipFileService.addJsonFileToZip(zos, ExportImportConstants.FileNames.FAVORITES, favoritesData);

        log.debug("Exported {} favorite points and {} favorite areas",
                favoritesData.getPoints().size(), favoritesData.getAreas().size());
    }

    private void addUserInfoData(ZipOutputStream zos, ExportJob job) throws IOException {
        log.debug("Exporting user info for user {}", job.getUserId());

        var user = dataCollectorService.collectUserInfo(job.getUserId());
        UserInfoDataDto userInfoData = exportDataMapper.toUserInfoDataDto(user);
        zipFileService.addJsonFileToZip(zos, ExportImportConstants.FileNames.USER_INFO, userInfoData);

        log.debug("Exported user info for user {}", user.getEmail());
    }

    private void addLocationSourcesData(ZipOutputStream zos, ExportJob job) throws IOException {
        log.debug("Exporting location sources for user {}", job.getUserId());

        var sources = dataCollectorService.collectLocationSources(job.getUserId());
        LocationSourcesDataDto sourcesData = exportDataMapper.toLocationSourcesDataDto(sources);
        zipFileService.addJsonFileToZip(zos, ExportImportConstants.FileNames.LOCATION_SOURCES, sourcesData);

        log.debug("Exported {} location sources", sources.size());
    }

    private void addReverseGeocodingData(ZipOutputStream zos, ExportJob job) throws IOException {
        log.debug("Exporting reverse geocoding data for user {}", job.getUserId());

        var stays = dataCollectorService.collectTimelineStays(job);
        Set<Long> geocodingIds = dependencyResolver.extractGeocodingIds(stays);

        if (geocodingIds.isEmpty()) {
            log.debug("No reverse geocoding locations to export");
            return;
        }

        var geocodingLocations = dataCollectorService.collectReverseGeocodingLocations(geocodingIds);
        ReverseGeocodingDataDto geocodingData = exportDataMapper.toReverseGeocodingDataDto(geocodingLocations);
        zipFileService.addJsonFileToZip(zos, ExportImportConstants.FileNames.REVERSE_GEOCODING, geocodingData);

        log.debug("Exported {} reverse geocoding locations", geocodingLocations.size());
    }
}
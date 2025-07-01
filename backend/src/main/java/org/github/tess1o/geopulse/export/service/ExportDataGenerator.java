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
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@ApplicationScoped
@Slf4j
public class ExportDataGenerator {

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    TimelineTripRepository timelineTripRepository;

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsSourceRepository gpsSourceRepository;

    @Inject
    ReverseGeocodingLocationRepository reverseGeocodingLocationRepository;

    @Inject
    ExportDataMapper exportDataMapper;

    @Inject
    GpsPointMapper gpsPointMapper;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    public byte[] generateExportZip(ExportJob job) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            // Add metadata file
            addMetadataFile(zos, job);

            // Collect dependencies if timeline is being exported
            Set<String> actualDataTypes = new HashSet<>(job.getDataTypes());
            if (job.getDataTypes().contains(ExportImportConstants.DataTypes.TIMELINE)) {
                collectTimelineDependencies(job, actualDataTypes);
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

    public byte[] generateOwnTracksExport(ExportJob job) throws IOException {
        log.debug("Generating OwnTracks export for user {}", job.getUserId());

        // Use pagination to handle large datasets
        int pageSize = 1000;
        int page = 0;
        var allPoints = new java.util.ArrayList<org.github.tess1o.geopulse.gps.model.GpsPointEntity>();

        while (true) {
            var pageData = gpsPointRepository.findByUserAndDateRange(
                    job.getUserId(),
                    job.getDateRange().getStartDate(),
                    job.getDateRange().getEndDate(),
                    page,
                    pageSize
            );

            if (pageData.isEmpty()) {
                break;
            }

            allPoints.addAll(pageData);
            page++;
        }

        // Convert GPS points to OwnTracks format
        var ownTracksMessages = gpsPointMapper.toOwnTracksLocationMessages(allPoints);

        // Create JSON array for OwnTracks format
        String json = objectMapper.writeValueAsString(ownTracksMessages);

        log.debug("Generated OwnTracks export with {} GPS points", allPoints.size());
        return json.getBytes();
    }

    private void addMetadataFile(ZipOutputStream zos, ExportJob job) throws IOException {
        ExportMetadataDto metadata = exportDataMapper.toMetadataDto(job);
        addJsonFileToZip(zos, ExportImportConstants.FileNames.METADATA, metadata);
    }

    private void addRawGpsData(ZipOutputStream zos, ExportJob job) throws IOException {
        log.debug("Exporting raw GPS data for user {}", job.getUserId());

        // Use pagination to handle large datasets
        int pageSize = 1000;
        int page = 0;
        var allPoints = new java.util.ArrayList<org.github.tess1o.geopulse.gps.model.GpsPointEntity>();

        while (true) {
            var pageData = gpsPointRepository.findByUserAndDateRange(
                    job.getUserId(),
                    job.getDateRange().getStartDate(),
                    job.getDateRange().getEndDate(),
                    page,
                    pageSize
            );

            if (pageData.isEmpty()) {
                break;
            }

            allPoints.addAll(pageData);
            page++;
        }

        RawGpsDataDto gpsData = exportDataMapper.toRawGpsDataDto(allPoints, job);
        addJsonFileToZip(zos, ExportImportConstants.FileNames.RAW_GPS_DATA, gpsData);

        log.debug("Exported {} GPS points", allPoints.size());
    }

    private void addTimelineData(ZipOutputStream zos, ExportJob job) throws IOException {
        log.debug("Exporting timeline data for user {}", job.getUserId());

        // Export stays
        var stays = timelineStayRepository.findByUserAndDateRange(
                job.getUserId(),
                job.getDateRange().getStartDate(),
                job.getDateRange().getEndDate()
        );

        // Export trips
        var trips = timelineTripRepository.findByUserAndDateRange(
                job.getUserId(),
                job.getDateRange().getStartDate(),
                job.getDateRange().getEndDate()
        );

        TimelineDataDto timelineData = exportDataMapper.toTimelineDataDto(stays, trips, job);
        addJsonFileToZip(zos, ExportImportConstants.FileNames.TIMELINE_DATA, timelineData);

        log.debug("Exported {} stays and {} trips", stays.size(), trips.size());
    }

    private void addFavoritesData(ZipOutputStream zos, ExportJob job) throws IOException {
        log.debug("Exporting favorites data for user {}", job.getUserId());

        var favorites = favoritesRepository.findByUserId(job.getUserId());
        FavoritesDataDto favoritesData = exportDataMapper.toFavoritesDataDto(favorites);
        addJsonFileToZip(zos, ExportImportConstants.FileNames.FAVORITES, favoritesData);

        log.debug("Exported {} favorite points and {} favorite areas", 
                favoritesData.getPoints().size(), favoritesData.getAreas().size());
    }

    private void addUserInfoData(ZipOutputStream zos, ExportJob job) throws IOException {
        log.debug("Exporting user info for user {}", job.getUserId());

        var user = userRepository.findById(job.getUserId());
        if (user == null) {
            throw new IllegalStateException("User not found: " + job.getUserId());
        }

        UserInfoDataDto userInfoData = exportDataMapper.toUserInfoDataDto(user);
        addJsonFileToZip(zos, ExportImportConstants.FileNames.USER_INFO, userInfoData);

        log.debug("Exported user info for user {}", user.getEmail());
    }

    private void addLocationSourcesData(ZipOutputStream zos, ExportJob job) throws IOException {
        log.debug("Exporting location sources for user {}", job.getUserId());

        var sources = gpsSourceRepository.findByUserId(job.getUserId());
        LocationSourcesDataDto sourcesData = exportDataMapper.toLocationSourcesDataDto(sources);
        addJsonFileToZip(zos, ExportImportConstants.FileNames.LOCATION_SOURCES, sourcesData);

        log.debug("Exported {} location sources", sources.size());
    }

    private void collectTimelineDependencies(ExportJob job, Set<String> actualDataTypes) {
        log.debug("Collecting timeline dependencies for user {}", job.getUserId());

        // Get all stays to collect dependency IDs
        var stays = timelineStayRepository.findByUserAndDateRange(
                job.getUserId(),
                job.getDateRange().getStartDate(),
                job.getDateRange().getEndDate()
        );

        // Collect unique favorite IDs
        Set<Long> favoriteIds = stays.stream()
                .filter(stay -> stay.getFavoriteLocation() != null)
                .map(stay -> stay.getFavoriteLocation().getId())
                .collect(Collectors.toSet());

        // Collect unique reverse geocoding IDs
        Set<Long> geocodingIds = stays.stream()
                .filter(stay -> stay.getGeocodingLocation() != null)
                .map(stay -> stay.getGeocodingLocation().getId())
                .collect(Collectors.toSet());

        if (!favoriteIds.isEmpty()) {
            actualDataTypes.add(ExportImportConstants.DataTypes.FAVORITES);
            log.debug("Auto-including {} favorite locations for timeline export", favoriteIds.size());
        }

        if (!geocodingIds.isEmpty()) {
            actualDataTypes.add(ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION);
            log.debug("Auto-including {} reverse geocoding locations for timeline export", geocodingIds.size());
        }
    }

    private void addReverseGeocodingData(ZipOutputStream zos, ExportJob job) throws IOException {
        log.debug("Exporting reverse geocoding data for user {}", job.getUserId());

        // Get all stays to collect reverse geocoding IDs
        var stays = timelineStayRepository.findByUserAndDateRange(
                job.getUserId(),
                job.getDateRange().getStartDate(),
                job.getDateRange().getEndDate()
        );

        // Collect unique reverse geocoding IDs
        Set<Long> geocodingIds = stays.stream()
                .filter(stay -> stay.getGeocodingLocation() != null)
                .map(stay -> stay.getGeocodingLocation().getId())
                .collect(Collectors.toSet());

        if (geocodingIds.isEmpty()) {
            log.debug("No reverse geocoding locations to export");
            return;
        }

        // Fetch the reverse geocoding locations
        var geocodingLocations = reverseGeocodingLocationRepository.findByIds(geocodingIds.stream().toList());
        ReverseGeocodingDataDto geocodingData = exportDataMapper.toReverseGeocodingDataDto(geocodingLocations);
        addJsonFileToZip(zos, ExportImportConstants.FileNames.REVERSE_GEOCODING, geocodingData);

        log.debug("Exported {} reverse geocoding locations", geocodingLocations.size());
    }

    private void addJsonFileToZip(ZipOutputStream zos, String fileName, Object data) throws IOException {
        ZipEntry entry = new ZipEntry(fileName);
        zos.putNextEntry(entry);

        String json = objectMapper.writeValueAsString(data);
        zos.write(json.getBytes());

        zos.closeEntry();
    }
}
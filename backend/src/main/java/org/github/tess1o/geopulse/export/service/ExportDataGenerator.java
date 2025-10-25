package org.github.tess1o.geopulse.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.dto.*;
import org.github.tess1o.geopulse.export.mapper.ExportDataMapper;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.gps.integrations.gpx.model.*;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.locationtech.jts.geom.Coordinate;

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
    TimelineDataGapRepository timelineDataGapRepository;

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

    private final XmlMapper xmlMapper = XmlMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
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
                    pageSize,
                    "timestamp",
                    "asc"
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

    public byte[] generateGeoJsonExport(ExportJob job) throws IOException {
        log.debug("Generating GeoJSON export for user {}", job.getUserId());

        // Use pagination to handle large datasets
        int pageSize = 1000;
        int page = 0;
        var allFeatures = new java.util.ArrayList<org.github.tess1o.geopulse.gps.integrations.geojson.model.GeoJsonFeature>();

        while (true) {
            var pageData = gpsPointRepository.findByUserAndDateRange(
                    job.getUserId(),
                    job.getDateRange().getStartDate(),
                    job.getDateRange().getEndDate(),
                    page,
                    pageSize,
                    "timestamp",
                    "asc"
            );

            if (pageData.isEmpty()) {
                break;
            }

            // Convert GPS points to GeoJSON features
            for (var gpsPoint : pageData) {
                allFeatures.add(convertGpsPointToGeoJsonFeature(gpsPoint));
            }

            page++;
        }

        // Create GeoJSON FeatureCollection
        var featureCollection = org.github.tess1o.geopulse.gps.integrations.geojson.model.GeoJsonFeatureCollection.builder()
                .type("FeatureCollection")
                .features(allFeatures)
                .build();

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(featureCollection);

        log.debug("Generated GeoJSON export with {} GPS points", allFeatures.size());
        return json.getBytes();
    }

    private org.github.tess1o.geopulse.gps.integrations.geojson.model.GeoJsonFeature convertGpsPointToGeoJsonFeature(
            org.github.tess1o.geopulse.gps.model.GpsPointEntity gpsPoint) {
        // Extract coordinates from PostGIS Point geometry
        double longitude = gpsPoint.getCoordinates().getX();
        double latitude = gpsPoint.getCoordinates().getY();

        // Create Point geometry with optional altitude
        org.github.tess1o.geopulse.gps.integrations.geojson.model.GeoJsonPoint geometry;
        if (gpsPoint.getAltitude() != null) {
            geometry = new org.github.tess1o.geopulse.gps.integrations.geojson.model.GeoJsonPoint(
                    longitude, latitude, gpsPoint.getAltitude()
            );
        } else {
            geometry = new org.github.tess1o.geopulse.gps.integrations.geojson.model.GeoJsonPoint(
                    longitude, latitude
            );
        }

        // Create properties with GPS metadata
        var properties = org.github.tess1o.geopulse.gps.integrations.geojson.model.GeoJsonProperties.builder()
                .timestamp(gpsPoint.getTimestamp().toString())
                .altitude(gpsPoint.getAltitude())
                .velocity(gpsPoint.getVelocity())
                .accuracy(gpsPoint.getAccuracy())
                .battery(gpsPoint.getBattery() != null ? gpsPoint.getBattery().intValue() : null)
                .deviceId(gpsPoint.getDeviceId())
                .sourceType(gpsPoint.getSourceType() != null ? gpsPoint.getSourceType().name() : null)
                .build();

        // Create and return Feature
        return org.github.tess1o.geopulse.gps.integrations.geojson.model.GeoJsonFeature.builder()
                .type("Feature")
                .geometry(geometry)
                .properties(properties)
                .id(gpsPoint.getId() != null ? gpsPoint.getId().toString() : null)
                .build();
    }

    public byte[] generateGpxExport(ExportJob job, boolean zipPerTrip) throws IOException {
        log.debug("Generating GPX export for user {}, zipPerTrip={}", job.getUserId(), zipPerTrip);

        if (zipPerTrip) {
            return generateGpxExportAsZip(job);
        } else {
            return generateSingleGpxFile(job);
        }
    }

    private byte[] generateSingleGpxFile(ExportJob job) throws IOException {
        GpxFile gpxFile = buildGpxFile(job);
        String xml = xmlMapper.writeValueAsString(gpxFile);
        log.debug("Generated single GPX export with {} bytes", xml.getBytes().length);
        return xml.getBytes();
    }

    private byte[] generateGpxExportAsZip(ExportJob job) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            // Get timeline trips
            var trips = timelineTripRepository.findByUserIdAndTimeRangeWithExpansion(
                    job.getUserId(),
                    job.getDateRange().getStartDate(),
                    job.getDateRange().getEndDate()
            );

            // Get timeline stays
            var stays = timelineStayRepository.findByUserIdAndTimeRangeWithExpansion(
                    job.getUserId(),
                    job.getDateRange().getStartDate(),
                    job.getDateRange().getEndDate()
            );

            log.debug("Exporting {} trips and {} stays as separate GPX files in zip", trips.size(), stays.size());

            // Export trips
            for (int i = 0; i < trips.size(); i++) {
                TimelineTripEntity trip = trips.get(i);
                GpxFile gpxFile = buildGpxFileForSingleTrip(trip);

                String filename = String.format("trip_%d_%s.gpx",
                        i + 1,
                        trip.getTimestamp().toString().substring(0, 19).replace(":", "-"));

                ZipEntry entry = new ZipEntry(filename);
                zos.putNextEntry(entry);
                String xml = xmlMapper.writeValueAsString(gpxFile);
                zos.write(xml.getBytes());
                zos.closeEntry();
            }

            // Export stays
            for (int i = 0; i < stays.size(); i++) {
                TimelineStayEntity stay = stays.get(i);
                GpxFile gpxFile = buildGpxFileForSingleStay(stay);

                String filename = String.format("stay_%d_%s.gpx",
                        i + 1,
                        stay.getTimestamp().toString().substring(0, 19).replace(":", "-"));

                ZipEntry entry = new ZipEntry(filename);
                zos.putNextEntry(entry);
                String xml = xmlMapper.writeValueAsString(gpxFile);
                zos.write(xml.getBytes());
                zos.closeEntry();
            }

            zos.finish();
            log.debug("Generated GPX zip with {} trips and {} stays, {} bytes total",
                    trips.size(), stays.size(), baos.toByteArray().length);
            return baos.toByteArray();
        }
    }

    private GpxFile buildGpxFile(ExportJob job) {
        GpxFile gpxFile = new GpxFile();
        gpxFile.setVersion("1.1");
        gpxFile.setCreator("GeoPulse");

        // Set metadata
        GpxMetadata metadata = new GpxMetadata();
        metadata.setName("GeoPulse Export");
        metadata.setDescription("GPS tracks and waypoints exported from GeoPulse");
        metadata.setTime(java.time.Instant.now());
        gpxFile.setMetadata(metadata);

        // Build tracks and waypoints
        var tracks = new java.util.ArrayList<GpxTrack>();
        var waypoints = new java.util.ArrayList<GpxWaypoint>();

        // Add raw GPS data as Track 1
        GpxTrack rawGpsTrack = buildRawGpsTrack(job);
        if (rawGpsTrack != null) {
            tracks.add(rawGpsTrack);
        }

        // Add timeline trips as separate tracks
        var tripTracks = buildTimelineTripTracks(job);
        tracks.addAll(tripTracks);

        // Add timeline stays as waypoints
        var stayWaypoints = buildTimelineStayWaypoints(job);
        waypoints.addAll(stayWaypoints);

        gpxFile.setTracks(tracks);
        gpxFile.setWaypoints(waypoints);

        return gpxFile;
    }

    private GpxTrack buildRawGpsTrack(ExportJob job) {
        // Use pagination to handle large datasets
        int pageSize = 1000;
        int page = 0;
        var allTrackPoints = new java.util.ArrayList<GpxTrackPoint>();

        while (true) {
            var pageData = gpsPointRepository.findByUserAndDateRange(
                    job.getUserId(),
                    job.getDateRange().getStartDate(),
                    job.getDateRange().getEndDate(),
                    page,
                    pageSize,
                    "timestamp",
                    "asc"
            );

            if (pageData.isEmpty()) {
                break;
            }

            for (var gpsPoint : pageData) {
                GpxTrackPoint trackPoint = new GpxTrackPoint();
                trackPoint.setLat(gpsPoint.getCoordinates().getY());
                trackPoint.setLon(gpsPoint.getCoordinates().getX());
                trackPoint.setElevation(gpsPoint.getAltitude());
                trackPoint.setTime(gpsPoint.getTimestamp());

                // Convert velocity from km/h to m/s
                if (gpsPoint.getVelocity() != null) {
                    trackPoint.setSpeed(gpsPoint.getVelocity() / 3.6);
                }

                allTrackPoints.add(trackPoint);
            }

            page++;
        }

        if (allTrackPoints.isEmpty()) {
            return null;
        }

        GpxTrackSegment segment = new GpxTrackSegment();
        segment.setTrackPoints(allTrackPoints);

        GpxTrack track = new GpxTrack();
        track.setName("Raw GPS Data");
        track.setDescription("All GPS points for the selected date range");
        track.setTrackSegments(java.util.List.of(segment));

        log.debug("Built raw GPS track with {} points", allTrackPoints.size());
        return track;
    }

    private java.util.List<GpxTrack> buildTimelineTripTracks(ExportJob job) {
        var trips = timelineTripRepository.findByUserIdAndTimeRangeWithExpansion(
                job.getUserId(),
                job.getDateRange().getStartDate(),
                job.getDateRange().getEndDate()
        );

        var tracks = new java.util.ArrayList<GpxTrack>();

        for (TimelineTripEntity trip : trips) {
            GpxTrack track = buildGpxTrackFromTrip(trip);
            if (track != null) {
                tracks.add(track);
            }
        }

        log.debug("Built {} timeline trip tracks", tracks.size());
        return tracks;
    }

    private GpxTrack buildGpxTrackFromTrip(TimelineTripEntity trip) {
        if (trip.getPath() == null || trip.getPath().getNumPoints() == 0) {
            return null;
        }

        var trackPoints = new java.util.ArrayList<GpxTrackPoint>();
        Coordinate[] coordinates = trip.getPath().getCoordinates();

        // Fetch original GPS points for this trip to get altitude data
        // The LineString stores only 2D coordinates, but we need 3D with elevation
        java.time.Instant endTime = trip.getTimestamp().plusSeconds(trip.getTripDuration());
        var gpsPoints = gpsPointRepository.findByUserAndDateRange(
                trip.getUser().getId(),
                trip.getTimestamp(),
                endTime,
                0,
                10000, // Large limit to get all points in the trip
                "timestamp",
                "asc"
        );

        // Create a map of GPS points by location for quick lookup
        var gpsPointMap = new java.util.HashMap<String, org.github.tess1o.geopulse.gps.model.GpsPointEntity>();
        for (var gpsPoint : gpsPoints) {
            // Use rounded coordinates as key (to handle minor GPS variations)
            String key = String.format("%.6f,%.6f", gpsPoint.getLatitude(), gpsPoint.getLongitude());
            gpsPointMap.put(key, gpsPoint);
        }

        // Interpolate timestamps along the path
        long tripDurationSeconds = trip.getTripDuration();
        java.time.Instant startTime = trip.getTimestamp();

        for (int i = 0; i < coordinates.length; i++) {
            GpxTrackPoint trackPoint = new GpxTrackPoint();
            trackPoint.setLat(coordinates[i].getY());
            trackPoint.setLon(coordinates[i].getX());

            // Try to get elevation from the original GPS point
            String key = String.format("%.6f,%.6f", coordinates[i].getY(), coordinates[i].getX());
            var matchingGpsPoint = gpsPointMap.get(key);
            if (matchingGpsPoint != null && matchingGpsPoint.getAltitude() != null) {
                trackPoint.setElevation(matchingGpsPoint.getAltitude());
                // Use actual GPS speed instead of average if available
                if (matchingGpsPoint.getVelocity() != null) {
                    trackPoint.setSpeed(matchingGpsPoint.getVelocity() / 3.6); // Convert km/h to m/s
                }
            } else if (!Double.isNaN(coordinates[i].getZ())) {
                // Fallback to 3D coordinate if available
                trackPoint.setElevation(coordinates[i].getZ());
            }

            // Interpolate timestamp
            double progress = coordinates.length > 1 ? (double) i / (coordinates.length - 1) : 0;
            long secondsOffset = (long) (tripDurationSeconds * progress);
            trackPoint.setTime(startTime.plusSeconds(secondsOffset));

            // Add average speed if no GPS point matched and we have trip average
            if (matchingGpsPoint == null && trip.getAvgGpsSpeed() != null) {
                trackPoint.setSpeed(trip.getAvgGpsSpeed());
            }

            trackPoints.add(trackPoint);
        }

        GpxTrackSegment segment = new GpxTrackSegment();
        segment.setTrackPoints(trackPoints);

        GpxTrack track = new GpxTrack();

        // Format track name with time and movement type
        String timeStr = startTime.toString().substring(11, 16); // HH:mm
        String movementType = trip.getMovementType() != null ? trip.getMovementType() : "Unknown";
        String distanceKm = String.format("%.1f", trip.getDistanceMeters() / 1000.0);

        track.setName(String.format("Trip: %s (%s km, %s)", timeStr, distanceKm, movementType));
        track.setDescription(String.format("Duration: %d min, Distance: %s km, Movement: %s",
                tripDurationSeconds / 60, distanceKm, movementType));
        track.setTrackSegments(java.util.List.of(segment));

        log.debug("Built GPX track from trip with {} points, {} had elevation data",
                trackPoints.size(),
                trackPoints.stream().filter(p -> p.getElevation() != null).count());

        return track;
    }

    private java.util.List<GpxWaypoint> buildTimelineStayWaypoints(ExportJob job) {
        var stays = timelineStayRepository.findByUserIdAndTimeRangeWithExpansion(
                job.getUserId(),
                job.getDateRange().getStartDate(),
                job.getDateRange().getEndDate()
        );

        var waypoints = new java.util.ArrayList<GpxWaypoint>();

        for (TimelineStayEntity stay : stays) {
            GpxWaypoint waypoint = new GpxWaypoint();
            waypoint.setLat(stay.getLocation().getY());
            waypoint.setLon(stay.getLocation().getX());
            waypoint.setTime(stay.getTimestamp());
            waypoint.setName(stay.getLocationName() != null ? stay.getLocationName() : "Unknown Location");

            // Format duration nicely
            long hours = stay.getStayDuration() / 3600;
            long minutes = (stay.getStayDuration() % 3600) / 60;
            String durationStr = hours > 0 ?
                    String.format("%dh %dm", hours, minutes) :
                    String.format("%dm", minutes);

            waypoint.setDescription(String.format("Duration: %s", durationStr));
            waypoint.setSymbol("Flag");

            waypoints.add(waypoint);
        }

        log.debug("Built {} timeline stay waypoints", waypoints.size());
        return waypoints;
    }

    private GpxFile buildGpxFileForSingleTrip(TimelineTripEntity trip) {
        GpxFile gpxFile = new GpxFile();
        gpxFile.setVersion("1.1");
        gpxFile.setCreator("GeoPulse");

        GpxMetadata metadata = new GpxMetadata();
        metadata.setName("GeoPulse Trip Export");
        metadata.setTime(java.time.Instant.now());
        gpxFile.setMetadata(metadata);

        GpxTrack track = buildGpxTrackFromTrip(trip);
        if (track != null) {
            gpxFile.setTracks(java.util.List.of(track));
        }

        // Add start and end waypoints
        var waypoints = new java.util.ArrayList<GpxWaypoint>();

        GpxWaypoint startWaypoint = new GpxWaypoint();
        startWaypoint.setLat(trip.getStartPoint().getY());
        startWaypoint.setLon(trip.getStartPoint().getX());
        startWaypoint.setTime(trip.getTimestamp());
        startWaypoint.setName("Trip Start");
        startWaypoint.setSymbol("Flag, Green");
        waypoints.add(startWaypoint);

        GpxWaypoint endWaypoint = new GpxWaypoint();
        endWaypoint.setLat(trip.getEndPoint().getY());
        endWaypoint.setLon(trip.getEndPoint().getX());
        endWaypoint.setTime(trip.getTimestamp().plusSeconds(trip.getTripDuration()));
        endWaypoint.setName("Trip End");
        endWaypoint.setSymbol("Flag, Red");
        waypoints.add(endWaypoint);

        gpxFile.setWaypoints(waypoints);

        return gpxFile;
    }

    public byte[] generateSingleTripGpx(java.util.UUID userId, Long tripId) throws IOException {
        log.debug("Generating GPX export for single trip {}", tripId);

        // Fetch the trip
        var trip = timelineTripRepository.findById(tripId);
        if (trip == null || !trip.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Trip not found or access denied");
        }

        GpxFile gpxFile = buildGpxFileForSingleTrip(trip);
        String xml = xmlMapper.writeValueAsString(gpxFile);

        log.debug("Generated single trip GPX export with {} bytes", xml.getBytes().length);
        return xml.getBytes();
    }

    public byte[] generateSingleStayGpx(java.util.UUID userId, Long stayId) throws IOException {
        log.debug("Generating GPX export for single stay {}", stayId);

        // Fetch the stay
        var stay = timelineStayRepository.findById(stayId);
        if (stay == null || !stay.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Stay not found or access denied");
        }

        GpxFile gpxFile = buildGpxFileForSingleStay(stay);
        String xml = xmlMapper.writeValueAsString(gpxFile);

        log.debug("Generated single stay GPX export with {} bytes", xml.getBytes().length);
        return xml.getBytes();
    }

    private GpxFile buildGpxFileForSingleStay(TimelineStayEntity stay) {
        // Build GPX file with single waypoint
        GpxFile gpxFile = new GpxFile();
        gpxFile.setVersion("1.1");
        gpxFile.setCreator("GeoPulse");

        GpxMetadata metadata = new GpxMetadata();
        metadata.setName("GeoPulse Stay Export");
        metadata.setTime(java.time.Instant.now());
        gpxFile.setMetadata(metadata);

        // Create waypoint for the stay
        GpxWaypoint waypoint = new GpxWaypoint();
        waypoint.setLat(stay.getLocation().getY());
        waypoint.setLon(stay.getLocation().getX());
        waypoint.setTime(stay.getTimestamp());
        waypoint.setName(stay.getLocationName() != null ? stay.getLocationName() : "Unknown Location");

        // Format duration
        long hours = stay.getStayDuration() / 3600;
        long minutes = (stay.getStayDuration() % 3600) / 60;
        String durationStr = hours > 0 ?
                String.format("%dh %dm", hours, minutes) :
                String.format("%dm", minutes);

        waypoint.setDescription(String.format("Duration: %s", durationStr));
        waypoint.setSymbol("Flag");

        gpxFile.setWaypoints(java.util.List.of(waypoint));
        gpxFile.setTracks(java.util.List.of()); // No tracks for a stay

        return gpxFile;
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
                    pageSize,
                    "timestamp",
                    "asc"
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

        // Export data gaps
        var dataGaps = timelineDataGapRepository.findByUserIdAndTimeRange(
                job.getUserId(),
                job.getDateRange().getStartDate(),
                job.getDateRange().getEndDate()
        );

        TimelineDataDto timelineData = exportDataMapper.toTimelineDataDto(stays, trips, dataGaps, job);
        addJsonFileToZip(zos, ExportImportConstants.FileNames.TIMELINE_DATA, timelineData);

        log.debug("Exported {} stays, {} trips and {} data gaps", stays.size(), trips.size(), dataGaps.size());
    }

    private void addDataGapsData(ZipOutputStream zos, ExportJob job) throws IOException {
        log.debug("Exporting data gaps for user {}", job.getUserId());

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

        addJsonFileToZip(zos, ExportImportConstants.FileNames.DATA_GAPS, dataGapsData);

        log.debug("Exported {} data gaps", dataGaps.size());
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
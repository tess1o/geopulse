package org.github.tess1o.geopulse.export.service;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.gps.integrations.gpx.model.*;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.locationtech.jts.geom.Coordinate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service responsible for generating GPX format exports.
 * Handles single file GPX, per-trip/stay GPX zips, and individual trip/stay exports.
 */
@ApplicationScoped
@Slf4j
public class GpxExportService {

    private final XmlMapper xmlMapper = XmlMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Inject
    ExportDataCollectorService dataCollectorService;

    /**
     * Generates a GPX export, either as a single file or as a ZIP with per-trip files.
     *
     * @param job         the export job
     * @param zipPerTrip  if true, creates a ZIP with separate GPX files per trip/stay
     * @return the GPX file content or ZIP archive as bytes
     * @throws IOException if an I/O error occurs
     */
    public byte[] generateGpxExport(ExportJob job, boolean zipPerTrip) throws IOException {
        log.debug("Generating GPX export for user {}, zipPerTrip={}", job.getUserId(), zipPerTrip);

        if (zipPerTrip) {
            return generateGpxExportAsZip(job);
        } else {
            return generateSingleGpxFile(job);
        }
    }

    /**
     * Generates a single GPX file containing all data.
     *
     * @param job the export job
     * @return the GPX file as bytes
     * @throws IOException if an I/O error occurs
     */
    private byte[] generateSingleGpxFile(ExportJob job) throws IOException {
        GpxFile gpxFile = buildGpxFile(job);
        String xml = xmlMapper.writeValueAsString(gpxFile);
        log.debug("Generated single GPX export with {} bytes", xml.getBytes().length);
        return xml.getBytes();
    }

    /**
     * Generates a ZIP archive with separate GPX files for each trip and stay.
     *
     * @param job the export job
     * @return the ZIP archive as bytes
     * @throws IOException if an I/O error occurs
     */
    private byte[] generateGpxExportAsZip(ExportJob job) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            // Get timeline trips
            var trips = dataCollectorService.collectTimelineTripsWithExpansion(job);

            // Get timeline stays
            var stays = dataCollectorService.collectTimelineStaysWithExpansion(job);

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

    /**
     * Generates a GPX file for a single trip.
     *
     * @param userId the user ID
     * @param tripId the trip ID
     * @return the GPX file as bytes
     * @throws IOException if an I/O error occurs
     */
    public byte[] generateSingleTripGpx(UUID userId, Long tripId) throws IOException {
        log.debug("Generating GPX export for single trip {}", tripId);

        var trip = dataCollectorService.fetchTripById(userId, tripId);
        GpxFile gpxFile = buildGpxFileForSingleTrip(trip);
        String xml = xmlMapper.writeValueAsString(gpxFile);

        log.debug("Generated single trip GPX export with {} bytes", xml.getBytes().length);
        return xml.getBytes();
    }

    /**
     * Generates a GPX file for a single stay.
     *
     * @param userId the user ID
     * @param stayId the stay ID
     * @return the GPX file as bytes
     * @throws IOException if an I/O error occurs
     */
    public byte[] generateSingleStayGpx(UUID userId, Long stayId) throws IOException {
        log.debug("Generating GPX export for single stay {}", stayId);

        var stay = dataCollectorService.fetchStayById(userId, stayId);
        GpxFile gpxFile = buildGpxFileForSingleStay(stay);
        String xml = xmlMapper.writeValueAsString(gpxFile);

        log.debug("Generated single stay GPX export with {} bytes", xml.getBytes().length);
        return xml.getBytes();
    }

    /**
     * Builds a complete GPX file with all data from the export job.
     *
     * @param job the export job
     * @return the GPX file model
     */
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
        var tracks = new ArrayList<GpxTrack>();
        var waypoints = new ArrayList<GpxWaypoint>();

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

    /**
     * Builds a GPX track from raw GPS points.
     *
     * @param job the export job
     * @return the GPX track, or null if no GPS points found
     */
    private GpxTrack buildRawGpsTrack(ExportJob job) {
        var allPoints = dataCollectorService.collectGpsPoints(job);
        if (allPoints.isEmpty()) {
            return null;
        }

        var allTrackPoints = new ArrayList<GpxTrackPoint>();

        for (var gpsPoint : allPoints) {
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

        GpxTrackSegment segment = new GpxTrackSegment();
        segment.setTrackPoints(allTrackPoints);

        GpxTrack track = new GpxTrack();
        track.setName("Raw GPS Data");
        track.setDescription("All GPS points for the selected date range");
        track.setTrackSegments(List.of(segment));

        log.debug("Built raw GPS track with {} points", allTrackPoints.size());
        return track;
    }

    /**
     * Builds GPX tracks from timeline trips.
     *
     * @param job the export job
     * @return list of GPX tracks
     */
    private List<GpxTrack> buildTimelineTripTracks(ExportJob job) {
        var trips = dataCollectorService.collectTimelineTripsWithExpansion(job);
        var tracks = new ArrayList<GpxTrack>();

        for (TimelineTripEntity trip : trips) {
            GpxTrack track = buildGpxTrackFromTrip(trip);
            if (track != null) {
                tracks.add(track);
            }
        }

        log.debug("Built {} timeline trip tracks", tracks.size());
        return tracks;
    }

    /**
     * Builds a GPX track from a single timeline trip.
     *
     * @param trip the timeline trip entity
     * @return the GPX track, or null if no path data
     */
    private GpxTrack buildGpxTrackFromTrip(TimelineTripEntity trip) {
        if (trip.getPath() == null || trip.getPath().getNumPoints() == 0) {
            return null;
        }

        var trackPoints = new ArrayList<GpxTrackPoint>();
        Coordinate[] coordinates = trip.getPath().getCoordinates();

        // Fetch original GPS points for this trip to get altitude data
        java.time.Instant endTime = trip.getTimestamp().plusSeconds(trip.getTripDuration());
        var gpsPoints = dataCollectorService.collectGpsPointsInTimeRange(
                trip.getUser().getId(),
                trip.getTimestamp(),
                endTime
        );

        // Create a map of GPS points by location for quick lookup
        var gpsPointMap = new HashMap<String, org.github.tess1o.geopulse.gps.model.GpsPointEntity>();
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
        track.setTrackSegments(List.of(segment));

        log.debug("Built GPX track from trip with {} points, {} had elevation data",
                trackPoints.size(),
                trackPoints.stream().filter(p -> p.getElevation() != null).count());

        return track;
    }

    /**
     * Builds GPX waypoints from timeline stays.
     *
     * @param job the export job
     * @return list of GPX waypoints
     */
    private List<GpxWaypoint> buildTimelineStayWaypoints(ExportJob job) {
        var stays = dataCollectorService.collectTimelineStaysWithExpansion(job);
        var waypoints = new ArrayList<GpxWaypoint>();

        for (TimelineStayEntity stay : stays) {
            GpxWaypoint waypoint = createWaypointFromStay(stay);
            waypoints.add(waypoint);
        }

        log.debug("Built {} timeline stay waypoints", waypoints.size());
        return waypoints;
    }

    /**
     * Creates a GPX waypoint from a timeline stay.
     *
     * @param stay the timeline stay entity
     * @return the GPX waypoint
     */
    private GpxWaypoint createWaypointFromStay(TimelineStayEntity stay) {
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

        return waypoint;
    }

    /**
     * Builds a GPX file for a single trip with start/end waypoints.
     *
     * @param trip the timeline trip entity
     * @return the GPX file model
     */
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
            gpxFile.setTracks(List.of(track));
        }

        // Add start and end waypoints
        var waypoints = new ArrayList<GpxWaypoint>();

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

    /**
     * Builds a GPX file for a single stay.
     *
     * @param stay the timeline stay entity
     * @return the GPX file model
     */
    private GpxFile buildGpxFileForSingleStay(TimelineStayEntity stay) {
        GpxFile gpxFile = new GpxFile();
        gpxFile.setVersion("1.1");
        gpxFile.setCreator("GeoPulse");

        GpxMetadata metadata = new GpxMetadata();
        metadata.setName("GeoPulse Stay Export");
        metadata.setTime(java.time.Instant.now());
        gpxFile.setMetadata(metadata);

        // Create waypoint for the stay
        GpxWaypoint waypoint = createWaypointFromStay(stay);

        gpxFile.setWaypoints(List.of(waypoint));
        gpxFile.setTracks(List.of()); // No tracks for a stay

        return gpxFile;
    }
}

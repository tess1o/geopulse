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

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
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
     * Converts a list of GPS points to a GPX track.
     * This is the core conversion logic used by all export methods.
     *
     * @param gpsPoints List of GPS point entities
     * @param trackName Name for the GPX track
     * @param trackDescription Description for the GPX track
     * @return GPX track with all points, or null if no points
     */
    private GpxTrack convertGpsPointsToGpxTrack(List<org.github.tess1o.geopulse.gps.model.GpsPointEntity> gpsPoints,
                                                String trackName, String trackDescription) {
        if (gpsPoints == null || gpsPoints.isEmpty()) {
            return null;
        }

        var trackPoints = new ArrayList<GpxTrackPoint>();
        for (var gpsPoint : gpsPoints) {
            GpxTrackPoint trackPoint = new GpxTrackPoint();
            trackPoint.setLat(gpsPoint.getCoordinates().getY());
            trackPoint.setLon(gpsPoint.getCoordinates().getX());
            trackPoint.setElevation(gpsPoint.getAltitude());
            trackPoint.setTime(gpsPoint.getTimestamp());

            // Convert velocity from km/h to m/s
            if (gpsPoint.getVelocity() != null) {
                trackPoint.setSpeed(gpsPoint.getVelocity() / 3.6);
            }

            trackPoints.add(trackPoint);
        }

        GpxTrackSegment segment = new GpxTrackSegment();
        segment.setTrackPoints(trackPoints);

        GpxTrack track = new GpxTrack();
        track.setName(trackName);
        track.setDescription(trackDescription);
        track.setTrackSegments(List.of(segment));

        return track;
    }

    /**
     * Generates a GPX export, either as a single file or as a ZIP with per-trip files.
     *
     * @param job         the export job
     * @param zipPerTrip  if true, creates a ZIP with separate GPX files per trip/stay
     * @param zipGroupBy  grouping mode for ZIP: "individual" (per trip/stay) or "daily" (per day)
     * @return the GPX file content or ZIP archive as bytes
     * @throws IOException if an I/O error occurs
     */
    public byte[] generateGpxExport(ExportJob job, boolean zipPerTrip, String zipGroupBy) throws IOException {
        log.debug("Generating GPX export for user {}, zipPerTrip={}, zipGroupBy={}",
                job.getUserId(), zipPerTrip, zipGroupBy);

        if (zipPerTrip) {
            return generateGpxExportAsZip(job, zipGroupBy);
        } else {
            return generateSingleGpxFile(job);
        }
    }

    /**
     * Generates a single GPX file containing all data using STREAMING export.
     * This handles millions of GPS points without loading them all into memory.
     *
     * @param job the export job
     * @return the GPX file as bytes
     * @throws IOException if an I/O error occurs
     */
    private byte[] generateSingleGpxFile(ExportJob job) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            streamGpxFileToOutput(job, baos);
            byte[] result = baos.toByteArray();
            log.info("Generated streaming GPX export with {} bytes", result.length);
            return result;
        } catch (XMLStreamException e) {
            throw new IOException("Failed to generate GPX export: " + e.getMessage(), e);
        }
    }

    /**
     * Streams a complete GPX file to the output stream without loading all data into memory.
     * Uses XMLStreamWriter for memory-efficient export of millions of GPS points.
     *
     * @param job the export job
     * @param outputStream the output stream to write to
     * @throws XMLStreamException if XML writing fails
     * @throws IOException if data collection fails
     */
    private void streamGpxFileToOutput(ExportJob job, ByteArrayOutputStream outputStream)
            throws XMLStreamException, IOException {

        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        XMLStreamWriter xml = factory.createXMLStreamWriter(writer);

        try {
            // Start GPX document
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("gpx");
            xml.writeAttribute("version", "1.1");
            xml.writeAttribute("creator", "GeoPulse");
            xml.writeAttribute("xmlns", "http://www.topografix.com/GPX/1/1");

            // Write metadata
            xml.writeStartElement("metadata");
            xml.writeStartElement("name");
            xml.writeCharacters("GeoPulse Export");
            xml.writeEndElement(); // name
            xml.writeStartElement("desc");
            xml.writeCharacters("GPS tracks and waypoints exported from GeoPulse");
            xml.writeEndElement(); // desc
            xml.writeStartElement("time");
            xml.writeCharacters(java.time.Instant.now().toString());
            xml.writeEndElement(); // time
            xml.writeEndElement(); // metadata

            // Stream raw GPS data as first track (MEMORY EFFICIENT!)
            streamRawGpsTrack(job, xml);

            // Add timeline trips as tracks (these are already small/simplified)
            streamTimelineTripTracks(job, xml);

            // Add timeline stays as waypoints
            streamTimelineStayWaypoints(job, xml);

            xml.writeEndElement(); // gpx
            xml.writeEndDocument();
            xml.flush();

        } finally {
            xml.close();
        }
    }

    /**
     * Streams raw GPS points as a GPX track without loading all into memory.
     * Processes GPS points in batches to maintain constant memory usage.
     */
    private void streamRawGpsTrack(ExportJob job, XMLStreamWriter xml)
            throws XMLStreamException {

        final int BATCH_SIZE = 1000; // Process 1000 points at a time
        int page = 0;
        long totalPoints = 0;
        boolean trackStarted = false;

        log.info("Starting streaming export of raw GPS data");

        while (true) {
            // Fetch batch of GPS points
            var batch = dataCollectorService.getGpsPointRepository().findByUserAndDateRange(
                    job.getUserId(),
                    job.getDateRange().getStartDate(),
                    job.getDateRange().getEndDate(),
                    page,
                    BATCH_SIZE,
                    "timestamp",
                    "asc"
            );

            if (batch.isEmpty()) {
                break;
            }

            // Start track on first batch
            if (!trackStarted) {
                xml.writeStartElement("trk");
                xml.writeStartElement("name");
                xml.writeCharacters("Raw GPS Data");
                xml.writeEndElement(); // name
                xml.writeStartElement("desc");
                xml.writeCharacters("All GPS points for the selected date range");
                xml.writeEndElement(); // desc
                xml.writeStartElement("trkseg");
                trackStarted = true;
            }

            // Write each GPS point in batch
            for (var gpsPoint : batch) {
                xml.writeStartElement("trkpt");
                xml.writeAttribute("lat", String.valueOf(gpsPoint.getCoordinates().getY()));
                xml.writeAttribute("lon", String.valueOf(gpsPoint.getCoordinates().getX()));

                if (gpsPoint.getAltitude() != null) {
                    xml.writeStartElement("ele");
                    xml.writeCharacters(String.valueOf(gpsPoint.getAltitude()));
                    xml.writeEndElement(); // ele
                }

                xml.writeStartElement("time");
                xml.writeCharacters(gpsPoint.getTimestamp().toString());
                xml.writeEndElement(); // time

                if (gpsPoint.getVelocity() != null) {
                    xml.writeStartElement("speed");
                    xml.writeCharacters(String.valueOf(gpsPoint.getVelocity() / 3.6)); // km/h to m/s
                    xml.writeEndElement(); // speed
                }

                xml.writeEndElement(); // trkpt
                totalPoints++;
            }

            page++;

            if (page % 10 == 0) {
                log.debug("Streamed {} GPS points so far...", totalPoints);
            }
        }

        // Close track if we wrote any points
        if (trackStarted) {
            xml.writeEndElement(); // trkseg
            xml.writeEndElement(); // trk
            log.info("Completed streaming {} raw GPS points", totalPoints);
        }
    }

    /**
     * Streams timeline trips as GPX tracks with ALL raw GPS points (not simplified paths).
     * CRITICAL FIX: Fetches raw GPS data for each trip to prevent data loss.
     */
    private void streamTimelineTripTracks(ExportJob job, XMLStreamWriter xml)
            throws XMLStreamException {

        var trips = dataCollectorService.collectTimelineTripsWithExpansion(job);

        for (TimelineTripEntity trip : trips) {
            // Fetch ALL raw GPS points for this trip's time range
            java.time.Instant tripStart = trip.getTimestamp();
            java.time.Instant tripEnd = tripStart.plusSeconds(trip.getTripDuration());

            var tripGpsPoints = dataCollectorService.collectGpsPointsInTimeRange(
                    trip.getUser().getId(),
                    tripStart,
                    tripEnd
            );

            if (tripGpsPoints.isEmpty()) {
                continue; // Skip trips with no GPS data
            }

            xml.writeStartElement("trk");

            String timeStr = tripStart.toString().substring(11, 16);
            String movementType = trip.getMovementType() != null ? trip.getMovementType() : "Unknown";
            String distanceKm = String.format("%.1f", trip.getDistanceMeters() / 1000.0);

            xml.writeStartElement("name");
            xml.writeCharacters(String.format("Trip: %s (%s km, %s)", timeStr, distanceKm, movementType));
            xml.writeEndElement(); // name

            xml.writeStartElement("desc");
            xml.writeCharacters(String.format("%d GPS points, Duration: %d min, Distance: %s km",
                    tripGpsPoints.size(), trip.getTripDuration() / 60, distanceKm));
            xml.writeEndElement(); // desc

            xml.writeStartElement("trkseg");

            // Write ALL raw GPS points for this trip
            for (var gpsPoint : tripGpsPoints) {
                xml.writeStartElement("trkpt");
                xml.writeAttribute("lat", String.valueOf(gpsPoint.getCoordinates().getY()));
                xml.writeAttribute("lon", String.valueOf(gpsPoint.getCoordinates().getX()));

                if (gpsPoint.getAltitude() != null) {
                    xml.writeStartElement("ele");
                    xml.writeCharacters(String.valueOf(gpsPoint.getAltitude()));
                    xml.writeEndElement(); // ele
                }

                xml.writeStartElement("time");
                xml.writeCharacters(gpsPoint.getTimestamp().toString());
                xml.writeEndElement(); // time

                if (gpsPoint.getVelocity() != null) {
                    xml.writeStartElement("speed");
                    xml.writeCharacters(String.valueOf(gpsPoint.getVelocity() / 3.6)); // km/h to m/s
                    xml.writeEndElement(); // speed
                }

                xml.writeEndElement(); // trkpt
            }

            xml.writeEndElement(); // trkseg
            xml.writeEndElement(); // trk

            log.debug("Streamed trip track with {} raw GPS points (was {} simplified path points)",
                    tripGpsPoints.size(), trip.getPath() != null ? trip.getPath().getNumPoints() : 0);
        }

        log.info("Streamed {} timeline trip tracks with raw GPS data", trips.size());
    }

    /**
     * Streams timeline stays as GPX waypoints.
     */
    private void streamTimelineStayWaypoints(ExportJob job, XMLStreamWriter xml)
            throws XMLStreamException {

        var stays = dataCollectorService.collectTimelineStaysWithExpansion(job);

        for (TimelineStayEntity stay : stays) {
            xml.writeStartElement("wpt");
            xml.writeAttribute("lat", String.valueOf(stay.getLocation().getY()));
            xml.writeAttribute("lon", String.valueOf(stay.getLocation().getX()));

            xml.writeStartElement("time");
            xml.writeCharacters(stay.getTimestamp().toString());
            xml.writeEndElement(); // time

            xml.writeStartElement("name");
            xml.writeCharacters(stay.getLocationName() != null ? stay.getLocationName() : "Unknown Location");
            xml.writeEndElement(); // name

            long hours = stay.getStayDuration() / 3600;
            long minutes = (stay.getStayDuration() % 3600) / 60;
            String durationStr = hours > 0 ?
                    String.format("%dh %dm", hours, minutes) :
                    String.format("%dm", minutes);

            xml.writeStartElement("desc");
            xml.writeCharacters(String.format("Duration: %s", durationStr));
            xml.writeEndElement(); // desc

            xml.writeStartElement("sym");
            xml.writeCharacters("Flag");
            xml.writeEndElement(); // sym

            xml.writeEndElement(); // wpt
        }

        log.debug("Streamed {} timeline stay waypoints", stays.size());
    }

    /**
     * Generates a ZIP archive with separate GPX files for each trip and stay.
     *
     * @param job        the export job
     * @param zipGroupBy grouping mode: "individual" (per trip/stay) or "daily" (per day)
     * @return the ZIP archive as bytes
     * @throws IOException if an I/O error occurs
     */
    private byte[] generateGpxExportAsZip(ExportJob job, String zipGroupBy) throws IOException {
        if ("daily".equalsIgnoreCase(zipGroupBy)) {
            return generateGpxExportAsZipGroupedByDay(job);
        } else {
            // Default to individual grouping (one file per trip/stay)
            return generateGpxExportAsZipIndividual(job);
        }
    }

    /**
     * Generates a ZIP archive with separate GPX files for each trip and stay (individual mode).
     *
     * @param job the export job
     * @return the ZIP archive as bytes
     * @throws IOException if an I/O error occurs
     */
    private byte[] generateGpxExportAsZipIndividual(ExportJob job) throws IOException {
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
     * Generates a ZIP archive with GPX files grouped by day.
     * Each day gets one GPX file containing all trips (as tracks) and stays (as waypoints) for that day.
     *
     * @param job the export job
     * @return the ZIP archive as bytes
     * @throws IOException if an I/O error occurs
     */
    private byte[] generateGpxExportAsZipGroupedByDay(ExportJob job) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            // Get timeline trips and stays
            var trips = dataCollectorService.collectTimelineTripsWithExpansion(job);
            var stays = dataCollectorService.collectTimelineStaysWithExpansion(job);

            // Group trips and stays by day
            var tripsByDay = new HashMap<java.time.LocalDate, List<TimelineTripEntity>>();
            var staysByDay = new HashMap<java.time.LocalDate, List<TimelineStayEntity>>();

            for (TimelineTripEntity trip : trips) {
                java.time.LocalDate day = java.time.LocalDateTime.ofInstant(
                        trip.getTimestamp(), java.time.ZoneOffset.UTC).toLocalDate();
                tripsByDay.computeIfAbsent(day, k -> new ArrayList<>()).add(trip);
            }

            for (TimelineStayEntity stay : stays) {
                java.time.LocalDate day = java.time.LocalDateTime.ofInstant(
                        stay.getTimestamp(), java.time.ZoneOffset.UTC).toLocalDate();
                staysByDay.computeIfAbsent(day, k -> new ArrayList<>()).add(stay);
            }

            // Get all unique days and sort them
            var allDays = new java.util.TreeSet<java.time.LocalDate>();
            allDays.addAll(tripsByDay.keySet());
            allDays.addAll(staysByDay.keySet());

            log.debug("Grouping {} trips and {} stays into {} daily GPX files",
                    trips.size(), stays.size(), allDays.size());

            // Create one GPX file per day
            for (java.time.LocalDate day : allDays) {
                GpxFile gpxFile = buildGpxFileForDay(day,
                        tripsByDay.getOrDefault(day, List.of()),
                        staysByDay.getOrDefault(day, List.of()));

                String filename = String.format("day_%s.gpx", day.toString());

                ZipEntry entry = new ZipEntry(filename);
                zos.putNextEntry(entry);
                String xml = xmlMapper.writeValueAsString(gpxFile);
                zos.write(xml.getBytes());
                zos.closeEntry();

                log.debug("Added {} with {} trips and {} stays", filename,
                        tripsByDay.getOrDefault(day, List.of()).size(),
                        staysByDay.getOrDefault(day, List.of()).size());
            }

            zos.finish();
            log.debug("Generated GPX zip with {} daily files, {} bytes total",
                    allDays.size(), baos.toByteArray().length);
            return baos.toByteArray();
        }
    }

    /**
     * Builds a GPX file for a single day with ALL raw GPS points organized by trips/stays.
     * IMPORTANT: Each trip gets its own track with ALL GPS points (not simplified path)!
     *
     * @param day    the date
     * @param trips  the trips for that day
     * @param stays  the stays for that day
     * @return the GPX file model
     */
    private GpxFile buildGpxFileForDay(java.time.LocalDate day, List<TimelineTripEntity> trips,
                                       List<TimelineStayEntity> stays) {
        GpxFile gpxFile = new GpxFile();
        gpxFile.setVersion("1.1");
        gpxFile.setCreator("GeoPulse");

        // Set metadata
        GpxMetadata metadata = new GpxMetadata();
        metadata.setName(String.format("GeoPulse Export - %s", day.toString()));
        metadata.setDescription(String.format("GPS tracks and waypoints for %s", day.toString()));
        metadata.setTime(java.time.Instant.now());
        gpxFile.setMetadata(metadata);

        var tracks = new ArrayList<GpxTrack>();
        var waypoints = new ArrayList<GpxWaypoint>();

        // CRITICAL FIX: Export each trip as a separate track with ALL raw GPS points
        for (TimelineTripEntity trip : trips) {
            // Fetch ALL raw GPS points for this trip's time range
            java.time.Instant tripStart = trip.getTimestamp();
            java.time.Instant tripEnd = tripStart.plusSeconds(trip.getTripDuration());

            var tripGpsPoints = dataCollectorService.collectGpsPointsInTimeRange(
                    trip.getUser().getId(),
                    tripStart,
                    tripEnd
            );

            if (!tripGpsPoints.isEmpty()) {
                String timeStr = tripStart.toString().substring(11, 16);
                String movementType = trip.getMovementType() != null ? trip.getMovementType() : "Unknown";
                String distanceKm = String.format("%.1f", trip.getDistanceMeters() / 1000.0);

                GpxTrack track = convertGpsPointsToGpxTrack(
                        tripGpsPoints,
                        String.format("Trip: %s (%s km, %s)", timeStr, distanceKm, movementType),
                        String.format("%d GPS points, Duration: %d min, Distance: %s km",
                                tripGpsPoints.size(), trip.getTripDuration() / 60, distanceKm)
                );

                if (track != null) {
                    tracks.add(track);
                    log.debug("Exported {} raw GPS points for trip at {}", tripGpsPoints.size(), timeStr);
                }
            }
        }

        // Export stays as waypoints with optional GPS tracks
        for (TimelineStayEntity stay : stays) {
            // Create waypoint marker
            GpxWaypoint waypoint = createWaypointFromStay(stay);
            waypoints.add(waypoint);

            // Optionally add GPS track for stay period (if there are GPS points during stay)
            java.time.Instant stayStart = stay.getTimestamp();
            java.time.Instant stayEnd = stayStart.plusSeconds(stay.getStayDuration());

            var stayGpsPoints = dataCollectorService.collectGpsPointsInTimeRange(
                    stay.getUser().getId(),
                    stayStart,
                    stayEnd
            );

            if (!stayGpsPoints.isEmpty()) {
                long hours = stay.getStayDuration() / 3600;
                long minutes = (stay.getStayDuration() % 3600) / 60;
                String durationStr = hours > 0 ? String.format("%dh %dm", hours, minutes) : String.format("%dm", minutes);
                String locationName = stay.getLocationName() != null ? stay.getLocationName() : "Unknown";

                GpxTrack stayTrack = convertGpsPointsToGpxTrack(
                        stayGpsPoints,
                        String.format("Stay: %s (%s)", locationName, durationStr),
                        String.format("%d GPS points during stay", stayGpsPoints.size())
                );

                if (stayTrack != null) {
                    tracks.add(stayTrack);
                    log.debug("Exported {} raw GPS points for stay at {}", stayGpsPoints.size(), locationName);
                }
            }
        }

        gpxFile.setTracks(tracks);
        gpxFile.setWaypoints(waypoints);

        log.info("Exported day {} with {} trip tracks, {} stay tracks, {} stay waypoints",
                day, trips.size(), stays.stream().filter(s -> {
                    var pts = dataCollectorService.collectGpsPointsInTimeRange(
                        s.getUser().getId(), s.getTimestamp(),
                        s.getTimestamp().plusSeconds(s.getStayDuration()));
                    return !pts.isEmpty();
                }).count(), stays.size());

        return gpxFile;
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

    // Old non-streaming methods removed - replaced by streamGpxFileToOutput() and streaming helpers

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
     * Builds a GPX file for a single trip with ALL raw GPS points.
     * IMPORTANT: Exports ALL GPS points from trip time range, not simplified timeline path!
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

        // CRITICAL FIX: Export ALL raw GPS points for this trip, not simplified timeline path!
        java.time.Instant endTime = trip.getTimestamp().plusSeconds(trip.getTripDuration());
        var gpsPoints = dataCollectorService.collectGpsPointsInTimeRange(
                trip.getUser().getId(),
                trip.getTimestamp(),
                endTime
        );

        String timeStr = trip.getTimestamp().toString().substring(11, 16);
        String movementType = trip.getMovementType() != null ? trip.getMovementType() : "Unknown";
        String distanceKm = String.format("%.1f", trip.getDistanceMeters() / 1000.0);

        GpxTrack track = convertGpsPointsToGpxTrack(
                gpsPoints,
                String.format("Trip: %s (%s km, %s)", timeStr, distanceKm, movementType),
                String.format("%d GPS points, Duration: %d min, Distance: %s km",
                        gpsPoints.size(), trip.getTripDuration() / 60, distanceKm)
        );

        if (track != null) {
            gpxFile.setTracks(List.of(track));
            log.info("Exported {} raw GPS points for trip (was {} simplified path points)",
                    gpsPoints.size(), trip.getPath() != null ? trip.getPath().getNumPoints() : 0);
        } else {
            gpxFile.setTracks(List.of());
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
     * Builds a GPX file for a single stay with ALL raw GPS points from the stay period.
     * IMPORTANT: Exports ALL GPS points from stay time range to prevent data loss!
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

        // Create waypoint marker for the stay
        GpxWaypoint waypoint = createWaypointFromStay(stay);
        gpxFile.setWaypoints(List.of(waypoint));

        // CRITICAL FIX: Export ALL raw GPS points during the stay period
        java.time.Instant endTime = stay.getTimestamp().plusSeconds(stay.getStayDuration());
        var gpsPoints = dataCollectorService.collectGpsPointsInTimeRange(
                stay.getUser().getId(),
                stay.getTimestamp(),
                endTime
        );

        long hours = stay.getStayDuration() / 3600;
        long minutes = (stay.getStayDuration() % 3600) / 60;
        String durationStr = hours > 0 ? String.format("%dh %dm", hours, minutes) : String.format("%dm", minutes);

        GpxTrack track = convertGpsPointsToGpxTrack(
                gpsPoints,
                String.format("Stay: %s (%s)", stay.getLocationName() != null ? stay.getLocationName() : "Unknown", durationStr),
                String.format("%d GPS points during stay", gpsPoints.size())
        );

        if (track != null) {
            gpxFile.setTracks(List.of(track));
            log.info("Exported {} raw GPS points for stay (duration: {} seconds)", gpsPoints.size(), stay.getStayDuration());
        } else {
            gpxFile.setTracks(List.of());
        }

        return gpxFile;
    }
}

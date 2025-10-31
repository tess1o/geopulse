package org.github.tess1o.geopulse.importdata.service;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.gpx.model.GpxFile;
import org.github.tess1o.geopulse.gps.integrations.gpx.model.GpxTrackPoint;
import org.github.tess1o.geopulse.gps.integrations.gpx.model.GpxWaypoint;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for parsing GPX files and converting them to GPS entities.
 * Shared between single-file and ZIP import strategies.
 */
@ApplicationScoped
@Slf4j
public class GpxParserService {

    private final XmlMapper xmlMapper = XmlMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    /**
     * Parse GPX XML content and convert to GPS entities
     *
     * @param xmlContent GPX XML content as string
     * @param user User entity for the GPS points
     * @param job Import job for filtering and progress tracking
     * @return List of GPS point entities
     * @throws IOException if parsing fails
     */
    public List<GpsPointEntity> parseGpxXmlToGpsPoints(String xmlContent, UserEntity user, ImportJob job) throws IOException {
        GpxFile gpxFile = xmlMapper.readValue(xmlContent, GpxFile.class);
        return convertGpxToGpsPoints(gpxFile, user, job);
    }

    /**
     * Validate GPX XML and count valid GPS points
     *
     * @param xmlContent GPX XML content as string
     * @return ValidationResult with total and valid point counts
     * @throws IOException if parsing fails
     */
    public ValidationResult validateGpx(String xmlContent) throws IOException {
        GpxFile gpxFile = xmlMapper.readValue(xmlContent, GpxFile.class);

        List<GpxTrackPoint> trackPoints = gpxFile.getAllTrackPoints();
        List<GpxWaypoint> waypoints = gpxFile.getValidWaypoints();

        int totalValidPoints = trackPoints.size() + waypoints.size();

        // Extract timestamp range
        Instant firstTimestamp = null;
        Instant lastTimestamp = null;

        // Get timestamps from track points
        for (GpxTrackPoint tp : trackPoints) {
            if (tp.hasValidTime()) {
                Instant time = tp.getTime();
                if (firstTimestamp == null || time.isBefore(firstTimestamp)) {
                    firstTimestamp = time;
                }
                if (lastTimestamp == null || time.isAfter(lastTimestamp)) {
                    lastTimestamp = time;
                }
            }
        }

        // Get timestamps from waypoints
        for (GpxWaypoint wp : waypoints) {
            if (wp.getTime() != null) {
                Instant time = wp.getTime();
                if (firstTimestamp == null || time.isBefore(firstTimestamp)) {
                    firstTimestamp = time;
                }
                if (lastTimestamp == null || time.isAfter(lastTimestamp)) {
                    lastTimestamp = time;
                }
            }
        }

        log.debug("GPX validation: {} track points, {} waypoints ({} total valid)",
                trackPoints.size(), waypoints.size(), totalValidPoints);

        return new ValidationResult(
                trackPoints.size() + waypoints.size(),
                totalValidPoints,
                firstTimestamp,
                lastTimestamp
        );
    }

    private List<GpsPointEntity> convertGpxToGpsPoints(GpxFile gpxFile, UserEntity user, ImportJob job) {
        List<GpsPointEntity> gpsPoints = new ArrayList<>();

        // Process track points
        List<GpxTrackPoint> trackPoints = gpxFile.getAllTrackPoints();
        for (GpxTrackPoint trackPoint : trackPoints) {
            GpsPointEntity gpsPoint = convertTrackPointToGpsPoint(trackPoint, user, job);
            if (gpsPoint != null) {
                gpsPoints.add(gpsPoint);
            }
        }

        // Process waypoints (if they have timestamps)
        List<GpxWaypoint> waypoints = gpxFile.getValidWaypoints();
        for (GpxWaypoint waypoint : waypoints) {
            GpsPointEntity gpsPoint = convertWaypointToGpsPoint(waypoint, user, job);
            if (gpsPoint != null) {
                gpsPoints.add(gpsPoint);
            }
        }

        return gpsPoints;
    }

    private GpsPointEntity convertTrackPointToGpsPoint(GpxTrackPoint trackPoint, UserEntity user, ImportJob job) {
        // Skip points without valid coordinates or timestamp
        if (!trackPoint.hasValidCoordinates() || !trackPoint.hasValidTime()) {
            return null;
        }

        // Apply date range filter if specified in import options
        if (shouldSkipDueDateFilter(trackPoint.getTime(), job)) {
            return null;
        }

        try {
            GpsPointEntity gpsEntity = new GpsPointEntity();
            gpsEntity.setUser(user);
            gpsEntity.setDeviceId("gpx-import");
            gpsEntity.setCoordinates(GeoUtils.createPoint(trackPoint.getLon(), trackPoint.getLat()));
            gpsEntity.setTimestamp(trackPoint.getTime());
            gpsEntity.setSourceType(GpsSourceType.GPX);
            gpsEntity.setCreatedAt(Instant.now());

            // Set elevation if available
            if (trackPoint.getElevation() != null) {
                gpsEntity.setAltitude(trackPoint.getElevation());
            }

            // Set speed if available (convert from m/s to km/h)
            if (trackPoint.getSpeed() != null) {
                gpsEntity.setVelocity(trackPoint.getSpeed() * 3.6); // Convert m/s to km/h
            }

            return gpsEntity;

        } catch (Exception e) {
            log.warn("Failed to create GPS entity from track point: {}", e.getMessage());
            return null;
        }
    }

    private GpsPointEntity convertWaypointToGpsPoint(GpxWaypoint waypoint, UserEntity user, ImportJob job) {
        // Skip waypoints without valid coordinates
        if (!waypoint.hasValidCoordinates()) {
            return null;
        }

        // Skip waypoints without timestamps (we can't create meaningful GPS points without time)
        if (waypoint.getTime() == null) {
            return null;
        }

        // Apply date range filter if specified in import options
        if (shouldSkipDueDateFilter(waypoint.getTime(), job)) {
            return null;
        }

        try {
            GpsPointEntity gpsEntity = new GpsPointEntity();
            gpsEntity.setUser(user);
            gpsEntity.setDeviceId("gpx-waypoint-import");
            gpsEntity.setCoordinates(GeoUtils.createPoint(waypoint.getLon(), waypoint.getLat()));
            gpsEntity.setTimestamp(waypoint.getTime());
            gpsEntity.setSourceType(GpsSourceType.GPX);
            gpsEntity.setCreatedAt(Instant.now());

            // Set elevation if available
            if (waypoint.getElevation() != null) {
                gpsEntity.setAltitude(waypoint.getElevation());
            }

            // Waypoints are typically stationary, so velocity is 0
            gpsEntity.setVelocity(0.0);

            return gpsEntity;

        } catch (Exception e) {
            log.warn("Failed to create GPS entity from waypoint: {}", e.getMessage());
            return null;
        }
    }

    private boolean shouldSkipDueDateFilter(Instant timestamp, ImportJob job) {
        if (job.getOptions().getDateRangeFilter() == null || timestamp == null) {
            return false;
        }

        return timestamp.isBefore(job.getOptions().getDateRangeFilter().getStartDate()) ||
               timestamp.isAfter(job.getOptions().getDateRangeFilter().getEndDate());
    }

    /**
     * Validation result for GPX files
     */
    public static class ValidationResult {
        private final int totalRecordCount;
        private final int validRecordCount;
        private final Instant firstTimestamp;
        private final Instant lastTimestamp;

        public ValidationResult(int totalRecordCount, int validRecordCount,
                               Instant firstTimestamp, Instant lastTimestamp) {
            this.totalRecordCount = totalRecordCount;
            this.validRecordCount = validRecordCount;
            this.firstTimestamp = firstTimestamp;
            this.lastTimestamp = lastTimestamp;
        }

        public int getTotalRecordCount() {
            return totalRecordCount;
        }

        public int getValidRecordCount() {
            return validRecordCount;
        }

        public Instant getFirstTimestamp() {
            return firstTimestamp;
        }

        public Instant getLastTimestamp() {
            return lastTimestamp;
        }
    }
}

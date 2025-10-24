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
 * Import strategy for GPX (GPS Exchange Format) files
 */
@ApplicationScoped
@Slf4j
public class GpxImportStrategy extends BaseGpsImportStrategy {

    private final XmlMapper xmlMapper = XmlMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Override
    public String getFormat() {
        return "gpx";
    }

    @Override
    protected FormatValidationResult validateFormatSpecificData(ImportJob job) throws IOException {
        String xmlContent = new String(job.getZipData()); // zipData contains GPX XML for GPX format

        // Parse GPX XML
        GpxFile gpxFile = xmlMapper.readValue(xmlContent, GpxFile.class);

        // Count valid GPS points from tracks and waypoints
        List<GpxTrackPoint> trackPoints = gpxFile.getAllTrackPoints();
        List<GpxWaypoint> waypoints = gpxFile.getValidWaypoints();

        int totalValidPoints = trackPoints.size() + waypoints.size();

        log.info("GPX validation successful: {} track points, {} waypoints ({} total valid GPS points)",
                trackPoints.size(), waypoints.size(), totalValidPoints);

        return new FormatValidationResult(trackPoints.size() + waypoints.size(), totalValidPoints);
    }

    @Override
    protected List<GpsPointEntity> parseAndConvertToGpsEntities(ImportJob job, UserEntity user) throws IOException {
        String xmlContent = new String(job.getZipData());
        GpxFile gpxFile = xmlMapper.readValue(xmlContent, GpxFile.class);

        // Convert GPX data to GPS entities
        return convertGpxToGpsPoints(gpxFile, user, job);
    }

    private List<GpsPointEntity> convertGpxToGpsPoints(GpxFile gpxFile, UserEntity user, ImportJob job) {
        List<GpsPointEntity> gpsPoints = new ArrayList<>();
        int processedPoints = 0;

        // Process track points
        List<GpxTrackPoint> trackPoints = gpxFile.getAllTrackPoints();
        for (GpxTrackPoint trackPoint : trackPoints) {
            GpsPointEntity gpsPoint = convertTrackPointToGpsPoint(trackPoint, user, job);
            if (gpsPoint != null) {
                gpsPoints.add(gpsPoint);
            }

            processedPoints++;
            updateProgress(processedPoints, trackPoints.size() + gpxFile.getValidWaypoints().size(), job, 10, 80);
        }

        // Process waypoints (if they have timestamps)
        List<GpxWaypoint> waypoints = gpxFile.getValidWaypoints();
        for (GpxWaypoint waypoint : waypoints) {
            GpsPointEntity gpsPoint = convertWaypointToGpsPoint(waypoint, user, job);
            if (gpsPoint != null) {
                gpsPoints.add(gpsPoint);
            }

            processedPoints++;
            updateProgress(processedPoints, trackPoints.size() + waypoints.size(), job, 10, 80);
        }

        return gpsPoints;
    }

    private GpsPointEntity convertTrackPointToGpsPoint(GpxTrackPoint trackPoint, UserEntity user, ImportJob job) {
        // Skip points without valid coordinates or timestamp
        if (!trackPoint.hasValidCoordinates() || !trackPoint.hasValidTime()) {
            return null;
        }

        // Apply date range filter using base class method
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

        // Apply date range filter using base class method
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


}
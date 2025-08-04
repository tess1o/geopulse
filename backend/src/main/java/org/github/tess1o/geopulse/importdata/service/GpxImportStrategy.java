package org.github.tess1o.geopulse.importdata.service;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.gpx.model.GpxFile;
import org.github.tess1o.geopulse.gps.integrations.gpx.model.GpxTrackPoint;
import org.github.tess1o.geopulse.gps.integrations.gpx.model.GpxWaypoint;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.timeline.model.LocationSource;
import org.github.tess1o.geopulse.timeline.model.TimelineStayEntity;
import org.github.tess1o.geopulse.timeline.service.TimelineInvalidationService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Import strategy for GPX (GPS Exchange Format) files
 */
@ApplicationScoped
@Slf4j
public class GpxImportStrategy implements ImportStrategy {
    
    @Inject
    UserRepository userRepository;
    
    @Inject
    BatchProcessor batchProcessor;
    
    @Inject
    TimelineInvalidationService timelineInvalidationService;
    
    private final XmlMapper xmlMapper = XmlMapper.builder()
            .addModule(new JavaTimeModule())
            .build();
    
    @Override
    public String getFormat() {
        return "gpx";
    }
    
    @Override
    public List<String> validateAndDetectDataTypes(ImportJob job) throws IOException {
        log.info("Validating GPX data for user {}", job.getUserId());
        
        try {
            String xmlContent = new String(job.getZipData()); // zipData contains GPX XML for GPX format
            
            // Parse GPX XML
            GpxFile gpxFile = xmlMapper.readValue(xmlContent, GpxFile.class);
            
            // Count valid GPS points from tracks and waypoints
            List<GpxTrackPoint> trackPoints = gpxFile.getAllTrackPoints();
            List<GpxWaypoint> waypoints = gpxFile.getValidWaypoints();
            
            int totalValidPoints = trackPoints.size() + waypoints.size();
            
            if (totalValidPoints == 0) {
                throw new IllegalArgumentException("GPX file contains no valid GPS data");
            }
            
            log.info("GPX validation successful: {} track points, {} waypoints ({} total valid GPS points)", 
                    trackPoints.size(), waypoints.size(), totalValidPoints);
            
            return List.of(ExportImportConstants.DataTypes.RAW_GPS);
            
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new IllegalArgumentException("Invalid GPX format: " + e.getMessage());
        }
    }
    
    @Override
    public void processImportData(ImportJob job) throws IOException {
        log.info("Processing GPX import data for user {}", job.getUserId());
        
        try {
            String xmlContent = new String(job.getZipData());
            GpxFile gpxFile = xmlMapper.readValue(xmlContent, GpxFile.class);
            
            UserEntity user = userRepository.findById(job.getUserId());
            if (user == null) {
                throw new IllegalStateException("User not found: " + job.getUserId());
            }
            
            // Convert GPX data to GPS entities
            List<GpsPointEntity> gpsPoints = convertGpxToGpsPoints(gpxFile, user, job);
            
            // Process in batches to avoid memory issues and timeouts
            int batchSize = 500; // Optimized batch size for large datasets
            BatchProcessor.BatchResult result = batchProcessor.processInBatches(gpsPoints, batchSize);
            
            // Trigger timeline generation since we only imported GPS data
            triggerTimelineGenerationForImportedGpsData(job);
            
            job.setProgress(100);
            
            log.info("GPX import completed for user {}: {} imported, {} skipped from {} total GPS points", 
                    job.getUserId(), result.imported, result.skipped, gpsPoints.size());

        } catch (Exception e) {
            log.error("Failed to process GPX import for user {}: {}", job.getUserId(), e.getMessage(), e);
            throw new IOException("Failed to process GPX import: " + e.getMessage(), e);
        }
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
            updateProgress(processedPoints, trackPoints.size() + gpxFile.getValidWaypoints().size(), job);
        }
        
        // Process waypoints (if they have timestamps)
        List<GpxWaypoint> waypoints = gpxFile.getValidWaypoints();
        for (GpxWaypoint waypoint : waypoints) {
            GpsPointEntity gpsPoint = convertWaypointToGpsPoint(waypoint, user, job);
            if (gpsPoint != null) {
                gpsPoints.add(gpsPoint);
            }
            
            processedPoints++;
            updateProgress(processedPoints, trackPoints.size() + waypoints.size(), job);
        }
        
        return gpsPoints;
    }
    
    private GpsPointEntity convertTrackPointToGpsPoint(GpxTrackPoint trackPoint, UserEntity user, ImportJob job) {
        // Skip points without valid coordinates or timestamp
        if (!trackPoint.hasValidCoordinates() || !trackPoint.hasValidTime()) {
            return null;
        }
        
        // Apply date range filter if specified
        if (job.getOptions().getDateRangeFilter() != null) {
            if (trackPoint.getTime().isBefore(job.getOptions().getDateRangeFilter().getStartDate()) ||
                trackPoint.getTime().isAfter(job.getOptions().getDateRangeFilter().getEndDate())) {
                return null;
            }
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
        
        // Apply date range filter if specified
        if (job.getOptions().getDateRangeFilter() != null) {
            if (waypoint.getTime().isBefore(job.getOptions().getDateRangeFilter().getStartDate()) ||
                waypoint.getTime().isAfter(job.getOptions().getDateRangeFilter().getEndDate())) {
                return null;
            }
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
    
    private void updateProgress(int processed, int total, ImportJob job) {
        if (processed % 100 == 0 || processed == total) {
            int progress = 10 + (int) ((double) processed / total * 80);
            job.setProgress(Math.min(progress, 90));
        }
    }
    
    /**
     * Triggers timeline generation for imported GPS data.
     * Since GPX import only imports GPS points (not pre-computed timeline data),
     * we need to generate timeline from the imported GPS points.
     */
    private void triggerTimelineGenerationForImportedGpsData(ImportJob job) {
        log.info("Triggering timeline generation for GPX import for user {}", job.getUserId());
        
        try {
            // Create a dummy timeline stay entity to trigger full user regeneration
            // This uses the same pattern as TimelineInvalidationService for full user regeneration
            UserEntity user = userRepository.findById(job.getUserId());
            if (user == null) {
                log.error("User not found for GPS timeline generation: {}", job.getUserId());
                return;
            }
            
            // Create a temporary stay entity to represent the need for timeline generation
            // We'll use a very old timestamp to ensure it gets processed
            TimelineStayEntity dummyStay = new TimelineStayEntity();
            dummyStay.setUser(user);
            dummyStay.setTimestamp(Instant.EPOCH); // Special marker for full regeneration
            dummyStay.setLatitude(0.0);
            dummyStay.setLongitude(0.0);
            dummyStay.setLocationName("GPX Import Trigger");
            dummyStay.setStayDuration(0);
            dummyStay.setLocationSource(LocationSource.HISTORICAL);
            dummyStay.setTimelineVersion("gpx-import-trigger");
            dummyStay.setIsStale(true);
            dummyStay.setCreatedAt(Instant.now());
            dummyStay.setLastUpdated(Instant.now());
            
            // Use the invalidation service to queue timeline generation
            timelineInvalidationService.markStaleAndQueue(List.of(dummyStay));
            
            log.info("Successfully queued timeline generation for GPX import for user {}", job.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to trigger timeline generation for GPX import for user {}: {}", 
                     job.getUserId(), e.getMessage(), e);
            // Don't fail the entire import - GPS data is still imported successfully
        }
    }
}
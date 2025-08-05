package org.github.tess1o.geopulse.timeline.assembly;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.shared.service.LocationPointResolver;
import org.github.tess1o.geopulse.shared.service.LocationResolutionResult;
import org.github.tess1o.geopulse.timeline.mapper.TimelineMapper;
import org.github.tess1o.geopulse.timeline.model.TrackPoint;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service responsible for data retrieval operations in the timeline domain.
 * Handles GPS data fetching and location name resolution while maintaining clean boundaries
 * between the timeline domain and external data sources.
 */
@ApplicationScoped
@Slf4j
public class TimelineDataService {

    private final GpsPointService gpsPointService;
    private final LocationPointResolver locationPointResolver;
    private final TimelineMapper timelineMapper;

    @Inject
    public TimelineDataService(GpsPointService gpsPointService,
                              LocationPointResolver locationPointResolver,
                              TimelineMapper timelineMapper) {
        this.gpsPointService = gpsPointService;
        this.locationPointResolver = locationPointResolver;
        this.timelineMapper = timelineMapper;
    }

    /**
     * Retrieve GPS point path data for a user within a time range.
     * 
     * @param userId the user identifier
     * @param startTime start of the time range
     * @param endTime end of the time range
     * @return GPS point path data, or null if no data available
     */
    public GpsPointPathDTO getGpsPointPath(UUID userId, Instant startTime, Instant endTime) {
        log.debug("Retrieving GPS points for user {} from {} to {}", userId, startTime, endTime);
        return gpsPointService.getGpsPointPath(userId, startTime, endTime);
    }

    /**
     * Convert GPS point path data to track points for timeline processing.
     * 
     * @param gpsPointPath the GPS point path data
     * @return list of track points for algorithm processing
     */
    public List<TrackPoint> convertToTrackPoints(GpsPointPathDTO gpsPointPath) {
        if (gpsPointPath == null || gpsPointPath.getPoints() == null) {
            return List.of();
        }
        return timelineMapper.toTrackPoints(gpsPointPath.getPoints());
    }

    /**
     * Enhanced location resolution that includes source references for database persistence.
     * 
     * @param userId user ID for favorite location lookup
     * @param point coordinates to resolve
     * @return location resolution result with name and source references
     */
    public LocationResolutionResult resolveLocationWithReferences(UUID userId, Point point) {
        log.debug("Resolving location with references for user {} at point {}", userId, point);
        return locationPointResolver.resolveLocationWithReferences(userId, point);
    }

    /**
     * Batch location resolution with references for timeline assembly.
     * Optimized to reduce database round-trips and respect API rate limits.
     * 
     * @param userId user ID for favorite location lookup
     * @param coordinates list of coordinates to resolve
     * @return map of coordinate string (lon,lat) to location resolution results
     */
    public Map<String, LocationResolutionResult> resolveLocationsWithReferencesBatch(UUID userId, List<Point> coordinates) {
        log.debug("Batch resolving {} locations with references for user {}", coordinates.size(), userId);
        return locationPointResolver.resolveLocationsWithReferencesBatch(userId, coordinates);
    }
}
package org.github.tess1o.geopulse.timeline.assembly;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.timeline.core.SpatialCalculationService;
import org.github.tess1o.geopulse.timeline.mapper.TimelineMapper;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineStayPoint;
import org.github.tess1o.geopulse.timeline.model.TimelineTrip;
import org.github.tess1o.geopulse.timeline.model.TimelineTripDTO;
import org.locationtech.jts.geom.Point;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service responsible for assembling timeline data into DTOs.
 * Handles the construction of the final timeline data structure from
 * processed stay points and trips, including DTO mapping and sorting.
 */
@ApplicationScoped
@Slf4j
public class TimelineAssemblyService {

    private final TimelineMapper timelineMapper;
    private final TimelineDataService timelineDataService;
    private final SpatialCalculationService spatialCalculationService;

    @Inject
    public TimelineAssemblyService(TimelineMapper timelineMapper,
                                 TimelineDataService timelineDataService,
                                 SpatialCalculationService spatialCalculationService) {
        this.timelineMapper = timelineMapper;
        this.timelineDataService = timelineDataService;
        this.spatialCalculationService = spatialCalculationService;
    }

    /**
     * Assemble a complete movement timeline from stay points and trips.
     * 
     * @param userId the user identifier
     * @param stayPoints detected stay points
     * @param trips detected trips
     * @return assembled movement timeline DTO
     */
    public MovementTimelineDTO assembleTimeline(UUID userId,
                                              List<TimelineStayPoint> stayPoints,
                                              List<TimelineTrip> trips) {
        log.debug("Assembling timeline for user {} with {} stay points and {} trips", 
                 userId, stayPoints.size(), trips.size());

        List<TimelineStayLocationDTO> stayLocations = processStayPoints(userId, stayPoints);
        List<TimelineTripDTO> tripDetails = processTrips(trips);

        return new MovementTimelineDTO(userId, stayLocations, tripDetails);
    }

    /**
     * Create an empty timeline for a user when no data is available.
     * 
     * @param userId the user identifier
     * @return empty movement timeline DTO
     */
    public MovementTimelineDTO createEmptyTimeline(UUID userId) {
        log.debug("Creating empty timeline for user {}", userId);
        return new MovementTimelineDTO(userId);
    }

    /**
     * Process stay points into timeline stay location DTOs.
     * 
     * @param userId the user identifier
     * @param stayPoints detected stay points
     * @return processed and sorted stay location DTOs
     */
    private List<TimelineStayLocationDTO> processStayPoints(UUID userId, List<TimelineStayPoint> stayPoints) {
        return stayPoints.stream()
                .map(stayPoint -> createStayLocationDTO(userId, stayPoint))
                .sorted(Comparator.comparing(TimelineStayLocationDTO::getTimestamp))
                .collect(Collectors.toList());
    }

    /**
     * Create a stay location DTO from a stay point, including location resolution.
     * 
     * @param userId the user identifier
     * @param stayPoint the stay point to convert
     * @return stay location DTO with resolved location name
     */
    private TimelineStayLocationDTO createStayLocationDTO(UUID userId, TimelineStayPoint stayPoint) {
        Point locationPoint = spatialCalculationService.createPoint(stayPoint.longitude(), stayPoint.latitude());
        var locationResult = timelineDataService.resolveLocationWithReferences(userId, locationPoint);
        return timelineMapper.toTimelineStayLocationDTO(stayPoint, locationResult);
    }

    /**
     * Process trips into timeline trip DTOs.
     * 
     * @param trips detected trips
     * @return processed and sorted trip DTOs
     */
    private List<TimelineTripDTO> processTrips(List<TimelineTrip> trips) {
        return trips.stream()
                .filter(trip -> !trip.path().isEmpty())
                .map(this::createTripDTO)
                .sorted(Comparator.comparing(TimelineTripDTO::getTimestamp))
                .collect(Collectors.toList());
    }

    /**
     * Create a trip DTO from a trip, extracting start point information.
     * 
     * @param trip the trip to convert
     * @return trip DTO with start point coordinates
     */
    private TimelineTripDTO createTripDTO(TimelineTrip trip) {
        GpsPoint startPoint = trip.path().getFirst();
        return timelineMapper.toTimelineTripDTO(trip, startPoint.getLatitude(), startPoint.getLongitude(), trip.path());
    }
}
package org.github.tess1o.geopulse.trips.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gps.service.simplification.PathSimplificationService;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class TripWorkspaceDataService {

    private final TripService tripService;
    private final TripAccessService tripAccessService;
    private final StreamingTimelineAggregator timelineAggregator;
    private final GpsPointRepository gpsPointRepository;
    private final PathSimplificationService pathSimplificationService;
    private final TimelineConfigurationProvider timelineConfigurationProvider;

    public TripWorkspaceDataService(TripService tripService,
                                    TripAccessService tripAccessService,
                                    StreamingTimelineAggregator timelineAggregator,
                                    GpsPointRepository gpsPointRepository,
                                    PathSimplificationService pathSimplificationService,
                                    TimelineConfigurationProvider timelineConfigurationProvider) {
        this.tripService = tripService;
        this.tripAccessService = tripAccessService;
        this.timelineAggregator = timelineAggregator;
        this.gpsPointRepository = gpsPointRepository;
        this.pathSimplificationService = pathSimplificationService;
        this.timelineConfigurationProvider = timelineConfigurationProvider;
    }

    public MovementTimelineDTO getTripTimeline(UUID userId, Long tripId, Instant requestedStart, Instant requestedEnd) {
        TripAccessContext access = tripAccessService.requireReadAccess(userId, tripId);
        TripEntity trip = access.trip();
        UUID ownerUserId = access.ownerUserId();
        if (isUnplannedTrip(trip)) {
            return new MovementTimelineDTO(ownerUserId, List.of(), List.of(), List.of());
        }

        Instant start = resolveStart(trip, requestedStart);
        Instant end = resolveEnd(trip, requestedEnd);
        validateRequestedRange(trip, start, end);

        return timelineAggregator.getTimelineFromDb(ownerUserId, start, end);
    }

    @SuppressWarnings("unchecked")
    public GpsPointPathDTO getTripPath(UUID userId, Long tripId, Instant requestedStart, Instant requestedEnd) {
        TripAccessContext access = tripAccessService.requireReadAccess(userId, tripId);
        TripEntity trip = access.trip();
        UUID ownerUserId = access.ownerUserId();
        if (isUnplannedTrip(trip)) {
            List<GpsPointPathPointDTO> emptyPoints = List.of();
            List<List<GpsPoint>> emptySegments = List.of();
            return new GpsPointPathDTO(ownerUserId, emptyPoints, 0, emptySegments);
        }

        Instant start = resolveStart(trip, requestedStart);
        Instant end = resolveEnd(trip, requestedEnd);
        validateRequestedRange(trip, start, end);

        List<GpsPointEntity> gpsPoints = gpsPointRepository.findByUserIdAndTimePeriod(ownerUserId, start, end);
        List<GpsPoint> converted = gpsPoints.stream()
                .map(gp -> new GpsPointPathPointDTO(
                        gp.getId(),
                        gp.getCoordinates().getX(),
                        gp.getCoordinates().getY(),
                        gp.getTimestamp(),
                        gp.getAccuracy(),
                        gp.getAltitude(),
                        gp.getVelocity(),
                        ownerUserId,
                        gp.getSourceType() != null ? gp.getSourceType().name() : null
                ))
                .collect(Collectors.toList());

        TimelineConfig config = timelineConfigurationProvider.getConfigurationForUser(ownerUserId);
        List<? extends GpsPoint> simplified = pathSimplificationService.simplify(converted, config);
        return new GpsPointPathDTO(ownerUserId, (List<GpsPointPathPointDTO>) simplified);
    }

    private Instant resolveStart(TripEntity trip, Instant requestedStart) {
        return requestedStart != null ? requestedStart : trip.getStartTime();
    }

    private Instant resolveEnd(TripEntity trip, Instant requestedEnd) {
        return requestedEnd != null ? requestedEnd : trip.getEndTime();
    }

    private boolean isUnplannedTrip(TripEntity trip) {
        return tripService.resolveStatus(trip) == TripStatus.UNPLANNED
                || trip.getStartTime() == null
                || trip.getEndTime() == null;
    }

    private void validateRequestedRange(TripEntity trip, Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        if (start.isBefore(trip.getStartTime()) || end.isAfter(trip.getEndTime())) {
            throw new IllegalArgumentException("Requested range must be inside trip range");
        }
    }
}

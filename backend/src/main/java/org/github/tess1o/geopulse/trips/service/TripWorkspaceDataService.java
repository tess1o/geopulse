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

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class TripWorkspaceDataService {

    private final TripService tripService;
    private final StreamingTimelineAggregator timelineAggregator;
    private final GpsPointRepository gpsPointRepository;
    private final PathSimplificationService pathSimplificationService;
    private final TimelineConfigurationProvider timelineConfigurationProvider;

    public TripWorkspaceDataService(TripService tripService,
                                    StreamingTimelineAggregator timelineAggregator,
                                    GpsPointRepository gpsPointRepository,
                                    PathSimplificationService pathSimplificationService,
                                    TimelineConfigurationProvider timelineConfigurationProvider) {
        this.tripService = tripService;
        this.timelineAggregator = timelineAggregator;
        this.gpsPointRepository = gpsPointRepository;
        this.pathSimplificationService = pathSimplificationService;
        this.timelineConfigurationProvider = timelineConfigurationProvider;
    }

    public MovementTimelineDTO getTripTimeline(UUID userId, Long tripId, Instant requestedStart, Instant requestedEnd) {
        TripEntity trip = tripService.getTripEntityOrThrow(userId, tripId);
        Instant start = resolveStart(trip, requestedStart);
        Instant end = resolveEnd(trip, requestedEnd);
        validateRequestedRange(trip, start, end);

        return timelineAggregator.getTimelineFromDb(userId, start, end);
    }

    @SuppressWarnings("unchecked")
    public GpsPointPathDTO getTripPath(UUID userId, Long tripId, Instant requestedStart, Instant requestedEnd) {
        TripEntity trip = tripService.getTripEntityOrThrow(userId, tripId);
        Instant start = resolveStart(trip, requestedStart);
        Instant end = resolveEnd(trip, requestedEnd);
        validateRequestedRange(trip, start, end);

        List<GpsPointEntity> gpsPoints = gpsPointRepository.findByUserIdAndTimePeriod(userId, start, end);
        List<GpsPoint> converted = gpsPoints.stream()
                .map(gp -> new GpsPointPathPointDTO(
                        gp.getId(),
                        gp.getCoordinates().getX(),
                        gp.getCoordinates().getY(),
                        gp.getTimestamp(),
                        gp.getAccuracy(),
                        gp.getAltitude(),
                        gp.getVelocity(),
                        userId,
                        gp.getSourceType() != null ? gp.getSourceType().name() : null
                ))
                .collect(Collectors.toList());

        TimelineConfig config = timelineConfigurationProvider.getConfigurationForUser(userId);
        List<? extends GpsPoint> simplified = pathSimplificationService.simplify(converted, config);
        return new GpsPointPathDTO(userId, (List<GpsPointPathPointDTO>) simplified);
    }

    private Instant resolveStart(TripEntity trip, Instant requestedStart) {
        return requestedStart != null ? requestedStart : trip.getStartTime();
    }

    private Instant resolveEnd(TripEntity trip, Instant requestedEnd) {
        return requestedEnd != null ? requestedEnd : trip.getEndTime();
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


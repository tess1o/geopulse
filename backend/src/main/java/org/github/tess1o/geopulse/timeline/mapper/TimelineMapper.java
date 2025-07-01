package org.github.tess1o.geopulse.timeline.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.shared.service.LocationResolutionResult;
import org.github.tess1o.geopulse.timeline.model.*;
import org.github.tess1o.geopulse.timeline.core.SpatialCalculationService;

import java.time.Duration;
import java.util.List;

@ApplicationScoped
public class TimelineMapper {

    private final SpatialCalculationService spatialCalculationService;

    @Inject
    public TimelineMapper(SpatialCalculationService spatialCalculationService) {
        this.spatialCalculationService = spatialCalculationService;
    }

    public TimelineTripDTO toTimelineTripDTO(TimelineTrip trip, double lat, double lon, List<? extends GpsPoint> pathPoints) {
        return TimelineTripDTO.builder()
                .latitude(lat)
                .longitude(lon)
                .timestamp(trip.startTime())
                .movementType(trip.travelMode().name())
                .distanceKm(spatialCalculationService.calculateTripDistance(trip.path()))
                .tripDuration(Duration.between(trip.startTime(), trip.endTime()).toMinutes())
                .path(pathPoints)
                .build();
    }

    public TimelineStayLocationDTO toTimelineStayLocationDTO(TimelineStayPoint timelineStayPoint, LocationResolutionResult locationResult) {
        return TimelineStayLocationDTO.builder()
                .stayDuration(Duration.between(timelineStayPoint.startTime(), timelineStayPoint.endTime()).toMinutes())
                .latitude(timelineStayPoint.latitude())
                .longitude(timelineStayPoint.longitude())
                .locationName(locationResult.getLocationName())
                .favoriteId(locationResult.getFavoriteId())
                .geocodingId(locationResult.getGeocodingId())
                .timestamp(timelineStayPoint.startTime())
                .build();
    }

    public TrackPoint toTrackPoint(GpsPointPathPointDTO pathPoint) {
        return TrackPoint.builder()
                .longitude(pathPoint.getLongitude())
                .latitude(pathPoint.getLatitude())
                .timestamp(pathPoint.getTimestamp())
                .velocity(pathPoint.getVelocity())
                .accuracy(pathPoint.getAccuracy())
                .build();
    }

    public List<TrackPoint> toTrackPoints(List<GpsPointPathPointDTO> pathPoints) {
        return pathPoints.stream()
                .map(this::toTrackPoint)
                .toList();
    }
}

package org.github.tess1o.geopulse.timeline.detection.trips;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.timeline.model.TimelineStayPoint;
import org.github.tess1o.geopulse.timeline.model.TimelineTrip;
import org.github.tess1o.geopulse.timeline.model.TravelMode;
import org.github.tess1o.geopulse.timeline.core.SpatialCalculationService;
import org.github.tess1o.geopulse.timeline.core.TimelineValidationService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class TimelineTripsDetectorSingle implements TimelineTripsDetector {

    private final TravelClassification travelClassification;
    private final SpatialCalculationService spatialCalculationService;
    private final TimelineValidationService validationService;

    @Inject
    public TimelineTripsDetectorSingle(TravelClassification travelClassification,
                                     SpatialCalculationService spatialCalculationService,
                                     TimelineValidationService validationService) {
        this.travelClassification = travelClassification;
        this.spatialCalculationService = spatialCalculationService;
        this.validationService = validationService;
    }

    public List<TimelineTrip> detectTrips(TimelineConfig config, List<GpsPointPathPointDTO> allPoints, List<TimelineStayPoint> timelineStayPoints) {
        List<TimelineTrip> trips = new ArrayList<>();
        for (int i = 1; i < timelineStayPoints.size(); i++) {
            GpsPoint startClosestPoint = getStayPointClosestPoint(timelineStayPoints.get(i - 1), allPoints);
            GpsPoint endClosestPoint = getStayPointClosestPoint(timelineStayPoints.get(i), allPoints);

            List<? extends GpsPoint> path = allPoints.stream()
                    .filter(p ->
                            validationService.isBetweenInclusive(startClosestPoint.getTimestamp(), endClosestPoint.getTimestamp(), p.getTimestamp()))
                    .toList();

            if (!path.isEmpty()) {
                Instant startTime = timelineStayPoints.get(i - 1).endTime();
                Instant endTime = timelineStayPoints.get(i).startTime();
                Duration duration = Duration.between(startTime, endTime);
                TravelMode travelMode = travelClassification.classifyTravelType(path, duration);
                trips.add(new TimelineTrip(startTime, endTime, path, travelMode));
            }
        }
        return trips;
    }

    private GpsPoint getStayPointClosestPoint(TimelineStayPoint timelineStayPoint, List<? extends GpsPoint> allPoints) {
        return allPoints.stream()
                .filter(p -> validationService.isBetweenInclusive(timelineStayPoint.startTime(), timelineStayPoint.endTime(), p.getTimestamp()))
                .min(Comparator.comparingDouble(p -> spatialCalculationService.calculateDistance(p.getLatitude(), p.getLongitude(), timelineStayPoint.latitude(), timelineStayPoint.longitude())))
                .orElseThrow(() -> new RuntimeException("Cannot find the closest point to " + timelineStayPoint)); //should not happen
    }
}

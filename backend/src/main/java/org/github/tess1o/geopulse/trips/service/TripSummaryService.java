package org.github.tess1o.geopulse.trips.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.streaming.service.LocationAnalyticsService;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.trips.model.dto.TripSummaryDto;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripStatus;
import org.github.tess1o.geopulse.trips.repository.TripPlanItemRepository;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class TripSummaryService {

    private final TripService tripService;
    private final TripAccessService tripAccessService;
    private final TripPlanItemRepository tripPlanItemRepository;
    private final TimelineStayRepository timelineStayRepository;
    private final TimelineTripRepository timelineTripRepository;
    private final LocationAnalyticsService locationAnalyticsService;

    @ConfigProperty(name = "geopulse.trip.summary.places.page", defaultValue = "1")
    int placesPage;

    @ConfigProperty(name = "geopulse.trip.summary.places.page-size", defaultValue = "10000")
    int placesPageSize;

    public TripSummaryService(TripService tripService,
                              TripAccessService tripAccessService,
                              TripPlanItemRepository tripPlanItemRepository,
                              TimelineStayRepository timelineStayRepository,
                              TimelineTripRepository timelineTripRepository,
                              LocationAnalyticsService locationAnalyticsService) {
        this.tripService = tripService;
        this.tripAccessService = tripAccessService;
        this.tripPlanItemRepository = tripPlanItemRepository;
        this.timelineStayRepository = timelineStayRepository;
        this.timelineTripRepository = timelineTripRepository;
        this.locationAnalyticsService = locationAnalyticsService;
    }

    public TripSummaryDto getSummary(UUID userId, Long tripId) {
        TripAccessContext access = tripAccessService.requireReadAccess(userId, tripId);
        TripEntity trip = access.trip();
        UUID ownerUserId = access.ownerUserId();
        TripStatus resolvedStatus = tripService.resolveStatus(trip);

        long[] planCounts = tripPlanItemRepository.countSummary(tripId);
        int totalPlanItems = Math.toIntExact(planCounts[0]);
        int visitedPlanItems = Math.toIntExact(planCounts[1]);
        double completionRate = totalPlanItems == 0 ? 0.0 : (visitedPlanItems * 100.0) / totalPlanItems;

        if (resolvedStatus == TripStatus.UNPLANNED || trip.getStartTime() == null || trip.getEndTime() == null) {
            return TripSummaryDto.builder()
                    .tripId(trip.getId())
                    .tripName(trip.getName())
                    .status(TripStatus.UNPLANNED)
                    .startTime(null)
                    .endTime(null)
                    .planItemsTotal(totalPlanItems)
                    .planItemsVisited(visitedPlanItems)
                    .planCompletionRate(completionRate)
                    .timelineStays(0)
                    .timelineTrips(0)
                    .totalDistanceMeters(0)
                    .totalTripDurationSeconds(0)
                    .actualPlacesCount(0)
                    .build();
        }

        Instant start = trip.getStartTime();
        Instant end = trip.getEndTime();

        long staysCount = timelineStayRepository.countByUserIdAndTimeRange(ownerUserId, start, end);
        long[] tripTotals = timelineTripRepository.summarizeOverlapping(ownerUserId, start, end);
        int placesCount = locationAnalyticsService.countMapPlaces(ownerUserId, start, end, placesPage, placesPageSize);

        return TripSummaryDto.builder()
                .tripId(trip.getId())
                .tripName(trip.getName())
                .status(resolvedStatus)
                .startTime(start)
                .endTime(end)
                .planItemsTotal(totalPlanItems)
                .planItemsVisited(visitedPlanItems)
                .planCompletionRate(completionRate)
                .timelineStays(staysCount)
                .timelineTrips(tripTotals[0])
                .totalDistanceMeters(tripTotals[1])
                .totalTripDurationSeconds(tripTotals[2])
                .actualPlacesCount(placesCount)
                .build();
    }
}

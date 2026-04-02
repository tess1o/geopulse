package org.github.tess1o.geopulse.trips.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.service.LocationAnalyticsService;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.trips.model.dto.TripSummaryDto;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripStatus;
import org.github.tess1o.geopulse.trips.repository.TripPlanItemRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class TripSummaryService {

    private final TripService tripService;
    private final TripAccessService tripAccessService;
    private final TripPlanItemRepository tripPlanItemRepository;
    private final StreamingTimelineAggregator timelineAggregator;
    private final TimelineTripRepository timelineTripRepository;
    private final LocationAnalyticsService locationAnalyticsService;

    @ConfigProperty(name = "geopulse.trip.summary.places.page", defaultValue = "1")
    int placesPage;

    @ConfigProperty(name = "geopulse.trip.summary.places.page-size", defaultValue = "10000")
    int placesPageSize;

    public TripSummaryService(TripService tripService,
                              TripAccessService tripAccessService,
                              TripPlanItemRepository tripPlanItemRepository,
                              StreamingTimelineAggregator timelineAggregator,
                              TimelineTripRepository timelineTripRepository,
                              LocationAnalyticsService locationAnalyticsService) {
        this.tripService = tripService;
        this.tripAccessService = tripAccessService;
        this.tripPlanItemRepository = tripPlanItemRepository;
        this.timelineAggregator = timelineAggregator;
        this.timelineTripRepository = timelineTripRepository;
        this.locationAnalyticsService = locationAnalyticsService;
    }

    public TripSummaryDto getSummary(UUID userId, Long tripId) {
        TripAccessContext access = tripAccessService.requireReadAccess(userId, tripId);
        TripEntity trip = access.trip();
        UUID ownerUserId = access.ownerUserId();
        TripStatus resolvedStatus = tripService.resolveStatus(trip);

        List<TripPlanItemEntity> planItems = tripPlanItemRepository.findByTripId(tripId);
        int totalPlanItems = planItems.size();
        int visitedPlanItems = (int) planItems.stream().filter(item -> Boolean.TRUE.equals(item.getIsVisited())).count();
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

        Map<String, Long> timelineCounts = timelineAggregator.getTimelineItemCounts(ownerUserId, start, end);

        List<TimelineTripEntity> trips = timelineTripRepository.findByUserIdAndTimeRangeWithExpansion(ownerUserId, start, end);
        long totalDistanceMeters = trips.stream()
                .mapToLong(TimelineTripEntity::getDistanceMeters)
                .sum();
        long totalTripDurationSeconds = trips.stream()
                .mapToLong(TimelineTripEntity::getTripDuration)
                .sum();

        var places = locationAnalyticsService.getMapPlaces(
                ownerUserId, start, end, null, null, null, null, placesPage, placesPageSize
        );

        return TripSummaryDto.builder()
                .tripId(trip.getId())
                .tripName(trip.getName())
                .status(resolvedStatus)
                .startTime(start)
                .endTime(end)
                .planItemsTotal(totalPlanItems)
                .planItemsVisited(visitedPlanItems)
                .planCompletionRate(completionRate)
                .timelineStays(timelineCounts.getOrDefault("stays", 0L))
                .timelineTrips(timelineCounts.getOrDefault("trips", 0L))
                .totalDistanceMeters(totalDistanceMeters)
                .totalTripDurationSeconds(totalTripDurationSeconds)
                .actualPlacesCount(places.size())
                .build();
    }
}

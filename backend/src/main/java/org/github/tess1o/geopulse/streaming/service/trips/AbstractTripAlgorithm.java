package org.github.tess1o.geopulse.streaming.service.trips;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public abstract class AbstractTripAlgorithm implements StreamTripAlgorithm {

    @Inject
    TravelClassification travelClassification;
    @Inject
    GpsStatisticsCalculator gpsStatisticsCalculator;
    @Inject
    TripWaterClassificationService tripWaterClassificationService;
    @Inject
    GpsPointRepository gpsPointRepository;

    @Override
    public abstract List<TimelineEvent> apply(UUID userId,
                                              List<TimelineEvent> events,
                                              TimelineConfig config,
                                              String environmentDatasetVersion);

    /**
     * Validate trip against minimum distance and duration requirements.
     */
    protected boolean isValidTrip(Trip trip, TimelineConfig config) {
        if (trip == null) {
            return false;
        }

        boolean validDistance = isValidTripDistance(trip, config);
        Integer minDuration = config.getStaypointMinDurationMinutes();

        boolean validDuration = minDuration == null || trip.getDuration().toMinutes() >= minDuration;

        return validDistance && validDuration;
    }

    protected boolean isValidTripDistance(Trip trip, TimelineConfig config) {
        if (trip == null) {
            return false;
        }

        Integer minDistance = config.getStaypointRadiusMeters();
        return minDistance == null || trip.getDistanceMeters() >= minDistance;
    }

    /**
     * Merge multiple trip segments into a single trip.
     * Used by single algorithm to combine fragmented trips.
     */
    protected Trip mergeTripSegments(UUID userId,
                                     List<Trip> trips,
                                     TimelineConfig config,
                                     String environmentDatasetVersion) {
        if (trips.isEmpty()) {
            return null;
        }

        if (trips.size() == 1) {
            return trips.getFirst();
        }

        // Combine all trip segments
        Trip firstTrip = trips.getFirst();
        Trip lastTrip = trips.getLast();

        Instant startTime = firstTrip.getStartTime();
        Instant endTime = lastTrip.getEndTime();


        // Calculate total distance and duration
        double totalDistance = trips.stream()
                .mapToDouble(Trip::getDistanceMeters)
                .sum();

        Duration totalDuration = Duration.between(
                firstTrip.getStartTime(),
                lastTrip.getStartTime().plus(lastTrip.getDuration())
        );

        // Check if any of the trips being merged is an inferred trip (empty GPS statistics)
        boolean hasInferredTrip = trips.stream()
                .anyMatch(t -> t.getStatistics() == null || !t.getStatistics().hasValidData());

        // Classify the overall trip mode
        TripGpsStatistics tripGpsStatistics;
        TripWaterStatistics tripWaterStatistics;
        TripType overallTripType;

        if (hasInferredTrip) {
            // If merging includes an inferred trip, use empty statistics to trigger distance-based classification
            // This prevents polluting flight classification with low-speed GPS data from adjacent car trips
            log.debug("Merged trips include inferred trip - using distance-based classification");
            tripGpsStatistics = TripGpsStatistics.empty();
            tripWaterStatistics = TripWaterStatistics.unavailable();
            overallTripType = classifyMergedTrip(totalDistance, totalDuration, tripGpsStatistics, tripWaterStatistics, config);
        } else {
            // Normal case: recalculate GPS and water statistics from one interval reload.
            MergedTripStatistics mergedTripStatistics = calculateMergedTripStatistics(
                    userId,
                    startTime,
                    endTime,
                    config,
                    environmentDatasetVersion
            );
            tripGpsStatistics = mergedTripStatistics.gpsStatistics();
            tripWaterStatistics = mergedTripStatistics.waterStatistics();
            overallTripType = classifyMergedTrip(totalDistance, totalDuration, tripGpsStatistics, tripWaterStatistics, config);
        }

        Trip mergedTrip = Trip.builder()
                .startTime(firstTrip.getStartTime())
                .duration(totalDuration)
                .distanceMeters(totalDistance)
                .startPoint(firstTrip.getStartLocation())
                .endPoint(lastTrip.getEndLocation())
                .statistics(tripGpsStatistics)
                .waterStatistics(tripWaterStatistics)
                .tripType(overallTripType)
                .build();

        log.debug("Merged {} trips into single trip: {}m, {}min, {}",
                trips.size(), totalDistance, totalDuration.toMinutes(), overallTripType);

        return mergedTrip;
    }

    protected TripType classifyMergedTrip(double totalDistance,
                                          Duration tripDuration,
                                          TripGpsStatistics tripGpsStatistics,
                                          TripWaterStatistics tripWaterStatistics,
                                          TimelineConfig config) {
        return travelClassification.classifyTravelType(
                tripGpsStatistics,
                tripWaterStatistics,
                tripDuration,
                Double.valueOf(totalDistance).longValue(),
                config
        );
    }

    /**
     * Check if two stays represent the same location.
     * Uses favorite/geocoding references first, then falls back to location names.
     */
    protected boolean isSameLocation(Stay stay1, Stay stay2) {
        if (stay1 == null || stay2 == null) {
            return false;
        }

        if (stay1.getFavoriteId() != null && stay2.getFavoriteId() != null) {
            return stay1.getFavoriteId().equals(stay2.getFavoriteId());
        }

        if (stay1.getGeocodingId() != null && stay2.getGeocodingId() != null) {
            return stay1.getGeocodingId().equals(stay2.getGeocodingId());
        }

        if (stay1.getLocationName() != null && stay2.getLocationName() != null) {
            return stay1.getLocationName().equals(stay2.getLocationName());
        }

        return false;
    }

    /**
     * Create a synthetic continuity trip between two consecutive stays with no detected trip.
     * Returns null when timestamps are missing or inconsistent.
     */
    protected Trip createContinuityTripBetweenStays(Stay previousStay, Stay nextStay, TimelineConfig config) {
        if (previousStay == null || nextStay == null ||
            previousStay.getStartTime() == null || previousStay.getDuration() == null || nextStay.getStartTime() == null) {
            log.warn("Cannot create continuity trip - missing stay timing data");
            return null;
        }

        Instant tripStart = previousStay.getEndTime();
        Instant tripEnd = nextStay.getStartTime();

        if (tripEnd.isBefore(tripStart)) {
            log.warn("Cannot create continuity trip - inconsistent stay order: previous end {} after next start {}",
                    tripStart, tripEnd);
            return null;
        }

        Duration tripDuration = Duration.between(tripStart, tripEnd);
        double distanceMeters = GeoUtils.haversine(
                previousStay.getLatitude(), previousStay.getLongitude(),
                nextStay.getLatitude(), nextStay.getLongitude());

        GPSPoint startPoint = GPSPoint.builder()
                .timestamp(tripStart)
                .latitude(previousStay.getLatitude())
                .longitude(previousStay.getLongitude())
                .speed(0.0)
                .accuracy(0.0)
                .build();
        GPSPoint endPoint = GPSPoint.builder()
                .timestamp(tripEnd)
                .latitude(nextStay.getLatitude())
                .longitude(nextStay.getLongitude())
                .speed(0.0)
                .accuracy(0.0)
                .build();

        TripGpsStatistics stats = TripGpsStatistics.empty();
        TripWaterStatistics waterStats = TripWaterStatistics.unavailable();
        TripType tripType;
        if (tripDuration.isZero() || distanceMeters <= 0) {
            tripType = TripType.UNKNOWN;
        } else {
            tripType = travelClassification.classifyTravelType(
                    stats,
                    waterStats,
                    tripDuration,
                    Double.valueOf(distanceMeters).longValue(),
                    config
            );
        }

        return Trip.builder()
                .startTime(tripStart)
                .duration(tripDuration)
                .statistics(stats)
                .waterStatistics(waterStats)
                .startPoint(startPoint)
                .endPoint(endPoint)
                .distanceMeters(distanceMeters)
                .tripType(tripType)
                .build();
    }

    private MergedTripStatistics calculateMergedTripStatistics(UUID userId,
                                                               Instant startTime,
                                                               Instant endTime,
                                                               TimelineConfig config,
                                                               String environmentDatasetVersion) {
        if (gpsPointRepository == null) {
            log.debug("GPS point repository unavailable during merged-trip statistics recalculation");
            return new MergedTripStatistics(TripGpsStatistics.empty(), TripWaterStatistics.unavailable());
        }

        String effectiveEnvironmentDatasetVersion = resolveEnvironmentDatasetVersion(config, environmentDatasetVersion);
        List<GPSPoint> intervalPoints = gpsPointRepository.findEssentialPointsInInterval(
                userId,
                startTime,
                endTime,
                effectiveEnvironmentDatasetVersion
        );

        return new MergedTripStatistics(
                gpsStatisticsCalculator.calculateStatistics(intervalPoints),
                calculateWaterStatistics(intervalPoints, config)
        );
    }

    private String resolveEnvironmentDatasetVersion(TimelineConfig config, String environmentDatasetVersion) {
        if (!isBoatEnabled(config) || tripWaterClassificationService == null) {
            return null;
        }
        return environmentDatasetVersion;
    }

    private TripWaterStatistics calculateWaterStatistics(List<GPSPoint> gpsPoints, TimelineConfig config) {
        if (tripWaterClassificationService == null) {
            return TripWaterStatistics.unavailable();
        }
        return tripWaterClassificationService.calculateStatistics(gpsPoints, config);
    }

    private boolean isBoatEnabled(TimelineConfig config) {
        return config != null && Boolean.TRUE.equals(config.getBoatEnabled());
    }

    private record MergedTripStatistics(TripGpsStatistics gpsStatistics, TripWaterStatistics waterStatistics) {
    }
}

package org.github.tess1o.geopulse.streaming.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.service.LocationPointResolver;
import org.github.tess1o.geopulse.shared.service.LocationResolutionResult;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.*;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.service.trips.GpsStatisticsCalculator;
import org.github.tess1o.geopulse.streaming.service.trips.TravelClassification;
import org.github.tess1o.geopulse.streaming.service.trips.TripGpsStatistics;
import org.github.tess1o.geopulse.streaming.service.trips.TripWaterClassificationService;
import org.github.tess1o.geopulse.streaming.service.trips.TripAltitudeStatistics;
import org.github.tess1o.geopulse.streaming.service.trips.TripAltitudeStatisticsCalculator;
import org.github.tess1o.geopulse.streaming.service.trips.TripWaterStatistics;
import org.locationtech.jts.geom.Point;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Centralized service for finalizing timeline events (stays and trips).
 * This service eliminates code duplication between StreamingTimelineProcessor
 * and DataGapDetectionEngine by providing shared finalization logic.
 * <p>
 * Includes batch location resolution optimization to improve performance
 * by reducing individual database queries from O(n) to O(1) batch operations.
 */
@ApplicationScoped
@Slf4j
public class TimelineEventFinalizationService {

    @Inject
    LocationPointResolver locationPointResolver;

    @Inject
    TravelClassification travelClassification;

    @Inject
    GpsStatisticsCalculator gpsStatisticsCalculator;

    @Inject
    TripWaterClassificationService tripWaterClassificationService;

    @Inject
    TripAltitudeStatisticsCalculator tripAltitudeStatisticsCalculator;

    /** How many stays {@link #populateStayLocations} resolves together. */
    int locationChunkSize = 10_000;

    /**
     * Finalize a stay event from user state without location resolution.
     * Location data will be populated later via batch processing.
     * Used by both main processing and data gap detection.
     *
     * Notes on the active explanation when the stay keeps points outside its radius for its favorite area.
     *
     * @param userState current user state with active stay points
     * @param config    timeline configuration
     * @return finalized stay event without location data, or null if the stay has no points
     */
    public Stay finalizeStayWithoutLocation(UserState userState, TimelineConfig config) {
        GPSPoint firstPoint = userState.getFirstActivePoint();
        GPSPoint lastPoint = userState.getLastActivePoint();
        GPSPoint centroid = userState.calculateCentroid();

        if (firstPoint == null || lastPoint == null || centroid == null) {
            log.debug("Cannot finalize stay - missing required points");
            return null;
        }

        Integer radiusMeters = config.getStaypointRadiusMeters();
        if (radiusMeters != null && FavoriteAreas.keepsPointsOutsideRadius(userState.getActivePoints(), centroid,
                radiusMeters, userState.getFavoriteAreas())) {
            userState.getActiveExplanation().note(TimelineReason.of(TimelineReasons.FAVORITE_AREA));
        }

        var anchorArea = FavoriteAreas.containing(firstPoint, userState.getFavoriteAreas());
        if (anchorArea != null && !anchorArea.preparedGeometry().covers(
                org.github.tess1o.geopulse.shared.geo.GeoUtils.createPoint(centroid.getLongitude(), centroid.getLatitude()))) {
            centroid = firstPoint;
        }
        Duration stayDuration = Duration.between(firstPoint.getTimestamp(), lastPoint.getTimestamp());

        Stay stay = Stay.builder()
                .startTime(firstPoint.getTimestamp())
                .duration(stayDuration)
                .latitude(centroid.getLatitude())
                .longitude(centroid.getLongitude())
                .locationName(null) // Will be populated later
                .favoriteId(null)   // Will be populated later
                .geocodingId(null)  // Will be populated later
                .build();

        return stay;
    }

    /**
     * Populate location data for all stay events using batch resolution.
     * This method processes all stays at once to optimize database queries.
     *
     * @param events list of timeline events (will modify Stay events in place)
     * @param userId user identifier for location resolution
     */
    public void populateStayLocations(List<TimelineEvent> events, UUID userId) {
        populateStayLocations(events, userId, null);
    }

    /**
     * Populate location data for all stay events using batch resolution with progress tracking.
     * <p>
     * Stays are resolved {@link #locationChunkSize} at a time: each chunk is one favorites lookup and one geocoding
     * cache lookup, and only one chunk's coordinates and results are in memory at once. A full rebuild resolves
     * hundreds of thousands of stays, and holding all their lookups together cost about 150 MB.
     *
     * @param events list of timeline events (will modify Stay events in place)
     * @param userId user identifier for location resolution
     * @param jobId  optional job ID for progress tracking
     */
    public void populateStayLocations(List<TimelineEvent> events, UUID userId, UUID jobId) {
        // Extract all stays that need location resolution
        List<Stay> staysToPopulate = new ArrayList<>();
        for (TimelineEvent event : events) {
            if (event instanceof Stay stay && stay.getLocationName() == null) { // Only process unpopulated stays
                staysToPopulate.add(stay);
            }
        }

        if (staysToPopulate.isEmpty()) {
            return;
        }

        log.info("Populating location data for {} stays using batch resolution", staysToPopulate.size());
        long startTime = System.currentTimeMillis();

        LocationPointResolver.LocationBatch batch =
                locationPointResolver.startBatch(userId, jobId, staysToPopulate.size());
        for (int from = 0; from < staysToPopulate.size(); from += locationChunkSize) {
            List<Stay> chunk = staysToPopulate.subList(from, Math.min(from + locationChunkSize, staysToPopulate.size()));
            List<Point> points = new ArrayList<>(chunk.size());
            for (Stay stay : chunk) {
                points.add(GeoUtils.createPoint(stay.getLongitude(), stay.getLatitude()));
            }

            Map<String, LocationResolutionResult> locationResults = locationPointResolver.resolveChunk(batch, points);

            // Populate each stay with its resolved location data
            for (int i = 0; i < chunk.size(); i++) {
                LocationResolutionResult locationResult =
                        locationResults.get(LocationPointResolver.coordinateKey(points.get(i)));
                if (locationResult != null) {
                    updateStayLocation(chunk.get(i), locationResult);
                } else {
                    log.warn("No location result found for stay; using fallback");
                    updateStayLocation(chunk.get(i), LocationResolutionResult.fromGeocoding("Unknown Location", null));
                }
            }
        }
        locationPointResolver.finishBatch(batch);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Populated location data for {} stays in {} ms", staysToPopulate.size(), duration);
    }

    /**
     * Update a Stay object with location resolution results.
     * Stay class is mutable (@Data annotation generates setters).
     */
    private void updateStayLocation(Stay stay, LocationResolutionResult locationResult) {
        stay.setLocationName(locationResult.getLocationName());
        stay.setFavoriteId(locationResult.getFavoriteId());
        stay.setGeocodingId(locationResult.getGeocodingId());

        // Anchor stays matched to favorite points to the favorite coordinates.
        // This prevents map scatter for "Home/Work" style locations.
        if (locationResult.hasAnchoredCoordinates()) {
            stay.setLatitude(locationResult.getAnchorLatitude());
            stay.setLongitude(locationResult.getAnchorLongitude());
        }
    }

    /**
     * Aligns trip start/end pins with the place of the closest adjacent favorite stay, matching the pins the map
     * showed when this was computed at read time. Persisting the result keeps every read path (owner, viewer, AI,
     * export) on the same endpoints.
     */
    public void snapTripEndpointsToAdjacentFavoriteStays(List<TimelineEvent> events) {
        List<Stay> stays = events.stream().filter(Stay.class::isInstance).map(Stay.class::cast).toList();
        List<Trip> trips = events.stream().filter(Trip.class::isInstance).map(Trip.class::cast).toList();
        if (stays.isEmpty() || trips.isEmpty()) {
            return;
        }
        // This runs over the whole regenerated range: index stays and trips once instead of scanning them per trip.
        StayEdges stayEnds = new StayEdges(stays, true);
        StayEdges stayStarts = new StayEdges(stays, false);
        TripCoverage coverage = new TripCoverage(trips);
        for (Trip trip : trips) {
            if (trip.getManualRoute() != null) continue;
            Instant tripStart = trip.getStartTime();
            Instant tripEnd = trip.getEndTime();

            Stay origin = stayEnds.closest(tripStart);
            if (origin != null && origin.getFavoriteId() != null
                    && !coverage.hasAnotherTripBetween(origin.getEndTime(), tripStart, trip)) {
                trip.setStartPoint(withCoordinates(trip.getStartPoint(), origin.getLatitude(),
                        origin.getLongitude()));
            }

            Stay destination = stayStarts.closest(tripEnd);
            if (destination != null && destination.getFavoriteId() != null
                    && !coverage.hasAnotherTripBetween(tripEnd, destination.getStartTime(), trip)) {
                trip.setEndPoint(withCoordinates(trip.getEndPoint(), destination.getLatitude(),
                        destination.getLongitude()));
            }
        }
    }

    /**
     * Stays ordered by one edge: their end, to find the stay closest before an instant, or their start, to find the
     * stay closest after it. Closeness is measured in whole seconds, and among equally close stays the one listed
     * first wins.
     */
    private static final class StayEdges {
        private final boolean before;
        private final Instant[] edges;
        private final int[] order;
        private final List<Stay> stays;

        StayEdges(List<Stay> stays, boolean before) {
            this.before = before;
            this.stays = stays;
            List<Integer> indexes = new ArrayList<>();
            for (int i = 0; i < stays.size(); i++) {
                if (stays.get(i).getStartTime() != null) indexes.add(i);
            }
            indexes.sort(Comparator.comparing((Integer i) -> edge(stays.get(i))).thenComparing(i -> i));
            order = indexes.stream().mapToInt(Integer::intValue).toArray();
            edges = new Instant[order.length];
            for (int i = 0; i < order.length; i++) edges[i] = edge(stays.get(order[i]));
        }

        private Instant edge(Stay stay) {
            return before ? stay.getEndTime() : stay.getStartTime();
        }

        Stay closest(Instant reference) {
            int position;
            int step;
            if (before) {
                position = firstAfter(reference) - 1;
                step = -1;
            } else {
                position = firstAtOrAfter(reference);
                step = 1;
            }
            if (position < 0 || position >= order.length) return null;
            long nearest = gapSeconds(edges[position], reference);
            int best = order[position];
            // The gap only grows away from the reference; ties are the stays at the same whole-second distance.
            for (int i = position + step; i >= 0 && i < order.length && gapSeconds(edges[i], reference) == nearest; i += step) {
                best = Math.min(best, order[i]);
            }
            return stays.get(best);
        }

        private static long gapSeconds(Instant edge, Instant reference) {
            return Math.abs(Duration.between(edge, reference).toSeconds());
        }

        private int firstAfter(Instant reference) {
            int low = 0;
            int high = edges.length;
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (edges[middle].isAfter(reference)) high = middle;
                else low = middle + 1;
            }
            return low;
        }

        private int firstAtOrAfter(Instant reference) {
            int low = 0;
            int high = edges.length;
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (edges[middle].isBefore(reference)) low = middle + 1;
                else high = middle;
            }
            return low;
        }
    }

    /** Trips ordered by start, with the two latest ends among each prefix, to ask whether another trip overlaps. */
    private static final class TripCoverage {
        private final Instant[] starts;
        private final Trip[] latest;
        private final Instant[] latestEnd;
        private final Instant[] secondEnd;

        TripCoverage(List<Trip> trips) {
            List<Trip> sorted = trips.stream().filter(trip -> trip.getStartTime() != null)
                    .sorted(Comparator.comparing(Trip::getStartTime)).toList();
            starts = new Instant[sorted.size()];
            latest = new Trip[sorted.size()];
            latestEnd = new Instant[sorted.size()];
            secondEnd = new Instant[sorted.size()];
            Trip top = null;
            Instant topEnd = null;
            Instant runnerUp = null;
            for (int i = 0; i < sorted.size(); i++) {
                Trip trip = sorted.get(i);
                Instant end = trip.getEndTime();
                if (topEnd == null || end.isAfter(topEnd)) {
                    runnerUp = topEnd;
                    top = trip;
                    topEnd = end;
                } else if (runnerUp == null || end.isAfter(runnerUp)) {
                    runnerUp = end;
                }
                starts[i] = trip.getStartTime();
                latest[i] = top;
                latestEnd[i] = topEnd;
                secondEnd[i] = runnerUp;
            }
        }

        /** Whether a trip other than {@code current} overlaps the open interval between {@code from} and {@code to}. */
        boolean hasAnotherTripBetween(Instant from, Instant to, Trip current) {
            if (from == null || to == null || !to.isAfter(from)) {
                return false;
            }
            int low = 0;
            int high = starts.length;
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (starts[middle].isBefore(to)) low = middle + 1;
                else high = middle;
            }
            if (low == 0) return false;
            Instant end = latest[low - 1] == current ? secondEnd[low - 1] : latestEnd[low - 1]; // NOPMD - identity is intended: current is an element of this coverage's trips
            return end != null && end.isAfter(from);
        }
    }

    private static GPSPoint withCoordinates(GPSPoint original, double latitude, double longitude) {
        if (original == null) {
            return GPSPoint.builder().latitude(latitude).longitude(longitude).build();
        }
        return GPSPoint.builder()
                .id(original.getId())
                .timestamp(original.getTimestamp())
                .latitude(latitude)
                .longitude(longitude)
                .speed(original.getSpeed())
                .accuracy(original.getAccuracy())
                .altitude(original.getAltitude())
                .onWater(original.getOnWater())
                .build();
    }

    /**
     * Finalize a trip event from user state with trip classification.
     * Used by main processing flow.
     *
     * @param userState current user state with active trip points
     * @param config    timeline configuration for trip classification
     * @return finalized trip event
     */
    public Trip finalizeTrip(UserState userState, TimelineConfig config) {
        List<GPSPoint> tripPath = userState.getActivePoints();

        if (tripPath.size() < 2) {
            log.debug("Cannot finalize trip - insufficient path points: {}", tripPath.size());
            return null;
        }

        GPSPoint firstPoint = tripPath.getFirst();
        GPSPoint lastPoint = tripPath.getLast();

        Duration tripDuration = Duration.between(firstPoint.getTimestamp(), lastPoint.getTimestamp());
        double totalDistance = calculateTripDistance(tripPath);
        TripGpsStatistics gpsStatistics = gpsStatisticsCalculator.calculateStatistics(tripPath);
        TripWaterStatistics waterStatistics = calculateWaterStatistics(tripPath, config);
        TripAltitudeStatistics altitudeStatistics = calculateAltitudeStatistics(tripPath, config);
        TripType tripType = travelClassification.classifyTravelType(gpsStatistics, waterStatistics, altitudeStatistics, tripDuration, Double.valueOf(totalDistance).longValue(), config);

        Trip trip = Trip.builder()
                .startTime(firstPoint.getTimestamp())
                .duration(tripDuration)
                .statistics(gpsStatistics)
                .waterStatistics(waterStatistics)
                .altitudeStatistics(altitudeStatistics)
                .startPoint(firstPoint)
                .endPoint(lastPoint)
                .distanceMeters(totalDistance)
                .tripType(tripType)
                .build();

        return trip;
    }

    /**
     * Finalize a trip event from user state with additional endpoint and without classification.
     * Used by data gap detection (classification will happen later in post-processing).
     *
     * @param userState current user state with active trip points
     * @param endPoint  optional additional endpoint to include in the trip
     * @return finalized trip event with unknown type
     */
    public Trip finalizeTripForGap(UserState userState, GPSPoint endPoint, TimelineConfig config) {
        List<GPSPoint> tripPath = userState.getActivePoints();
        if (endPoint != null) {
            // Reserve the appended endpoint up front instead of copying and then growing a full trip array.
            List<GPSPoint> extendedPath = new ArrayList<>(tripPath.size() + 1);
            for (GPSPoint point : tripPath) extendedPath.add(point);
            extendedPath.add(endPoint);
            tripPath = extendedPath;
        }

        if (tripPath.isEmpty()) {
            log.debug("Cannot finalize trip - no path points");
            return null;
        }

        GPSPoint firstPoint = tripPath.getFirst();
        GPSPoint lastPoint = tripPath.getLast();

        Duration tripDuration = Duration.between(firstPoint.getTimestamp(), lastPoint.getTimestamp());
        double totalDistance = calculateTripDistance(tripPath);
        TripGpsStatistics gpsStatistics = gpsStatisticsCalculator.calculateStatistics(tripPath);
        TripWaterStatistics waterStatistics = calculateWaterStatistics(tripPath, config);
        TripAltitudeStatistics altitudeStatistics = calculateAltitudeStatistics(tripPath, config);
        TripType tripType = travelClassification.classifyTravelType(gpsStatistics, waterStatistics, altitudeStatistics, tripDuration, Double.valueOf(totalDistance).longValue(), config);

        return Trip.builder()
                .startTime(firstPoint.getTimestamp())
                .duration(tripDuration)
                .startPoint(firstPoint)
                .endPoint(lastPoint)
                .statistics(gpsStatistics)
                .waterStatistics(waterStatistics)
                .altitudeStatistics(altitudeStatistics)
                .distanceMeters(totalDistance)
                .tripType(tripType)
                .build();
    }

    /**
     * Calculate the total distance of a trip by summing distances between consecutive points.
     *
     * @param tripPath list of GPS points along the trip path
     * @return total distance in meters
     */
    private double calculateTripDistance(List<GPSPoint> tripPath) {
        if (tripPath.size() < 2) {
            return 0.0;
        }

        double totalDistance = 0.0;
        for (int i = 1; i < tripPath.size(); i++) {
            totalDistance += tripPath.get(i - 1).distanceTo(tripPath.get(i));
        }

        return totalDistance;
    }

    private TripAltitudeStatistics calculateAltitudeStatistics(List<GPSPoint> tripPath, TimelineConfig config) {
        if (tripAltitudeStatisticsCalculator == null) {
            return TripAltitudeStatistics.unavailable();
        }
        return tripAltitudeStatisticsCalculator.calculateStatistics(tripPath, config);
    }

    private TripWaterStatistics calculateWaterStatistics(List<GPSPoint> tripPath, TimelineConfig config) {
        if (tripWaterClassificationService == null) {
            return TripWaterStatistics.unavailable();
        }
        return tripWaterClassificationService.calculateStatistics(tripPath, config);
    }
}

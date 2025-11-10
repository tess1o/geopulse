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
import org.locationtech.jts.geom.Point;

import java.time.Duration;
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

    /**
     * Finalize a stay event from user state without location resolution.
     * Location data will be populated later via batch processing.
     * Used by both main processing and data gap detection.
     *
     * @param userState current user state with active stay points
     * @param config    timeline configuration for accuracy validation
     * @return finalized stay event without location data, or null if validation fails
     */
    public Stay finalizeStayWithoutLocation(UserState userState, TimelineConfig config) {
        GPSPoint firstPoint = userState.getFirstActivePoint();
        GPSPoint lastPoint = userState.getLastActivePoint();
        GPSPoint centroid = userState.calculateCentroid();

        if (firstPoint == null || lastPoint == null || centroid == null) {
            log.warn("Cannot finalize stay - missing required points");
            return null;
        }

        // Validate accuracy ratio if enabled
        if (!passesAccuracyRatioValidation(userState.copyActivePoints(), config)) {
            log.debug("Stay rejected due to insufficient accuracy ratio");
            return null;
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
     * This method processes all stays at once to optimize database queries.
     *
     * @param events list of timeline events (will modify Stay events in place)
     * @param userId user identifier for location resolution
     * @param jobId  optional job ID for progress tracking
     */
    public void populateStayLocations(List<TimelineEvent> events, UUID userId, UUID jobId) {
        // Extract all stays that need location resolution
        List<Stay> staysToPopulate = events.stream()
                .filter(event -> event instanceof Stay)
                .map(event -> (Stay) event)
                .filter(stay -> stay.getLocationName() == null) // Only process unpopulated stays
                .collect(java.util.stream.Collectors.toList());

        if (staysToPopulate.isEmpty()) {
            return;
        }

        log.info("Populating location data for {} stays using batch resolution", staysToPopulate.size());
        long startTime = System.currentTimeMillis();

        // Create Points for all stay centroids
        List<Point> stayPoints = staysToPopulate.stream()
                .map(stay -> GeoUtils.createPoint(stay.getLongitude(), stay.getLatitude()))
                .collect(java.util.stream.Collectors.toList());

        // Batch resolve all locations
        Map<String, LocationResolutionResult> locationResults =
                locationPointResolver.resolveLocationsWithReferencesBatch(userId, stayPoints, jobId);

        // Populate each stay with its resolved location data
        for (Stay stay : staysToPopulate) {
            Point point = GeoUtils.createPoint(stay.getLongitude(), stay.getLatitude());
            String coordKey = point.getX() + "," + point.getY();
            LocationResolutionResult locationResult = locationResults.get(coordKey);

            if (locationResult != null) {
                // Update stay with location data (Stay objects should be mutable for this)
                updateStayLocation(stay, locationResult);
            } else {
                log.warn("No location result found for stay at {}, using fallback", coordKey);
                updateStayLocation(stay, LocationResolutionResult.fromGeocoding("Unknown Location", null));
            }
        }

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
        List<GPSPoint> tripPath = userState.copyActivePoints();

        if (tripPath.size() < 2) {
            log.warn("Cannot finalize trip - insufficient path points: {}", tripPath.size());
            return null;
        }

        GPSPoint firstPoint = tripPath.getFirst();
        GPSPoint lastPoint = tripPath.getLast();

        Duration tripDuration = Duration.between(firstPoint.getTimestamp(), lastPoint.getTimestamp());
        double totalDistance = calculateTripDistance(tripPath);
        TripGpsStatistics gpsStatistics = gpsStatisticsCalculator.calculateStatistics(tripPath);
        TripType tripType = travelClassification.classifyTravelType(gpsStatistics, tripDuration, Double.valueOf(totalDistance).longValue(), config);

        Trip trip = Trip.builder()
                .startTime(firstPoint.getTimestamp())
                .duration(tripDuration)
                .statistics(gpsStatistics)
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
        List<GPSPoint> tripPath = userState.copyActivePoints();
        if (endPoint != null) {
            tripPath.add(endPoint);
        }

        if (tripPath.isEmpty()) {
            log.warn("Cannot finalize trip - no path points");
            return null;
        }

        GPSPoint firstPoint = tripPath.getFirst();
        GPSPoint lastPoint = tripPath.getLast();

        Duration tripDuration = Duration.between(firstPoint.getTimestamp(), lastPoint.getTimestamp());
        double totalDistance = calculateTripDistance(tripPath);
        TripGpsStatistics gpsStatistics = gpsStatisticsCalculator.calculateStatistics(tripPath);
        TripType tripType = travelClassification.classifyTravelType(gpsStatistics, tripDuration, Double.valueOf(totalDistance).longValue(), config);

        return Trip.builder()
                .startTime(firstPoint.getTimestamp())
                .duration(tripDuration)
                .startPoint(firstPoint)
                .endPoint(lastPoint)
                .statistics(gpsStatistics)
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

    /**
     * Validate if a cluster of GPS points meets the minimum accuracy ratio requirement.
     * This ensures that a sufficient percentage of points in the cluster have acceptable accuracy.
     *
     * @param points GPS points in the cluster to validate
     * @param config timeline configuration containing accuracy thresholds
     * @return true if cluster passes accuracy ratio validation or if validation is disabled
     */
    private boolean passesAccuracyRatioValidation(List<GPSPoint> points, TimelineConfig config) {
        if (points == null || points.isEmpty()) {
            return false;
        }

        // Skip validation if velocity accuracy checks are disabled
        if (Boolean.FALSE.equals(config.getUseVelocityAccuracy())) {
            return true;
        }

        Double maxAccuracyThreshold = config.getStaypointMaxAccuracyThreshold();
        Double minAccuracyRatio = config.getStaypointMinAccuracyRatio();

        if (maxAccuracyThreshold == null || maxAccuracyThreshold <= 0 ||
                minAccuracyRatio == null || minAccuracyRatio <= 0) {
            return true; // Skip validation if thresholds are not properly configured
        }

        // Calculate accuracy ratio
        long totalPoints = points.size();
        long accuratePoints = points.stream()
                .mapToLong(point -> (point.getAccuracy() <= maxAccuracyThreshold) ? 1 : 0)
                .sum();

        double actualRatio = (double) accuratePoints / totalPoints;
        boolean passes = actualRatio >= minAccuracyRatio;

        if (!passes) {
            log.debug("Accuracy ratio validation failed: {}/{} ({}) < required {}",
                    accuratePoints, totalPoints, actualRatio, minAccuracyRatio);
        }

        return passes;
    }
}
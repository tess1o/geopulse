package org.github.tess1o.geopulse.digest.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.github.tess1o.geopulse.digest.model.DigestReplayPathPoint;
import org.github.tess1o.geopulse.digest.model.DigestReplayTrip;
import org.github.tess1o.geopulse.digest.model.YearlyDigestReplay;
import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.gps.service.simplification.PathSimplificationService;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class DigestReplayService {
    static final int MAX_POINTS_PER_TRIP = 1000;

    private final TimelineTripRepository timelineTripRepository;
    private final GpsPointService gpsPointService;
    private final PathSimplificationService pathSimplificationService;
    private final TimelineConfigurationProvider timelineConfigurationProvider;

    public YearlyDigestReplay getYearlyReplay(UUID userId, int year, String timezone) {
        ZoneId zoneId = ZoneId.of(timezone);
        Instant start = LocalDate.of(year, 1, 1).atStartOfDay(zoneId).toInstant();
        Instant endExclusive = LocalDate.of(year + 1, 1, 1).atStartOfDay(zoneId).toInstant();
        List<TimelineTripEntity> selectedTrips = selectRepresentativeTrips(
                timelineTripRepository.findStartedBetween(userId, start, endExclusive), zoneId);

        return YearlyDigestReplay.builder()
                .year(year)
                .trips(selectedTrips.stream()
                        .map(trip -> toReplayTrip(userId, trip))
                        .filter(java.util.Objects::nonNull)
                        .toList())
                .build();
    }

    static List<TimelineTripEntity> selectRepresentativeTrips(List<TimelineTripEntity> trips, ZoneId zoneId) {
        Comparator<TimelineTripEntity> bestFirst = Comparator
                .comparingLong(TimelineTripEntity::getDistanceMeters).reversed()
                .thenComparing(TimelineTripEntity::getTimestamp)
                .thenComparing(TimelineTripEntity::getId, Comparator.nullsLast(Long::compareTo));
        Map<Integer, TimelineTripEntity> byMonth = new LinkedHashMap<>();

        trips.stream()
                .filter(trip -> trip.getTimestamp() != null)
                .sorted(Comparator.comparing(TimelineTripEntity::getTimestamp))
                .forEach(trip -> {
                    int month = trip.getTimestamp().atZone(zoneId).getMonthValue();
                    byMonth.merge(month, trip, (current, candidate) ->
                            bestFirst.compare(candidate, current) < 0 ? candidate : current);
                });

        return byMonth.values().stream()
                .sorted(Comparator.comparing(TimelineTripEntity::getTimestamp))
                .toList();
    }

    private DigestReplayTrip toReplayTrip(UUID userId, TimelineTripEntity trip) {
        if (!hasValidEndpoints(trip)) {
            return null;
        }

        List<? extends GpsPoint> rawPath = gpsPointService
                .getGpsPointPath(userId, trip.getTimestamp(), trip.getTimestamp().plusSeconds(trip.getTripDuration()))
                .getPoints();
        List<? extends GpsPoint> simplified = pathSimplificationService.simplify(
                rawPath, timelineConfigurationProvider.getConfigurationForUser(userId));
        List<GpsPoint> bounded = capPoints(simplified, MAX_POINTS_PER_TRIP);
        if (!hasTwoDistinctPoints(bounded)) {
            bounded = List.of(endpoint(trip, true), endpoint(trip, false));
        }

        return DigestReplayTrip.builder()
                .id(trip.getId())
                .startTime(trip.getTimestamp())
                .endTime(trip.getTimestamp().plusSeconds(trip.getTripDuration()))
                .tripDuration(trip.getTripDuration())
                .distanceMeters(trip.getDistanceMeters())
                .movementType(trip.getMovementType())
                .startLatitude(trip.getStartPoint().getY())
                .startLongitude(trip.getStartPoint().getX())
                .endLatitude(trip.getEndPoint().getY())
                .endLongitude(trip.getEndPoint().getX())
                .path(bounded.stream().map(DigestReplayService::toPathPoint).toList())
                .build();
    }

    private static boolean hasValidEndpoints(TimelineTripEntity trip) {
        if (trip.getTimestamp() == null || trip.getTripDuration() < 0
                || trip.getStartPoint() == null || trip.getEndPoint() == null) {
            return false;
        }
        double startLat = trip.getStartPoint().getY();
        double startLon = trip.getStartPoint().getX();
        double endLat = trip.getEndPoint().getY();
        double endLon = trip.getEndPoint().getX();
        return validCoordinate(startLat, startLon) && validCoordinate(endLat, endLon)
                && (startLat != endLat || startLon != endLon);
    }

    private static boolean hasTwoDistinctPoints(List<GpsPoint> points) {
        if (points.size() < 2) {
            return false;
        }
        GpsPoint first = points.getFirst();
        return points.stream().anyMatch(point -> point.getLatitude() != first.getLatitude()
                || point.getLongitude() != first.getLongitude());
    }

    private static boolean validCoordinate(double latitude, double longitude) {
        return Double.isFinite(latitude) && latitude >= -90 && latitude <= 90
                && Double.isFinite(longitude) && longitude >= -180 && longitude <= 180;
    }

    private static GpsPoint endpoint(TimelineTripEntity trip, boolean start) {
        var point = start ? trip.getStartPoint() : trip.getEndPoint();
        Instant timestamp = start ? trip.getTimestamp() : trip.getTimestamp().plusSeconds(trip.getTripDuration());
        return new ReplayEndpoint(point.getY(), point.getX(), timestamp);
    }

    private static List<GpsPoint> capPoints(List<? extends GpsPoint> points, int limit) {
        List<? extends GpsPoint> safePoints = points == null ? List.of() : points.stream()
                .filter(point -> validCoordinate(point.getLatitude(), point.getLongitude()))
                .toList();
        if (safePoints.size() <= limit) {
            return new ArrayList<>(safePoints);
        }

        // shortcut: replay responses cap each route evenly at 1000 points; add geometry-aware resampling if visible artifacts appear.
        List<GpsPoint> result = new ArrayList<>(limit);
        for (int index = 0; index < limit; index++) {
            int sourceIndex = (int) Math.round(index * (safePoints.size() - 1d) / (limit - 1d));
            result.add(safePoints.get(sourceIndex));
        }
        return result;
    }

    private static DigestReplayPathPoint toPathPoint(GpsPoint point) {
        Double altitude = point instanceof GpsPointPathPointDTO dto ? dto.getAltitude() : null;
        return DigestReplayPathPoint.builder()
                .latitude(point.getLatitude())
                .longitude(point.getLongitude())
                .altitude(altitude)
                .timestamp(point.getTimestamp())
                .build();
    }

    private record ReplayEndpoint(double latitude, double longitude, Instant timestamp) implements GpsPoint {
        @Override public double getLatitude() { return latitude; }
        @Override public double getLongitude() { return longitude; }
        @Override public Instant getTimestamp() { return timestamp; }
    }
}

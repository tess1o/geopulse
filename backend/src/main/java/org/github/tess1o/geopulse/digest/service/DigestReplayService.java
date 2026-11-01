package org.github.tess1o.geopulse.digest.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.github.tess1o.geopulse.digest.model.DigestReplay;
import org.github.tess1o.geopulse.digest.model.DigestReplayPathPoint;
import org.github.tess1o.geopulse.digest.model.DigestReplayTrip;
import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.gps.service.simplification.PathSimplificationService;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository.TripStartAndDistance;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.trips.repository.TripRepository;
import org.locationtech.jts.geom.Coordinate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

@ApplicationScoped
@RequiredArgsConstructor
public class DigestReplayService {
    static final int MAX_POINTS_PER_TRIP = 1000;
    /** A stay that ended (or began) further than this from a trip is not where the trip started (or ended). */
    static final Duration MAX_STAY_GAP = Duration.ofHours(12);
    /** A monthly replay has one chapter per seven days of the month: 1–7, 8–14, 15–21, 22–28 and 29–31. */
    static final int DAYS_PER_MONTHLY_CHAPTER = 7;

    private final TimelineTripRepository timelineTripRepository;
    private final TimelineStayRepository timelineStayRepository;
    private final TripRepository tripRepository;
    private final GpsPointService gpsPointService;
    private final PathSimplificationService pathSimplificationService;
    private final TimelineConfigurationProvider timelineConfigurationProvider;

    public DigestReplay getYearlyReplay(UUID userId, int year, String timezone) {
        LocalDate firstDay = LocalDate.of(year, 1, 1);
        return DigestReplay.builder()
                .year(year)
                .trips(buildChapters(userId, firstDay, firstDay.plusYears(1), ZoneId.of(timezone),
                        LocalDate::getMonthValue))
                .build();
    }

    public DigestReplay getMonthlyReplay(UUID userId, int year, int month, String timezone) {
        LocalDate firstDay = LocalDate.of(year, month, 1);
        return DigestReplay.builder()
                .year(year)
                .month(month)
                .trips(buildChapters(userId, firstDay, firstDay.plusMonths(1), ZoneId.of(timezone),
                        date -> (date.getDayOfMonth() - 1) / DAYS_PER_MONTHLY_CHAPTER))
                .build();
    }

    private List<DigestReplayTrip> buildChapters(UUID userId, LocalDate firstDay, LocalDate endDayExclusive,
                                                 ZoneId zoneId, ToIntFunction<LocalDate> chapterOfDay) {
        Instant start = firstDay.atStartOfDay(zoneId).toInstant();
        Instant endExclusive = endDayExclusive.atStartOfDay(zoneId).toInstant();
        List<NamedTrip> namedTrips = tripRepository.findTakenStartingBetween(userId, start, endExclusive).stream()
                .map(NamedTrip::of)
                .toList();
        List<Chapter> chapters = selectChapters(
                timelineTripRepository.findStartsAndDistances(userId, start, endExclusive),
                namedTrips,
                instant -> chapterOfDay.applyAsInt(instant.atZone(zoneId).toLocalDate()));

        // Only the chosen trips are loaded in full, geometry included.
        Map<Long, TimelineTripEntity> singleTrips = timelineTripRepository
                .findByUserAndIds(userId, chapters.stream().map(Chapter::tripId).filter(Objects::nonNull).toList())
                .stream()
                .collect(Collectors.toMap(TimelineTripEntity::getId, Function.identity()));

        return chapters.stream()
                .map(chapter -> chapter.namedTrip() != null
                        ? toNamedReplayTrip(userId, chapter.namedTrip())
                        : toReplayTrip(userId, singleTrips.get(chapter.tripId())))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * One chapter per period (a month of a year, or seven days of a month), in date order. A named trip that starts
     * in the period and has timeline trips becomes that period's chapter; otherwise the period's longest timeline
     * trip does, leaving out trips that belong to any named trip.
     */
    static List<Chapter> selectChapters(List<TripStartAndDistance> trips, List<NamedTrip> namedTrips,
                                        Function<Instant, Integer> chapterOf) {
        Map<Integer, Chapter> byChapter = new HashMap<>();
        namedTrips.stream()
                .sorted(Comparator.comparing(NamedTrip::start).thenComparing(NamedTrip::id, Comparator.nullsLast(Long::compareTo)))
                .filter(named -> trips.stream().anyMatch(trip -> named.contains(trip.timestamp())))
                .forEach(named -> byChapter.putIfAbsent(chapterOf.apply(named.start()),
                        new Chapter(named.start(), null, named)));

        Comparator<TripStartAndDistance> bestFirst = Comparator
                .comparingLong(TripStartAndDistance::distanceMeters).reversed()
                .thenComparing(TripStartAndDistance::timestamp)
                .thenComparing(TripStartAndDistance::id, Comparator.nullsLast(Long::compareTo));
        trips.stream()
                .filter(trip -> trip.timestamp() != null)
                .filter(trip -> namedTrips.stream().noneMatch(named -> named.contains(trip.timestamp())))
                .sorted(bestFirst)
                .forEach(trip -> byChapter.putIfAbsent(chapterOf.apply(trip.timestamp()),
                        new Chapter(trip.timestamp(), trip.id(), null)));

        return byChapter.values().stream()
                .sorted(Comparator.comparing(Chapter::startsAt))
                .toList();
    }

    private DigestReplayTrip toReplayTrip(UUID userId, TimelineTripEntity trip) {
        if (trip == null || !hasValidEndpoints(trip)) {
            return null;
        }

        Instant endTime = trip.getTimestamp().plusSeconds(trip.getTripDuration());
        List<? extends GpsPoint> rawPath = gpsPointService
                .getGpsPointPath(userId, trip.getTimestamp(), endTime)
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
                .endTime(endTime)
                .tripDuration(trip.getTripDuration())
                .distanceMeters(trip.getDistanceMeters())
                .movementType(trip.getMovementType())
                .startLatitude(trip.getStartPoint().getY())
                .startLongitude(trip.getStartPoint().getX())
                .endLatitude(trip.getEndPoint().getY())
                .endLongitude(trip.getEndPoint().getX())
                .path(bounded.stream().map(DigestReplayService::toPathPoint).toList())
                .legCount(1)
                .originName(stayNameBefore(userId, trip.getTimestamp()))
                .destinationName(stayNameAfter(userId, endTime))
                .build();
    }

    /**
     * The named trip as one route: its timeline trips' stored paths joined in order. The joined route spans days
     * spent at places, so it carries no timestamps and the film paces it by distance.
     */
    private DigestReplayTrip toNamedReplayTrip(UUID userId, NamedTrip namedTrip) {
        List<TimelineTripEntity> legs = timelineTripRepository
                .findStartedBetween(userId, namedTrip.start(), namedTrip.end()).stream()
                .filter(DigestReplayService::hasValidEndpoints)
                .toList();
        if (legs.isEmpty()) {
            return null;
        }

        List<GpsPoint> route = new ArrayList<>();
        legs.forEach(leg -> route.addAll(legRoute(leg)));
        List<GpsPoint> bounded = capPoints(route, MAX_POINTS_PER_TRIP);
        if (!hasTwoDistinctPoints(bounded)) {
            return null;
        }

        TimelineTripEntity longest = legs.stream()
                .max(Comparator.comparingLong(TimelineTripEntity::getDistanceMeters))
                .orElseThrow();
        GpsPoint first = bounded.getFirst();
        GpsPoint last = bounded.getLast();
        return DigestReplayTrip.builder()
                .id(longest.getId())
                .name(namedTrip.name())
                .legCount(legs.size())
                .startTime(namedTrip.start())
                .endTime(namedTrip.end())
                .tripDuration(Duration.between(namedTrip.start(), namedTrip.end()).toSeconds())
                .distanceMeters(legs.stream().mapToLong(TimelineTripEntity::getDistanceMeters).sum())
                .movementType(longest.getMovementType())
                .startLatitude(first.getLatitude())
                .startLongitude(first.getLongitude())
                .endLatitude(last.getLatitude())
                .endLongitude(last.getLongitude())
                .path(bounded.stream().map(DigestReplayService::toPathPoint).toList())
                .build();
    }

    private static List<GpsPoint> legRoute(TimelineTripEntity leg) {
        if (leg.getPath() != null && leg.getPath().getNumPoints() >= 2) {
            List<GpsPoint> points = new ArrayList<>();
            for (Coordinate coordinate : leg.getPath().getCoordinates()) {
                points.add(new ReplayEndpoint(coordinate.getY(), coordinate.getX(), null));
            }
            return points;
        }
        return List.of(
                new ReplayEndpoint(leg.getStartPoint().getY(), leg.getStartPoint().getX(), null),
                new ReplayEndpoint(leg.getEndPoint().getY(), leg.getEndPoint().getX(), null));
    }

    private String stayNameBefore(UUID userId, Instant tripStart) {
        Instant earliestEnd = tripStart.minus(MAX_STAY_GAP);
        return timelineStayRepository.findLatestByUserIdAtOrBeforeTimestamp(userId, tripStart)
                .filter(stay -> !stay.getTimestamp().plusSeconds(stay.getStayDuration()).isBefore(earliestEnd))
                .map(TimelineStayEntity::getLocationName)
                .orElse(null);
    }

    private String stayNameAfter(UUID userId, Instant tripEnd) {
        Instant latestStart = tripEnd.plus(MAX_STAY_GAP);
        return timelineStayRepository.findEarliestByUserIdAtOrAfterTimestamp(userId, tripEnd)
                .filter(stay -> !stay.getTimestamp().isAfter(latestStart))
                .map(TimelineStayEntity::getLocationName)
                .orElse(null);
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

    /** A user's trip from the Trips feature, as far as the replay needs it. */
    record NamedTrip(Long id, String name, Instant start, Instant end) {
        static NamedTrip of(TripEntity trip) {
            return new NamedTrip(trip.getId(), trip.getName(), trip.getStartTime(), trip.getEndTime());
        }

        boolean contains(Instant instant) {
            return !instant.isBefore(start) && instant.isBefore(end);
        }
    }

    /** A chapter of the film: a single timeline trip ({@code tripId}) or a named trip. */
    record Chapter(Instant startsAt, Long tripId, NamedTrip namedTrip) {
    }

    private record ReplayEndpoint(double latitude, double longitude, Instant timestamp) implements GpsPoint {
        @Override public double getLatitude() { return latitude; }
        @Override public double getLongitude() { return longitude; }
        @Override public Instant getTimestamp() { return timestamp; }
    }
}

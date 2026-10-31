package org.github.tess1o.geopulse.digest.service;

import org.github.tess1o.geopulse.digest.service.DigestReplayService.Chapter;
import org.github.tess1o.geopulse.digest.service.DigestReplayService.NamedTrip;
import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.gps.service.simplification.PathSimplificationService;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository.TripStartAndDistance;
import org.github.tess1o.geopulse.trips.repository.TripRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class DigestReplayServiceTest {

    private static final Function<Instant, Integer> KYIV_MONTH =
            instant -> instant.atZone(ZoneId.of("Europe/Kyiv")).getMonthValue();
    private static final Function<Instant, Integer> UTC_MONTH =
            instant -> instant.atZone(ZoneId.of("UTC")).getMonthValue();

    @Test
    void selectsLongestTripPerUserLocalMonthWithDeterministicTies() {
        var shorterJanuary = candidate(1, "2026-01-10T12:00:00Z", 100);
        var earlierLongestJanuary = candidate(2, "2026-01-11T12:00:00Z", 500);
        var laterLongestJanuary = candidate(3, "2026-01-20T12:00:00Z", 500);
        var localFebruary = candidate(4, "2026-01-31T22:30:00Z", 300);

        var chapters = DigestReplayService.selectChapters(
                List.of(shorterJanuary, laterLongestJanuary, localFebruary, earlierLongestJanuary), List.of(), KYIV_MONTH);

        assertEquals(List.of(2L, 4L), chapters.stream().map(Chapter::tripId).toList());
    }

    @Test
    void breaksExactTiesById() {
        var chapters = DigestReplayService.selectChapters(
                List.of(candidate(8, "2026-06-10T12:00:00Z", 500), candidate(7, "2026-06-10T12:00:00Z", 500)),
                List.of(), UTC_MONTH);

        assertEquals(7L, chapters.getFirst().tripId());
    }

    @Test
    void aNamedTripIsItsMonthsChapterAndItsLegsAreNotPickedForAnotherMonth() {
        var lisbon = new NamedTrip(40L, "Lisbon", Instant.parse("2026-03-28T00:00:00Z"), Instant.parse("2026-04-04T00:00:00Z"));
        var flightOut = candidate(1, "2026-03-28T08:00:00Z", 3_000_000);
        var shortMarchWalk = candidate(2, "2026-03-05T08:00:00Z", 900);
        var flightBack = candidate(3, "2026-04-03T18:00:00Z", 3_000_000); // April, but part of the Lisbon trip
        var aprilCommute = candidate(4, "2026-04-10T08:00:00Z", 12_000);

        var chapters = DigestReplayService.selectChapters(
                List.of(flightOut, shortMarchWalk, flightBack, aprilCommute), List.of(lisbon), UTC_MONTH);

        assertEquals(2, chapters.size());
        assertEquals(lisbon, chapters.get(0).namedTrip());
        assertNull(chapters.get(0).tripId());
        assertEquals(4L, chapters.get(1).tripId());
    }

    @Test
    void ignoresANamedTripWithoutTimelineTripsAndKeepsTheFirstNamedTripOfAMonth() {
        var empty = new NamedTrip(1L, "Never tracked", Instant.parse("2026-05-01T00:00:00Z"), Instant.parse("2026-05-03T00:00:00Z"));
        var first = new NamedTrip(2L, "Carpathians", Instant.parse("2026-05-10T00:00:00Z"), Instant.parse("2026-05-12T00:00:00Z"));
        var second = new NamedTrip(3L, "Odesa", Instant.parse("2026-05-20T00:00:00Z"), Instant.parse("2026-05-22T00:00:00Z"));

        var chapters = DigestReplayService.selectChapters(
                List.of(candidate(1, "2026-05-10T09:00:00Z", 200_000), candidate(2, "2026-05-20T09:00:00Z", 450_000)),
                List.of(second, empty, first), UTC_MONTH);

        assertEquals(1, chapters.size());
        assertEquals(first, chapters.getFirst().namedTrip());
    }

    @Test
    void groupsByTheGivenChapterFunction() {
        Function<Instant, Integer> week = instant -> (instant.atZone(ZoneId.of("UTC")).getDayOfMonth() - 1) / 7;

        var chapters = DigestReplayService.selectChapters(List.of(
                candidate(1, "2026-03-02T10:00:00Z", 100),
                candidate(2, "2026-03-07T10:00:00Z", 300),
                candidate(3, "2026-03-08T10:00:00Z", 50),
                candidate(4, "2026-03-31T10:00:00Z", 10)), List.of(), week);

        assertEquals(List.of(2L, 3L, 4L), chapters.stream().map(Chapter::tripId).toList());
    }

    @Test
    void loadsOnlySelectedUserTripAndFallsBackToEndpoints() {
        UUID userId = UUID.randomUUID();
        var repository = mock(TimelineTripRepository.class);
        var gpsPointService = mock(GpsPointService.class);
        var simplificationService = mock(PathSimplificationService.class);
        var configurationProvider = mock(TimelineConfigurationProvider.class);
        var config = mock(TimelineConfig.class);
        var trip = trip(10, "2026-03-02T08:00:00Z", 800, 3_600);
        Instant from = Instant.parse("2025-12-31T22:00:00Z");
        Instant to = Instant.parse("2026-12-31T22:00:00Z");
        when(repository.findStartsAndDistances(userId, from, to))
                .thenReturn(List.of(candidate(10, "2026-03-02T08:00:00Z", 800)));
        when(repository.findByUserAndIds(userId, List.of(10L))).thenReturn(List.of(trip));
        when(gpsPointService.getGpsPointPath(userId, trip.getTimestamp(), trip.getTimestamp().plusSeconds(3_600)))
                .thenReturn(new GpsPointPathDTO(userId, List.of()));
        when(configurationProvider.getConfigurationForUser(userId)).thenReturn(config);
        when(simplificationService.simplify(anyList(), any())).thenReturn(List.of());

        var replay = service(repository, gpsPointService, simplificationService, configurationProvider)
                .getYearlyReplay(userId, 2026, "Europe/Kyiv");

        assertEquals(1, replay.getTrips().size());
        assertNull(replay.getMonth());
        assertEquals(2, replay.getTrips().getFirst().getPath().size());
        assertEquals(50.45, replay.getTrips().getFirst().getPath().getFirst().getLatitude());
        assertEquals(24.03, replay.getTrips().getFirst().getPath().getLast().getLongitude());
        verify(gpsPointService).getGpsPointPath(userId, trip.getTimestamp(), trip.getTimestamp().plusSeconds(3_600));
    }

    @Test
    void fallsBackToEndpointsWhenEveryGpsPointIsTheSamePlace() {
        UUID userId = UUID.randomUUID();
        var repository = mock(TimelineTripRepository.class);
        var gpsPointService = mock(GpsPointService.class);
        var simplificationService = mock(PathSimplificationService.class);
        var configurationProvider = mock(TimelineConfigurationProvider.class);
        var trip = trip(11, "2026-05-02T08:00:00Z", 800, 600);
        var stuck = new GpsPointPathPointDTO();
        stuck.setLatitude(50.0);
        stuck.setLongitude(30.0);
        stuck.setTimestamp(trip.getTimestamp());
        when(repository.findStartsAndDistances(any(), any(), any()))
                .thenReturn(List.of(candidate(11, "2026-05-02T08:00:00Z", 800)));
        when(repository.findByUserAndIds(any(), anyList())).thenReturn(List.of(trip));
        when(gpsPointService.getGpsPointPath(any(), any(), any())).thenReturn(new GpsPointPathDTO(userId, List.of()));
        when(simplificationService.simplify(anyList(), any())).thenAnswer(invocation -> List.of(stuck, stuck, stuck));

        var replay = service(repository, gpsPointService, simplificationService, configurationProvider)
                .getYearlyReplay(userId, 2026, "UTC");

        var path = replay.getTrips().getFirst().getPath();
        assertEquals(2, path.size());
        assertEquals(50.45, path.getFirst().getLatitude());
        assertEquals(49.84, path.getLast().getLatitude());
    }

    private static DigestReplayService service(TimelineTripRepository repository, GpsPointService gpsPointService,
                                               PathSimplificationService simplificationService,
                                               TimelineConfigurationProvider configurationProvider) {
        var stayRepository = mock(TimelineStayRepository.class);
        when(stayRepository.findLatestByUserIdAtOrBeforeTimestamp(any(), any())).thenReturn(Optional.empty());
        when(stayRepository.findEarliestByUserIdAtOrAfterTimestamp(any(), any())).thenReturn(Optional.empty());
        return new DigestReplayService(repository, stayRepository, mock(TripRepository.class), gpsPointService,
                simplificationService, configurationProvider);
    }

    private static TripStartAndDistance candidate(long id, String timestamp, long distanceMeters) {
        return new TripStartAndDistance(id, Instant.parse(timestamp), distanceMeters);
    }

    private static TimelineTripEntity trip(long id, String timestamp, long distanceMeters, long durationSeconds) {
        var geometryFactory = new GeometryFactory();
        return TimelineTripEntity.builder()
                .id(id)
                .timestamp(Instant.parse(timestamp))
                .distanceMeters(distanceMeters)
                .tripDuration(durationSeconds)
                .startPoint(geometryFactory.createPoint(new Coordinate(30.52, 50.45)))
                .endPoint(geometryFactory.createPoint(new Coordinate(24.03, 49.84)))
                .build();
    }
}

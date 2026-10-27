package org.github.tess1o.geopulse.digest.service;

import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.gps.service.simplification.PathSimplificationService;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class DigestReplayServiceTest {

    @Test
    void selectsLongestTripPerUserLocalMonthWithDeterministicTies() {
        var shorterJanuary = trip(1, "2026-01-10T12:00:00Z", 100);
        var earlierLongestJanuary = trip(2, "2026-01-11T12:00:00Z", 500);
        var laterLongestJanuary = trip(3, "2026-01-20T12:00:00Z", 500);
        var localFebruary = trip(4, "2026-01-31T22:30:00Z", 300);

        var selected = DigestReplayService.selectRepresentativeTrips(
                List.of(shorterJanuary, laterLongestJanuary, localFebruary, earlierLongestJanuary),
                ZoneId.of("Europe/Kyiv"));

        assertEquals(List.of(2L, 4L), selected.stream().map(TimelineTripEntity::getId).toList());
    }

    @Test
    void breaksExactTiesById() {
        var first = trip(8, "2026-06-10T12:00:00Z", 500);
        var second = trip(7, "2026-06-10T12:00:00Z", 500);

        var selected = DigestReplayService.selectRepresentativeTrips(
                List.of(first, second), ZoneId.of("UTC"));

        assertEquals(7L, selected.getFirst().getId());
    }

    @Test
    void loadsOnlySelectedUserTripAndFallsBackToEndpoints() {
        UUID userId = UUID.randomUUID();
        var repository = mock(TimelineTripRepository.class);
        var gpsPointService = mock(GpsPointService.class);
        var simplificationService = mock(PathSimplificationService.class);
        var configurationProvider = mock(TimelineConfigurationProvider.class);
        var config = mock(TimelineConfig.class);
        var trip = trip(10, "2026-03-02T08:00:00Z", 800);
        var geometryFactory = new GeometryFactory();
        trip.setTripDuration(3_600);
        trip.setStartPoint(geometryFactory.createPoint(new Coordinate(30.52, 50.45)));
        trip.setEndPoint(geometryFactory.createPoint(new Coordinate(24.03, 49.84)));
        when(repository.findStartedBetween(userId,
                Instant.parse("2025-12-31T22:00:00Z"), Instant.parse("2026-12-31T22:00:00Z")))
                .thenReturn(List.of(trip));
        when(gpsPointService.getGpsPointPath(userId, trip.getTimestamp(), trip.getTimestamp().plusSeconds(3_600)))
                .thenReturn(new GpsPointPathDTO(userId, List.of()));
        when(configurationProvider.getConfigurationForUser(userId)).thenReturn(config);
        when(simplificationService.simplify(anyList(), any())).thenReturn(List.of());

        var service = new DigestReplayService(repository, gpsPointService, simplificationService, configurationProvider);
        var replay = service.getYearlyReplay(userId, 2026, "Europe/Kyiv");

        assertEquals(1, replay.getTrips().size());
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
        var trip = trip(11, "2026-05-02T08:00:00Z", 800);
        var geometryFactory = new GeometryFactory();
        trip.setTripDuration(600);
        trip.setStartPoint(geometryFactory.createPoint(new Coordinate(30.52, 50.45)));
        trip.setEndPoint(geometryFactory.createPoint(new Coordinate(24.03, 49.84)));
        var stuck = new GpsPointPathPointDTO();
        stuck.setLatitude(50.0);
        stuck.setLongitude(30.0);
        stuck.setTimestamp(trip.getTimestamp());
        when(repository.findStartedBetween(any(), any(), any())).thenReturn(List.of(trip));
        when(gpsPointService.getGpsPointPath(any(), any(), any())).thenReturn(new GpsPointPathDTO(userId, List.of()));
        when(simplificationService.simplify(anyList(), any())).thenAnswer(invocation -> List.of(stuck, stuck, stuck));

        var replay = new DigestReplayService(repository, gpsPointService, simplificationService, configurationProvider)
                .getYearlyReplay(userId, 2026, "UTC");

        var path = replay.getTrips().getFirst().getPath();
        assertEquals(2, path.size());
        assertEquals(50.45, path.getFirst().getLatitude());
        assertEquals(49.84, path.getLast().getLatitude());
    }

    private static TimelineTripEntity trip(long id, String timestamp, long distanceMeters) {
        return TimelineTripEntity.builder()
                .id(id)
                .timestamp(Instant.parse(timestamp))
                .distanceMeters(distanceMeters)
                .build();
    }
}

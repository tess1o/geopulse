package org.github.tess1o.geopulse.mapmatching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.mapmatching.dto.MapMatchingTripResolutionDTO;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingStatus;
import org.github.tess1o.geopulse.mapmatching.model.TimelineTripPathMatchEntity;
import org.github.tess1o.geopulse.mapmatching.repository.TimelineTripPathMatchRepository;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class MapMatchingServiceFailureHandlingTest {

    @Mock MapMatchingConfiguration configuration;
    @Mock TimelineTripPathMatchRepository matchRepository;
    @Mock TimelineTripRepository tripRepository;
    @Mock GpsPointRepository gpsPointRepository;
    @Mock TimelineConfigurationProvider timelineConfigurationProvider;
    @Mock MapMatchingHashService hashService;
    @Mock MapMatchingProfileResolver profileResolver;
    @Mock ValhallaMapMatchingProvider valhallaProvider;
    @Mock UserRepository userRepository;

    private MapMatchingService service;

    @BeforeEach
    void setUp() {
        service = new MapMatchingService(configuration, matchRepository, tripRepository, gpsPointRepository,
                timelineConfigurationProvider, hashService, profileResolver, valhallaProvider,
                userRepository, new ObjectMapper());
    }

    @Test
    void terminalValhalla400FailsTargetWithoutRetry() {
        TimelineTripPathMatchEntity target = processFailure(new ValhallaHttpException(400,
                "{\"error_code\":443,\"error\":\"Exact route match algorithm failed to find path\"}", null));

        verify(matchRepository).markFailed(eq(target.getId()), contains("HTTP 400"));
        verify(matchRepository, never()).markFailedOrRetry(anyLong(), anyString(), anyInt());
    }

    @Test
    void transientValhallaFailureKeepsRetryPolicy() {
        when(configuration.getMaxAttempts()).thenReturn(3);
        TimelineTripPathMatchEntity target = processFailure(new ValhallaHttpException(503,
                "{\"error\":\"Service unavailable\"}", null));

        verify(matchRepository).markFailedOrRetry(eq(target.getId()), contains("HTTP 503"), eq(3));
        verify(matchRepository, never()).markFailed(anyLong(), anyString());
    }

    @Test
    void terminalFailureStatusHasNoRetryDelay() {
        UUID userId = UUID.randomUUID();
        TimelineTripEntity trip = mock(TimelineTripEntity.class);
        TimelineTripPathMatchEntity target = mock(TimelineTripPathMatchEntity.class);
        when(trip.getId()).thenReturn(7854179L);
        when(target.getTrip()).thenReturn(trip);
        when(target.getId()).thenReturn(2306L);
        when(target.getStatus()).thenReturn(MapMatchingStatus.FAILED);
        when(target.getLastError()).thenReturn("Valhalla trace_route failed with HTTP 400");
        when(matchRepository.findOwnedTargets(userId, List.of(2306L))).thenReturn(List.of(target));

        MapMatchingTripResolutionDTO result = service.status(userId, List.of(2306L)).getFirst();

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getRetryAt()).isNull();
        assertThat(result.getPollAfterMs()).isZero();
        assertThat(result.getSegments()).isNull();
    }

    @Test
    void resolveReusesAndReattachesExistingCacheRowForRegeneratedTrip() {
        UUID userId = UUID.randomUUID();
        UserEntity user = mock(UserEntity.class);
        TimelineTripEntity regeneratedTrip = mock(TimelineTripEntity.class);
        TimelineTripPathMatchEntity existing = TimelineTripPathMatchEntity.builder()
                .id(4400L)
                .user(user)
                .provider("valhalla")
                .profile("auto")
                .status(MapMatchingStatus.MATCHED)
                .matchedSegmentsJson("[[{\"latitude\":50.1,\"longitude\":30.1}]]")
                .build();
        Instant start = Instant.parse("2026-07-02T10:00:00Z");

        when(configuration.isEnabled()).thenReturn(true);
        when(configuration.provider()).thenReturn("valhalla");
        when(configuration.valhallaConfigured()).thenReturn(true);
        when(configuration.getMaxTripDurationHours()).thenReturn(24);
        when(configuration.configHashSource()).thenReturn("algorithm=v3|valhalla");
        when(userRepository.findById(userId)).thenReturn(user);
        when(user.getTimelineDisplayMapMatchingEnabled()).thenReturn(true);
        when(user.getId()).thenReturn(userId);
        when(timelineConfigurationProvider.getConfigurationForUser(userId))
                .thenReturn(TimelineConfig.builder().useVelocityAccuracy(false).build());
        when(tripRepository.findByIdOptional(9001L)).thenReturn(Optional.of(regeneratedTrip));
        when(regeneratedTrip.getId()).thenReturn(9001L);
        when(regeneratedTrip.getUser()).thenReturn(user);
        when(regeneratedTrip.getTimestamp()).thenReturn(start);
        when(regeneratedTrip.getTripDuration()).thenReturn(120L);
        when(regeneratedTrip.getMovementType()).thenReturn("CAR");
        when(gpsPointRepository.findEligibleByUserIdAndTimePeriod(userId, start, start.plusSeconds(120), null))
                .thenReturn(List.of(point(start), point(start.plusSeconds(60))));
        when(profileResolver.resolveProfile("CAR")).thenReturn("auto");
        when(hashService.configHash("algorithm=v3|valhalla|auto")).thenReturn("config-hash");
        when(hashService.inputHash(anyList(), isNull())).thenReturn("input-hash");
        when(matchRepository.findCurrent(userId, "valhalla", "auto", "config-hash", "input-hash"))
                .thenReturn(Optional.of(existing));

        MapMatchingTripResolutionDTO result = service.resolve(userId, List.of(9001L)).getTrips().getFirst();

        assertThat(result.getTripId()).isEqualTo(9001L);
        assertThat(result.getTargetId()).isEqualTo(4400L);
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        verify(matchRepository).attachToTrip(existing, regeneratedTrip, org.github.tess1o.geopulse.mapmatching.model.MapMatchingSource.ON_DEMAND);
        verify(matchRepository, never()).enqueueIfMissing(any(), any(), anyString(), anyString(), anyString(), anyString(), any());
        verifyNoInteractions(valhallaProvider);
    }

    private TimelineTripPathMatchEntity processFailure(RuntimeException failure) {
        UUID userId = UUID.randomUUID();
        UserEntity user = mock(UserEntity.class);
        TimelineTripEntity trip = mock(TimelineTripEntity.class);
        TimelineTripPathMatchEntity target = TimelineTripPathMatchEntity.builder()
                .id(2306L)
                .trip(trip)
                .provider("valhalla")
                .profile("pedestrian")
                .build();
        Instant start = Instant.parse("2026-07-02T10:00:00Z");
        GpsPointEntity first = point(start);
        GpsPointEntity second = point(start.plusSeconds(60));

        when(user.getId()).thenReturn(userId);
        when(trip.getUser()).thenReturn(user);
        when(trip.getTimestamp()).thenReturn(start);
        when(trip.getTripDuration()).thenReturn(60L);
        when(gpsPointRepository.findEligibleByUserIdAndTimePeriod(userId, start, start.plusSeconds(60), null))
                .thenReturn(List.of(first, second));
        when(configuration.getMaxTripDurationHours()).thenReturn(24);
        when(configuration.getMaxInputPoints()).thenReturn(100);
        when(valhallaProvider.matchSegments(anyList(), eq("pedestrian"))).thenThrow(failure);

        Map<UUID, Double> accuracyCache = new HashMap<>();
        accuracyCache.put(userId, null);
        service.processTargets(List.of(target), accuracyCache);
        return target;
    }

    private GpsPointEntity point(Instant timestamp) {
        GpsPointEntity point = new GpsPointEntity();
        point.setTimestamp(timestamp);
        return point;
    }
}

package org.github.tess1o.geopulse.gps.service;

import jakarta.persistence.EntityManager;
import jakarta.ws.rs.ForbiddenException;
import org.github.tess1o.geopulse.geofencing.service.GeofenceEvaluationService;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.model.RawGpsPointLocationDTO;
import org.github.tess1o.geopulse.gps.model.RawGpsPointMapResponseDTO;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gps.service.filter.GpsDataFilteringService;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.shared.service.LocationPointResolver;
import org.github.tess1o.geopulse.shared.service.LocationResolutionResult;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.trips.GpsPointEnvironmentService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
class GpsPointServiceRawMapPointsTest {

    @Test
    void getRawGpsMapPoints_returnsLimitedLightweightPointsInRepositoryOrder() {
        UUID userId = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-08T00:00:00Z");
        Instant end = Instant.parse("2026-07-09T00:00:00Z");

        GpsPointRepository repository = mock(GpsPointRepository.class);
        when(repository.countByUserIdAndTimePeriod(userId, start, end)).thenReturn(3L);
        when(repository.findMapPointsByUserIdAndTimePeriod(userId, start, end, 2)).thenReturn(List.of(
                gpsPoint(11L, userId, "2026-07-08T10:00:00Z", 1.327946, 103.804579),
                gpsPoint(12L, userId, "2026-07-08T10:01:00Z", 1.328000, 103.805000)
        ));

        RawGpsPointMapResponseDTO response = createService(repository, mock(LocationPointResolver.class))
                .getRawGpsMapPoints(userId, start, end, 2);

        assertThat(response.getTotalCount()).isEqualTo(3);
        assertThat(response.getReturnedCount()).isEqualTo(2);
        assertThat(response.getLimit()).isEqualTo(2);
        assertThat(response.isLimited()).isTrue();
        assertThat(response.getPoints()).extracting("id").containsExactly(11L, 12L);
        assertThat(response.getPoints().getFirst().getLatitude()).isEqualTo(1.327946);
        assertThat(response.getPoints().getFirst().getLongitude()).isEqualTo(103.804579);
        assertThat(response.getPoints().getFirst().getSourceType()).isEqualTo("OWNTRACKS");
    }

    @Test
    void resolveRawGpsPointLocation_rejectsPointOwnedByAnotherUser() {
        UUID currentUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        GpsPointRepository repository = mock(GpsPointRepository.class);
        LocationPointResolver resolver = mock(LocationPointResolver.class);

        when(repository.findByIdOptional(99L)).thenReturn(Optional.of(
                gpsPoint(99L, otherUserId, "2026-07-08T10:00:00Z", 1.327946, 103.804579)
        ));

        GpsPointService service = createService(repository, resolver);

        assertThatThrownBy(() -> service.resolveRawGpsPointLocation(currentUserId, 99L))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(resolver);
    }

    @Test
    void resolveRawGpsPointLocation_usesLocationPointResolverFavoriteResult() {
        UUID userId = UUID.randomUUID();
        GpsPointEntity point = gpsPoint(55L, userId, "2026-07-08T10:00:00Z", 1.327946, 103.804579);
        GpsPointRepository repository = mock(GpsPointRepository.class);
        LocationPointResolver resolver = mock(LocationPointResolver.class);

        when(repository.findByIdOptional(55L)).thenReturn(Optional.of(point));
        when(resolver.resolveLocationWithReferences(userId, point.getCoordinates()))
                .thenReturn(LocationResolutionResult.fromFavorite("Home", 7L, 1.327900, 103.804500));

        RawGpsPointLocationDTO result = createService(repository, resolver)
                .resolveRawGpsPointLocation(userId, 55L);

        assertThat(result.getLocationName()).isEqualTo("Home");
        assertThat(result.getSourceType()).isEqualTo("favorite");
        assertThat(result.getFavoriteId()).isEqualTo(7L);
        assertThat(result.getGeocodingId()).isNull();
        assertThat(result.getAnchorLatitude()).isEqualTo(1.327900);
        assertThat(result.getAnchorLongitude()).isEqualTo(103.804500);
    }

    private GpsPointService createService(GpsPointRepository repository, LocationPointResolver resolver) {
        GpsPointService service = new GpsPointService(
                new GpsPointMapper(),
                repository,
                mock(GpsPointDuplicateDetectionService.class),
                mock(EntityManager.class),
                mock(StreamingTimelineGenerationService.class),
                mock(GpsDataFilteringService.class),
                mock(GpsTelemetryRenderingService.class),
                mock(GeofenceEvaluationService.class),
                mock(TimelineConfigurationProvider.class),
                mock(GpsPointEnvironmentService.class)
        );
        service.locationPointResolver = resolver;
        return service;
    }

    private GpsPointEntity gpsPoint(Long id, UUID userId, String timestamp, double latitude, double longitude) {
        GpsPointEntity point = new GpsPointEntity();
        point.setId(id);
        point.setUser(UserEntity.builder().id(userId).build());
        point.setTimestamp(Instant.parse(timestamp));
        point.setCoordinates(GeoUtils.createPoint(longitude, latitude));
        point.setAccuracy(6.0);
        point.setBattery(61.0);
        point.setVelocity(62.0);
        point.setAltitude(123.0);
        point.setSourceType(GpsSourceType.OWNTRACKS);
        return point;
    }
}

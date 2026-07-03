package org.github.tess1o.geopulse.gps.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.github.tess1o.geopulse.geofencing.service.GeofenceEvaluationService;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.model.GpsStatusDTO;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gps.service.filter.GpsDataFilteringService;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.trips.GpsPointEnvironmentService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class GpsPointServiceStatusTest {

    @Test
    void getGpsStatus_returnsEmptyStatusWhenUserHasNoGpsData() {
        UUID userId = UUID.randomUUID();
        GpsPointRepository repository = mock(GpsPointRepository.class);
        when(repository.countByUser(userId)).thenReturn(0L);
        when(repository.findLatest(userId)).thenReturn(Optional.empty());

        GpsStatusDTO status = createService(repository).getGpsStatus(userId);

        assertThat(status.generatedAt()).isNotNull();
        assertThat(status.hasGpsData()).isFalse();
        assertThat(status.latestGpsPointTimestamp()).isNull();
        assertThat(status.latestGpsPointEpochSeconds()).isNull();
        assertThat(status.latestGpsPointAgeSeconds()).isNull();
        assertThat(status.latestGpsPointAgeMinutes()).isNull();
        assertThat(status.latestGpsPointReceivedAt()).isNull();
        assertThat(status.latestSourceType()).isNull();
        assertThat(status.latestDeviceId()).isNull();
        assertThat(status.totalGpsPoints()).isZero();
    }

    @Test
    void getGpsStatus_returnsLatestPointMetadataWithoutCoordinates() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant timestamp = Instant.now().minusSeconds(3660);
        Instant receivedAt = timestamp.plusSeconds(2);

        GpsPointEntity latestPoint = new GpsPointEntity();
        latestPoint.setTimestamp(timestamp);
        latestPoint.setCreatedAt(receivedAt);
        latestPoint.setSourceType(GpsSourceType.OWNTRACKS);
        latestPoint.setDeviceId("pixel-9-pro");

        GpsPointRepository repository = mock(GpsPointRepository.class);
        when(repository.countByUser(userId)).thenReturn(12345L);
        when(repository.findLatest(userId)).thenReturn(Optional.of(latestPoint));

        GpsStatusDTO status = createService(repository).getGpsStatus(userId);

        assertThat(status.hasGpsData()).isTrue();
        assertThat(status.latestGpsPointTimestamp()).isEqualTo(timestamp);
        assertThat(status.latestGpsPointEpochSeconds()).isEqualTo(timestamp.getEpochSecond());
        assertThat(status.latestGpsPointAgeSeconds()).isBetween(3660L, 3670L);
        assertThat(status.latestGpsPointAgeMinutes()).isEqualTo(61L);
        assertThat(status.latestGpsPointReceivedAt()).isEqualTo(receivedAt);
        assertThat(status.latestSourceType()).isEqualTo("OWNTRACKS");
        assertThat(status.latestDeviceId()).isEqualTo("pixel-9-pro");
        assertThat(status.totalGpsPoints()).isEqualTo(12345L);

        JsonNode json = objectMapper().valueToTree(status);
        assertThat(json.has("coordinates")).isFalse();
        assertThat(json.has("latitude")).isFalse();
        assertThat(json.has("longitude")).isFalse();
        assertThat(json.has("latestGpsPointTimestamp")).isTrue();
    }

    private GpsPointService createService(GpsPointRepository repository) {
        return new GpsPointService(
                mock(GpsPointMapper.class),
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
    }

    private ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .build();
    }
}

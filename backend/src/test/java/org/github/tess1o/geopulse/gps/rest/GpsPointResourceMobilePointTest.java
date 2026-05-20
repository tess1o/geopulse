package org.github.tess1o.geopulse.gps.rest;

import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.geofencing.service.GeofenceEvaluationService;
import org.github.tess1o.geopulse.gps.exceptions.GpsCoordinateDuplicateException;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.model.GpsPointDTO;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.model.GpsPointsRetentionRequest;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.gps.service.GpsPointDuplicateDetectionService;
import org.github.tess1o.geopulse.gps.service.GpsTelemetryRenderingService;
import org.github.tess1o.geopulse.gps.service.filter.GpsDataFilteringService;
import org.github.tess1o.geopulse.gps.service.filter.GpsFilterResult;
import org.github.tess1o.geopulse.gps.service.simplification.PathSimplificationService;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.service.GpsSourceService;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import jakarta.persistence.EntityManager;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class GpsPointResourceMobilePointTest {
    private static final String DEVICE_ID = "pixel-9-pro";


    @Mock
    GpsPointService gpsPointService;

    @Mock
    CurrentUserService currentUserService;

    @Mock
    PathSimplificationService pathSimplificationService;

    @Mock
    TimelineConfigurationProvider configurationProvider;

    @Mock
    GpsSourceService gpsSourceService;

    GpsPointResource resource;

    @BeforeEach
    void setUp() {
        resource = new GpsPointResource(
                gpsPointService,
                currentUserService,
                pathSimplificationService,
                configurationProvider,
                gpsSourceService
        );
    }

    @Test
    void ingestMobileAppPoints_usesDefaultMobileAppConfigWithoutUiDependency() {
        UUID userId = UUID.randomUUID();
        GpsPointDTO point = new GpsPointDTO(
                0L,
                Instant.parse("2026-05-15T10:00:00Z"),
                new GpsPointDTO.CoordinatesDTO(59.3293, 18.0686),
                5.0,
                89.0,
                1.0,
                20.0,
                null
        );
        List<GpsPointDTO> points = List.of(point);
        GpsPointsRetentionRequest request = new GpsPointsRetentionRequest(points);

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(gpsSourceService.isDefaultFilterInaccurateDataEnabled()).thenReturn(false);
        when(gpsSourceService.getDefaultMaxAllowedAccuracy()).thenReturn(100);
        when(gpsSourceService.getDefaultMaxAllowedSpeed()).thenReturn(250);
        when(gpsSourceService.isDefaultDuplicateDetectionEnabled()).thenReturn(false);
        when(gpsSourceService.getDefaultDuplicateDetectionThresholdMinutes()).thenReturn(2);

        Response response = resource.ingestMobileAppPoints(request, DEVICE_ID);

        assertEquals(200, response.getStatus());
        verify(gpsPointService).saveMobileAppGpsPoints(eq(points), eq(DEVICE_ID), eq(userId), eq(GpsSourceType.MOBILE_APP), any(GpsSourceConfigEntity.class));
    }

    @Test
    void ingestMobileAppPoints_appliesConfiguredGlobalDefaults() {
        UUID userId = UUID.randomUUID();
        GpsPointDTO point = new GpsPointDTO(
                0L,
                Instant.parse("2026-05-15T10:00:00Z"),
                new GpsPointDTO.CoordinatesDTO(59.3293, 18.0686),
                null,
                null,
                null,
                null,
                null
        );
        List<GpsPointDTO> points = List.of(point);
        GpsPointsRetentionRequest request = new GpsPointsRetentionRequest(points);

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(gpsSourceService.isDefaultFilterInaccurateDataEnabled()).thenReturn(true);
        when(gpsSourceService.getDefaultMaxAllowedAccuracy()).thenReturn(42);
        when(gpsSourceService.getDefaultMaxAllowedSpeed()).thenReturn(180);
        when(gpsSourceService.isDefaultDuplicateDetectionEnabled()).thenReturn(true);
        when(gpsSourceService.getDefaultDuplicateDetectionThresholdMinutes()).thenReturn(7);

        Response response = resource.ingestMobileAppPoints(request, DEVICE_ID);

        assertEquals(200, response.getStatus());
        verify(gpsPointService).saveMobileAppGpsPoints(eq(points), eq(DEVICE_ID), eq(userId), eq(GpsSourceType.MOBILE_APP), any(GpsSourceConfigEntity.class));
    }

    @Test
    void ingestMobileAppPoints_replacesNullPointsWithEmptyList() {
        UUID userId = UUID.randomUUID();
        GpsPointsRetentionRequest request = new GpsPointsRetentionRequest(null);

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(gpsSourceService.isDefaultFilterInaccurateDataEnabled()).thenReturn(false);
        when(gpsSourceService.getDefaultMaxAllowedAccuracy()).thenReturn(100);
        when(gpsSourceService.getDefaultMaxAllowedSpeed()).thenReturn(250);
        when(gpsSourceService.isDefaultDuplicateDetectionEnabled()).thenReturn(false);
        when(gpsSourceService.getDefaultDuplicateDetectionThresholdMinutes()).thenReturn(2);

        Response response = resource.ingestMobileAppPoints(request, DEVICE_ID);

        assertEquals(200, response.getStatus());
        verify(gpsPointService).saveMobileAppGpsPoints(eq(List.of()), eq(DEVICE_ID), eq(userId), eq(GpsSourceType.MOBILE_APP), any(GpsSourceConfigEntity.class));
    }

    @Test
    void ingestMobileAppPoints_returnsConflictWhenPointIsDuplicate() {
        UUID userId = UUID.randomUUID();
        GpsPointDTO point = new GpsPointDTO(
                0L,
                Instant.parse("2026-05-15T10:00:00Z"),
                new GpsPointDTO.CoordinatesDTO(59.3293, 18.0686),
                5.0,
                89.0,
                1.0,
                20.0,
                null
        );
        List<GpsPointDTO> points = List.of(point);
        GpsPointsRetentionRequest request = new GpsPointsRetentionRequest(points);
        String duplicateMessage = "Skipping duplicate Mobile App GPS point for user " + userId + " at timestamp 2026-05-15T10:00:00Z";

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(gpsSourceService.isDefaultFilterInaccurateDataEnabled()).thenReturn(false);
        when(gpsSourceService.getDefaultMaxAllowedAccuracy()).thenReturn(100);
        when(gpsSourceService.getDefaultMaxAllowedSpeed()).thenReturn(250);
        when(gpsSourceService.isDefaultDuplicateDetectionEnabled()).thenReturn(false);
        when(gpsSourceService.getDefaultDuplicateDetectionThresholdMinutes()).thenReturn(2);
        doThrow(new GpsCoordinateDuplicateException(duplicateMessage))
                .when(gpsPointService)
                .saveMobileAppGpsPoints(eq(points), eq(DEVICE_ID), eq(userId), eq(GpsSourceType.MOBILE_APP), any(GpsSourceConfigEntity.class));

        Response response = resource.ingestMobileAppPoints(request, DEVICE_ID);

        assertEquals(409, response.getStatus());
    }

    @Test
    void saveMobileAppGpsPoints_persistsOldestToNewestAndSkipsMissingTimestamp() {
        GpsPointMapper mapper = mock(GpsPointMapper.class);
        GpsPointRepository repository = mock(GpsPointRepository.class);
        GpsPointDuplicateDetectionService duplicateDetectionService = mock(GpsPointDuplicateDetectionService.class);
        EntityManager entityManager = mock(EntityManager.class);
        StreamingTimelineGenerationService timelineService = mock(StreamingTimelineGenerationService.class);
        GpsDataFilteringService filteringService = mock(GpsDataFilteringService.class);
        GpsTelemetryRenderingService telemetryService = mock(GpsTelemetryRenderingService.class);
        GeofenceEvaluationService geofenceService = mock(GeofenceEvaluationService.class);
        GpsPointService service = new GpsPointService(
                mapper,
                repository,
                duplicateDetectionService,
                entityManager,
                timelineService,
                filteringService,
                telemetryService,
                geofenceService
        );

        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        Instant earlierTimestamp = Instant.parse("2026-05-15T09:00:00Z");
        Instant laterTimestamp = Instant.parse("2026-05-15T11:00:00Z");
        GpsPointDTO laterPoint = new GpsPointDTO(0L, laterTimestamp, new GpsPointDTO.CoordinatesDTO(41.0, -73.0), 5.0, 88.0, 1.5, 12.0, null);
        GpsPointDTO missingTimestampPoint = new GpsPointDTO(0L, null, new GpsPointDTO.CoordinatesDTO(42.0, -72.0), 5.0, 87.0, 1.0, 13.0, null);
        GpsPointDTO earlierPoint = new GpsPointDTO(0L, earlierTimestamp, new GpsPointDTO.CoordinatesDTO(40.0, -74.0), 4.0, 86.0, 0.5, 14.0, null);
        GpsSourceConfigEntity config = GpsSourceConfigEntity.builder()
                .sourceType(GpsSourceType.MOBILE_APP)
                .active(true)
                .filterInaccurateData(false)
                .maxAllowedAccuracy(100)
                .maxAllowedSpeed(250)
                .enableDuplicateDetection(false)
                .build();
        GpsPointEntity earlierEntity = new GpsPointEntity();
        earlierEntity.setUser(user);
        earlierEntity.setTimestamp(earlierTimestamp);
        GpsPointEntity laterEntity = new GpsPointEntity();
        laterEntity.setUser(user);
        laterEntity.setTimestamp(laterTimestamp);

        when(entityManager.getReference(UserEntity.class, userId)).thenReturn(user);
        when(duplicateDetectionService.isDuplicatePoint(userId, earlierTimestamp, GpsSourceType.MOBILE_APP)).thenReturn(false);
        when(duplicateDetectionService.isDuplicatePoint(userId, laterTimestamp, GpsSourceType.MOBILE_APP)).thenReturn(false);
        when(mapper.toEntity(earlierPoint, DEVICE_ID, user, GpsSourceType.MOBILE_APP)).thenReturn(earlierEntity);
        when(mapper.toEntity(laterPoint, DEVICE_ID, user, GpsSourceType.MOBILE_APP)).thenReturn(laterEntity);
        when(filteringService.filter(any(GpsPointEntity.class), eq(config))).thenReturn(GpsFilterResult.accepted());
        when(repository.findByUniqueKey(any(), any(), any())).thenReturn(java.util.Optional.empty());

        service.saveMobileAppGpsPoints(List.of(laterPoint, missingTimestampPoint, earlierPoint), DEVICE_ID, userId, GpsSourceType.MOBILE_APP, config);

        var inOrder = inOrder(repository);
        inOrder.verify(repository).persist(earlierEntity);
        inOrder.verify(repository).persist(laterEntity);
        inOrder.verifyNoMoreInteractions();

        verify(mapper, never()).toEntity(eq(missingTimestampPoint), any(), any(), any());
    }

    @Test
    void saveMobileAppGpsPoints_ignoresNullAndEmptyPayloads() {
        GpsPointMapper mapper = mock(GpsPointMapper.class);
        GpsPointRepository repository = mock(GpsPointRepository.class);
        GpsPointDuplicateDetectionService duplicateDetectionService = mock(GpsPointDuplicateDetectionService.class);
        EntityManager entityManager = mock(EntityManager.class);
        StreamingTimelineGenerationService timelineService = mock(StreamingTimelineGenerationService.class);
        GpsDataFilteringService filteringService = mock(GpsDataFilteringService.class);
        GpsTelemetryRenderingService telemetryService = mock(GpsTelemetryRenderingService.class);
        GeofenceEvaluationService geofenceService = mock(GeofenceEvaluationService.class);
        GpsPointService service = new GpsPointService(
                mapper,
                repository,
                duplicateDetectionService,
                entityManager,
                timelineService,
                filteringService,
                telemetryService,
                geofenceService
        );

        UUID userId = UUID.randomUUID();
        GpsSourceConfigEntity config = GpsSourceConfigEntity.builder()
                .sourceType(GpsSourceType.MOBILE_APP)
                .active(true)
                .filterInaccurateData(false)
                .maxAllowedAccuracy(100)
                .maxAllowedSpeed(250)
                .enableDuplicateDetection(false)
                .build();

        service.saveMobileAppGpsPoints(null, DEVICE_ID, userId, GpsSourceType.MOBILE_APP, config);
        service.saveMobileAppGpsPoints(List.of(), DEVICE_ID, userId, GpsSourceType.MOBILE_APP, config);

        verifyNoInteractions(
                mapper,
                repository,
                duplicateDetectionService,
                entityManager,
                timelineService,
                filteringService,
                telemetryService,
                geofenceService
        );
    }
}

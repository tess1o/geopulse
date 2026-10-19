package org.github.tess1o.geopulse.gps.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.coverage.model.CoverageStatus;
import org.github.tess1o.geopulse.coverage.service.CoverageProcessingService;
import org.github.tess1o.geopulse.coverage.service.CoverageService;
import org.github.tess1o.geopulse.geofencing.service.GeofenceEvaluationService;
import org.github.tess1o.geopulse.gps.exceptions.GpsCoordinateDuplicateException;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.model.GpsPointDTO;
import org.github.tess1o.geopulse.gps.model.GpsPointDeleteResult;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.model.GpsPointsRetentionRequest;
import org.github.tess1o.geopulse.gps.model.GpsPointDeleteResponse;
import org.github.tess1o.geopulse.gps.model.BulkDeleteGpsPointsDto;
import org.github.tess1o.geopulse.gps.model.BulkEditGpsPointsDto;
import org.github.tess1o.geopulse.gps.model.EditGpsPointDto;
import org.github.tess1o.geopulse.gps.model.GpsPointBulkUpdateResponse;
import org.github.tess1o.geopulse.gps.model.GpsPointEditItem;
import org.github.tess1o.geopulse.gps.model.GpsPointUpdateResponse;
import org.github.tess1o.geopulse.gps.model.GpsPointUpdateResult;
import org.github.tess1o.geopulse.shared.api.ApiErrorCode;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
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
import org.github.tess1o.geopulse.streaming.service.AsyncTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.repository.TimelineRebuildRequestRepository;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.service.trips.GpsPointEnvironmentService;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Mock
    AsyncTimelineGenerationService asyncTimelineGenerationService;

    @Mock
    CoverageService coverageService;

    @Mock
    CoverageProcessingService coverageProcessingService;

    GpsPointResource resource;

    @BeforeEach
    void setUp() {
        resource = new GpsPointResource(
                gpsPointService,
                currentUserService,
                pathSimplificationService,
                configurationProvider,
                gpsSourceService,
                asyncTimelineGenerationService,
                coverageService,
                coverageProcessingService
        );
    }

    @Test
    void deleteGpsPoints_returnsFastResponseAndSchedulesDerivedDataRefresh() {
        UUID userId = UUID.randomUUID();
        UUID timelineJobId = UUID.randomUUID();
        Instant earliestTimestamp = Instant.parse("2026-08-13T10:18:57Z");
        BulkDeleteGpsPointsDto request = new BulkDeleteGpsPointsDto(List.of(10L, 11L));

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(gpsPointService.deleteGpsPoints(List.of(10L, 11L), userId))
                .thenReturn(new GpsPointDeleteResult(2, earliestTimestamp));
        when(asyncTimelineGenerationService.scheduleTimelineRegenerationFromTimestamp(userId, earliestTimestamp))
                .thenReturn(new AsyncTimelineGenerationService.TimelineSchedulingResult(timelineJobId, true, false));
        when(coverageService.getCoverageStatus(userId))
                .thenReturn(new CoverageStatus(true, true, true, null, null));
        when(coverageProcessingService.requestFullRecalculationAsync(userId))
                .thenReturn(new CoverageProcessingService.CoverageSchedulingResult(true, true));

        GpsPointDeleteResponse response = resource.deleteGpsPoints(request);

        assertEquals(2, response.deletedCount());
        assertEquals(timelineJobId, response.timelineJobId());
        assertEquals(true, response.timelineRegenerationScheduled());
        assertEquals(true, response.coverageRebuildScheduled());
        verify(asyncTimelineGenerationService).scheduleTimelineRegenerationFromTimestamp(userId, earliestTimestamp);
        verify(coverageProcessingService).requestFullRecalculationAsync(userId);
    }

    @Test
    void deleteGpsPoints_emptyDeleteDoesNotScheduleDerivedDataRefresh() {
        UUID userId = UUID.randomUUID();
        BulkDeleteGpsPointsDto request = new BulkDeleteGpsPointsDto(List.of(10L));

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(gpsPointService.deleteGpsPoints(List.of(10L), userId))
                .thenReturn(new GpsPointDeleteResult(0, null));

        GpsPointDeleteResponse response = resource.deleteGpsPoints(request);

        assertEquals(0, response.deletedCount());
        assertNull(response.timelineJobId());
        assertEquals(false, response.timelineRegenerationScheduled());
        assertEquals(false, response.coverageRebuildScheduled());
        verifyNoInteractions(asyncTimelineGenerationService, coverageService, coverageProcessingService);
    }

    @Test
    void deleteGpsPoints_deletesRowsWithoutSynchronousTimelineGeneration() {
        GpsPointMapper mapper = mock(GpsPointMapper.class);
        GpsPointRepository repository = mock(GpsPointRepository.class);
        GpsPointDuplicateDetectionService duplicateDetectionService = mock(GpsPointDuplicateDetectionService.class);
        EntityManager entityManager = mock(EntityManager.class);
        GpsDataFilteringService filteringService = mock(GpsDataFilteringService.class);
        GpsTelemetryRenderingService telemetryService = mock(GpsTelemetryRenderingService.class);
        GeofenceEvaluationService geofenceService = mock(GeofenceEvaluationService.class);
        TimelineConfigurationProvider timelineConfigurationProvider = mock(TimelineConfigurationProvider.class);
        GpsPointEnvironmentService gpsPointEnvironmentService = mock(GpsPointEnvironmentService.class);
        GpsPointService service = new GpsPointService(
                mapper,
                repository,
                duplicateDetectionService,
                entityManager,
                filteringService,
                telemetryService,
                geofenceService,
                timelineConfigurationProvider,
                gpsPointEnvironmentService,
                mock(TimelineRebuildRequestRepository.class)
        );

        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        GpsPointEntity firstPoint = new GpsPointEntity();
        firstPoint.setUser(user);
        firstPoint.setTimestamp(Instant.parse("2026-08-13T10:18:57Z"));
        GpsPointEntity secondPoint = new GpsPointEntity();
        secondPoint.setUser(user);
        secondPoint.setTimestamp(Instant.parse("2026-08-13T10:20:00Z"));
        List<Long> pointIds = List.of(10L, 11L);

        when(repository.list("id in ?1", pointIds)).thenReturn(List.of(firstPoint, secondPoint));

        GpsPointDeleteResult result = service.deleteGpsPoints(pointIds, userId);

        assertEquals(2, result.deletedCount());
        assertEquals(Instant.parse("2026-08-13T10:18:57Z"), result.earliestAffectedTimestamp());
        verify(repository).delete(firstPoint);
        verify(repository).delete(secondPoint);
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

        resource.ingestMobileAppPoints(request, DEVICE_ID);
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

        resource.ingestMobileAppPoints(request, DEVICE_ID);
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

        resource.ingestMobileAppPoints(request, DEVICE_ID);
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

        GeoPulseException problem = assertThrows(GeoPulseException.class,
                () -> resource.ingestMobileAppPoints(request, DEVICE_ID));
        assertEquals(409, problem.code().statusCode());
    }

    @Test
    void saveMobileAppGpsPoints_persistsOldestToNewestAndSkipsMissingTimestamp() {
        GpsPointMapper mapper = mock(GpsPointMapper.class);
        GpsPointRepository repository = mock(GpsPointRepository.class);
        GpsPointDuplicateDetectionService duplicateDetectionService = mock(GpsPointDuplicateDetectionService.class);
        EntityManager entityManager = mock(EntityManager.class);
        GpsDataFilteringService filteringService = mock(GpsDataFilteringService.class);
        GpsTelemetryRenderingService telemetryService = mock(GpsTelemetryRenderingService.class);
        GeofenceEvaluationService geofenceService = mock(GeofenceEvaluationService.class);
        TimelineConfigurationProvider timelineConfigurationProvider = mock(TimelineConfigurationProvider.class);
        GpsPointEnvironmentService gpsPointEnvironmentService = mock(GpsPointEnvironmentService.class);
        GpsPointService service = new GpsPointService(
                mapper,
                repository,
                duplicateDetectionService,
                entityManager,
                filteringService,
                telemetryService,
                geofenceService,
                timelineConfigurationProvider,
                gpsPointEnvironmentService,
                mock(TimelineRebuildRequestRepository.class)
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
    void saveMobileAppGpsPoints_enrichesSavedBatchWhenBoatDatasetExists() {
        GpsPointMapper mapper = mock(GpsPointMapper.class);
        GpsPointRepository repository = mock(GpsPointRepository.class);
        GpsPointDuplicateDetectionService duplicateDetectionService = mock(GpsPointDuplicateDetectionService.class);
        EntityManager entityManager = mock(EntityManager.class);
        GpsDataFilteringService filteringService = mock(GpsDataFilteringService.class);
        GpsTelemetryRenderingService telemetryService = mock(GpsTelemetryRenderingService.class);
        GeofenceEvaluationService geofenceService = mock(GeofenceEvaluationService.class);
        TimelineConfigurationProvider timelineConfigurationProvider = mock(TimelineConfigurationProvider.class);
        GpsPointEnvironmentService gpsPointEnvironmentService = mock(GpsPointEnvironmentService.class);
        GpsPointService service = new GpsPointService(
                mapper,
                repository,
                duplicateDetectionService,
                entityManager,
                filteringService,
                telemetryService,
                geofenceService,
                timelineConfigurationProvider,
                gpsPointEnvironmentService,
                mock(TimelineRebuildRequestRepository.class)
        );

        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        Instant firstTimestamp = Instant.parse("2026-05-15T09:00:00Z");
        Instant secondTimestamp = Instant.parse("2026-05-15T09:05:00Z");
        GpsPointDTO firstPoint = new GpsPointDTO(0L, firstTimestamp, new GpsPointDTO.CoordinatesDTO(40.0, -74.0), 4.0, 86.0, 0.5, 14.0, null);
        GpsPointDTO secondPoint = new GpsPointDTO(0L, secondTimestamp, new GpsPointDTO.CoordinatesDTO(40.1, -74.1), 5.0, 88.0, 1.5, 12.0, null);
        GpsSourceConfigEntity config = GpsSourceConfigEntity.builder()
                .sourceType(GpsSourceType.MOBILE_APP)
                .active(true)
                .filterInaccurateData(false)
                .maxAllowedAccuracy(100)
                .maxAllowedSpeed(250)
                .enableDuplicateDetection(false)
                .build();
        GpsPointEntity firstEntity = new GpsPointEntity();
        firstEntity.setId(10L);
        firstEntity.setUser(user);
        firstEntity.setTimestamp(firstTimestamp);
        GpsPointEntity secondEntity = new GpsPointEntity();
        secondEntity.setId(11L);
        secondEntity.setUser(user);
        secondEntity.setTimestamp(secondTimestamp);

        when(entityManager.getReference(UserEntity.class, userId)).thenReturn(user);
        when(duplicateDetectionService.isDuplicatePoint(userId, firstTimestamp, GpsSourceType.MOBILE_APP)).thenReturn(false);
        when(duplicateDetectionService.isDuplicatePoint(userId, secondTimestamp, GpsSourceType.MOBILE_APP)).thenReturn(false);
        when(mapper.toEntity(firstPoint, DEVICE_ID, user, GpsSourceType.MOBILE_APP)).thenReturn(firstEntity);
        when(mapper.toEntity(secondPoint, DEVICE_ID, user, GpsSourceType.MOBILE_APP)).thenReturn(secondEntity);
        when(filteringService.filter(any(GpsPointEntity.class), eq(config))).thenReturn(GpsFilterResult.accepted());
        when(repository.findByUniqueKey(any(), any(), any())).thenReturn(java.util.Optional.empty());
        when(gpsPointEnvironmentService.getCurrentEnvironmentDatasetVersion()).thenReturn("dataset-v1");
        when(timelineConfigurationProvider.isBoatEnabledForUser(userId)).thenReturn(true);

        service.saveMobileAppGpsPoints(List.of(firstPoint, secondPoint), DEVICE_ID, userId, GpsSourceType.MOBILE_APP, config);

        verify(entityManager).flush();
        verify(gpsPointEnvironmentService).enrichPoints(userId, List.of(10L, 11L), "dataset-v1");
    }

    @Test
    void saveMobileAppGpsPoints_ignoresNullAndEmptyPayloads() {
        GpsPointMapper mapper = mock(GpsPointMapper.class);
        GpsPointRepository repository = mock(GpsPointRepository.class);
        GpsPointDuplicateDetectionService duplicateDetectionService = mock(GpsPointDuplicateDetectionService.class);
        EntityManager entityManager = mock(EntityManager.class);
        GpsDataFilteringService filteringService = mock(GpsDataFilteringService.class);
        GpsTelemetryRenderingService telemetryService = mock(GpsTelemetryRenderingService.class);
        GeofenceEvaluationService geofenceService = mock(GeofenceEvaluationService.class);
        TimelineConfigurationProvider timelineConfigurationProvider = mock(TimelineConfigurationProvider.class);
        GpsPointEnvironmentService gpsPointEnvironmentService = mock(GpsPointEnvironmentService.class);
        GpsPointService service = new GpsPointService(
                mapper,
                repository,
                duplicateDetectionService,
                entityManager,
                filteringService,
                telemetryService,
                geofenceService,
                timelineConfigurationProvider,
                gpsPointEnvironmentService,
                mock(TimelineRebuildRequestRepository.class)
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
                filteringService,
                telemetryService,
                geofenceService,
                timelineConfigurationProvider,
                gpsPointEnvironmentService
        );
    }

    @Test
    void updateGpsPoints_returnsFastResponseAndSchedulesDerivedDataRefresh() {
        UUID userId = UUID.randomUUID();
        UUID timelineJobId = UUID.randomUUID();
        Instant earliestTimestamp = Instant.parse("2026-08-13T10:18:57Z");
        List<GpsPointEditItem> edits = List.of(editItem(10L, 50.0, 30.0, 0.0), editItem(11L, 50.1, 30.1, 0.0));

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(gpsPointService.updateGpsPoints(edits, userId))
                .thenReturn(new GpsPointUpdateResult(List.of(dto(10L), dto(11L)), earliestTimestamp, true));
        when(asyncTimelineGenerationService.scheduleTimelineRegenerationFromTimestamp(userId, earliestTimestamp))
                .thenReturn(new AsyncTimelineGenerationService.TimelineSchedulingResult(timelineJobId, true, false));
        when(coverageService.getCoverageStatus(userId))
                .thenReturn(new CoverageStatus(true, true, true, null, null));
        when(coverageProcessingService.requestFullRecalculationAsync(userId))
                .thenReturn(new CoverageProcessingService.CoverageSchedulingResult(true, true));

        GpsPointBulkUpdateResponse response = resource.updateGpsPoints(new BulkEditGpsPointsDto(edits));

        assertEquals(2, response.updatedCount());
        assertEquals(timelineJobId, response.timelineJobId());
        assertTrue(response.timelineRegenerationScheduled());
        assertTrue(response.coverageRebuildScheduled());
    }

    @Test
    void updateGpsPoint_skipsCoverageRebuildWhenCoordinatesUnchanged() {
        UUID userId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-08-13T10:18:57Z");
        EditGpsPointDto edit = editItem(10L, 50.0, 30.0, 0.0);
        GpsPointDTO updated = dto(10L);

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(gpsPointService.updateGpsPoint(10L, edit, userId))
                .thenReturn(new GpsPointUpdateResult(List.of(updated), timestamp, false));
        when(asyncTimelineGenerationService.scheduleTimelineRegenerationFromTimestamp(userId, timestamp))
                .thenReturn(new AsyncTimelineGenerationService.TimelineSchedulingResult(null, true, true));

        GpsPointUpdateResponse response = resource.updateGpsPoint(10L, edit);

        assertEquals(updated, response.point());
        assertNull(response.timelineJobId());
        assertTrue(response.timelineRegenerationScheduled());
        assertFalse(response.coverageRebuildScheduled());
        verifyNoInteractions(coverageService, coverageProcessingService);
    }

    @Test
    void updateGpsPoints_appliesEditsAndRequestsRebuildWithoutSynchronousTimelineGeneration() {
        GpsPointRepository repository = mock(GpsPointRepository.class);
        TimelineRebuildRequestRepository rebuildRequests = mock(TimelineRebuildRequestRepository.class);
        GpsPointService service = serviceForEdits(repository, rebuildRequests);
        UUID userId = UUID.randomUUID();
        GpsPointEntity first = entity(10L, userId, Instant.parse("2026-08-13T10:20:00Z"), 50.0, 30.0);
        GpsPointEntity second = entity(11L, userId, Instant.parse("2026-08-13T10:18:57Z"), 50.1, 30.1);
        when(repository.list("id in ?1", List.of(10L, 11L))).thenReturn(List.of(first, second));

        GpsPointUpdateResult result = service.updateGpsPoints(
                List.of(editItem(10L, 50.0, 30.0, 0.0), editItem(11L, 50.1, 30.1, 3.5)), userId);

        assertEquals(2, result.points().size());
        assertEquals(Instant.parse("2026-08-13T10:18:57Z"), result.earliestAffectedTimestamp());
        assertFalse(result.coordinatesChanged());
        assertEquals(0.0, first.getVelocity());
        assertEquals(3.5, second.getVelocity());
        verify(repository).persist(first);
        verify(repository).persist(second);
        verify(rebuildRequests).request(userId, Instant.parse("2026-08-13T10:18:57Z"));
    }

    @Test
    void updateGpsPoints_reportsMovedCoordinates() {
        GpsPointRepository repository = mock(GpsPointRepository.class);
        GpsPointService service = serviceForEdits(repository, mock(TimelineRebuildRequestRepository.class));
        UUID userId = UUID.randomUUID();
        GpsPointEntity point = entity(10L, userId, Instant.parse("2026-08-13T10:20:00Z"), 50.0, 30.0);
        when(repository.list("id in ?1", List.of(10L))).thenReturn(List.of(point));

        GpsPointUpdateResult result = service.updateGpsPoints(List.of(editItem(10L, 50.5, 30.0, 1.0)), userId);

        assertTrue(result.coordinatesChanged());
        assertEquals(50.5, point.getCoordinates().getY());
        assertEquals(30.0, point.getCoordinates().getX());
    }

    @Test
    void updateGpsPoints_rejectsDuplicateMissingAndForeignPoints() {
        GpsPointRepository repository = mock(GpsPointRepository.class);
        TimelineRebuildRequestRepository rebuildRequests = mock(TimelineRebuildRequestRepository.class);
        GpsPointService service = serviceForEdits(repository, rebuildRequests);
        UUID userId = UUID.randomUUID();
        GpsPointEntity foreign = entity(12L, UUID.randomUUID(), Instant.parse("2026-08-13T10:20:00Z"), 50.0, 30.0);
        when(repository.list("id in ?1", List.of(11L))).thenReturn(List.of());
        when(repository.list("id in ?1", List.of(12L))).thenReturn(List.of(foreign));

        GeoPulseException duplicate = assertThrows(GeoPulseException.class, () -> service.updateGpsPoints(
                List.of(editItem(10L, 50.0, 30.0, 0.0), editItem(10L, 50.0, 30.0, 1.0)), userId));
        GeoPulseException missing = assertThrows(GeoPulseException.class, () -> service.updateGpsPoints(
                List.of(editItem(11L, 50.0, 30.0, 0.0)), userId));
        GeoPulseException denied = assertThrows(GeoPulseException.class, () -> service.updateGpsPoints(
                List.of(editItem(12L, 50.0, 30.0, 0.0)), userId));

        assertEquals(ApiErrorCode.INVALID_GPS_POINT_EDIT, duplicate.code());
        assertEquals(ApiErrorCode.GPS_POINT_NOT_FOUND, missing.code());
        assertEquals(ApiErrorCode.GPS_POINT_ACCESS_DENIED, denied.code());
        verify(repository, never()).persist(any(GpsPointEntity.class));
        verifyNoInteractions(rebuildRequests);
    }

    private static GpsPointEditItem editItem(Long id, double lat, double lng, Double velocity) {
        return new GpsPointEditItem(id, new EditGpsPointDto(
                new EditGpsPointDto.CoordinatesDto(lat, lng), velocity, 10.0));
    }

    private static GpsPointDTO dto(Long id) {
        return new GpsPointDTO(id, Instant.parse("2026-08-13T10:18:57Z"),
                new GpsPointDTO.CoordinatesDTO(50.0, 30.0), 0.0, null, null, 10.0, null);
    }

    private static GpsPointEntity entity(Long id, UUID ownerId, Instant timestamp, double lat, double lng) {
        UserEntity owner = new UserEntity();
        owner.setId(ownerId);
        GpsPointEntity point = new GpsPointEntity();
        point.setId(id);
        point.setUser(owner);
        point.setTimestamp(timestamp);
        point.setCoordinates(GeoUtils.createPoint(lng, lat));
        point.setVelocity(9.0);
        point.setSourceType(GpsSourceType.OWNTRACKS);
        return point;
    }

    private static GpsPointService serviceForEdits(GpsPointRepository repository,
                                                   TimelineRebuildRequestRepository rebuildRequests) {
        return new GpsPointService(
                new GpsPointMapper(),
                repository,
                mock(GpsPointDuplicateDetectionService.class),
                mock(EntityManager.class),
                mock(GpsDataFilteringService.class),
                mock(GpsTelemetryRenderingService.class),
                mock(GeofenceEvaluationService.class),
                mock(TimelineConfigurationProvider.class),
                mock(GpsPointEnvironmentService.class),
                rebuildRequests
        );
    }
}

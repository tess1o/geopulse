package org.github.tess1o.geopulse.gps.rest;

import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.gps.exceptions.GpsCoordinateDuplicateException;
import org.github.tess1o.geopulse.gps.model.MobileAppGpsPointRequest;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.gps.service.simplification.PathSimplificationService;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.service.GpsSourceService;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class GpsPointResourceMobilePointTest {

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
    void ingestMobileAppPoint_usesDefaultMobileAppConfigWithoutUiDependency() {
        UUID userId = UUID.randomUUID();
        MobileAppGpsPointRequest request = new MobileAppGpsPointRequest(
                59.3293, 18.0686, Instant.parse("2026-05-15T10:00:00Z"),
                5.0, 20.0, 1.0, 89.0
        );

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(gpsSourceService.isDefaultFilterInaccurateDataEnabled()).thenReturn(false);
        when(gpsSourceService.getDefaultMaxAllowedAccuracy()).thenReturn(100);
        when(gpsSourceService.getDefaultMaxAllowedSpeed()).thenReturn(250);
        when(gpsSourceService.isDefaultDuplicateDetectionEnabled()).thenReturn(false);
        when(gpsSourceService.getDefaultDuplicateDetectionThresholdMinutes()).thenReturn(2);

        Response response = resource.ingestMobileAppPoint(request);

        assertEquals(200, response.getStatus());
        verify(gpsPointService).saveMobileAppGpsPoint(eq(request), eq(userId), eq(GpsSourceType.MOBILE_APP), any(GpsSourceConfigEntity.class));
    }

    @Test
    void ingestMobileAppPoint_appliesConfiguredGlobalDefaults() {
        UUID userId = UUID.randomUUID();
        MobileAppGpsPointRequest request = new MobileAppGpsPointRequest(
                59.3293, 18.0686, Instant.parse("2026-05-15T10:00:00Z"),
                null, null, null, null
        );

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(gpsSourceService.isDefaultFilterInaccurateDataEnabled()).thenReturn(true);
        when(gpsSourceService.getDefaultMaxAllowedAccuracy()).thenReturn(42);
        when(gpsSourceService.getDefaultMaxAllowedSpeed()).thenReturn(180);
        when(gpsSourceService.isDefaultDuplicateDetectionEnabled()).thenReturn(true);
        when(gpsSourceService.getDefaultDuplicateDetectionThresholdMinutes()).thenReturn(7);

        Response response = resource.ingestMobileAppPoint(request);

        assertEquals(200, response.getStatus());
        verify(gpsPointService).saveMobileAppGpsPoint(eq(request), eq(userId), eq(GpsSourceType.MOBILE_APP), any(GpsSourceConfigEntity.class));
    }

    @Test
    void ingestMobileAppPoint_returnsConflictWhenPointIsDuplicate() {
        UUID userId = UUID.randomUUID();
        MobileAppGpsPointRequest request = new MobileAppGpsPointRequest(
                59.3293, 18.0686, Instant.parse("2026-05-15T10:00:00Z"),
                5.0, 20.0, 1.0, 89.0
        );
        String duplicateMessage = "Skipping duplicate Mobile App GPS point for user " + userId + " at timestamp 2026-05-15T10:00:00Z";

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(gpsSourceService.isDefaultFilterInaccurateDataEnabled()).thenReturn(false);
        when(gpsSourceService.getDefaultMaxAllowedAccuracy()).thenReturn(100);
        when(gpsSourceService.getDefaultMaxAllowedSpeed()).thenReturn(250);
        when(gpsSourceService.isDefaultDuplicateDetectionEnabled()).thenReturn(false);
        when(gpsSourceService.getDefaultDuplicateDetectionThresholdMinutes()).thenReturn(2);
        doThrow(new GpsCoordinateDuplicateException(duplicateMessage))
                .when(gpsPointService)
                .saveMobileAppGpsPoint(eq(request), eq(userId), eq(GpsSourceType.MOBILE_APP), any(GpsSourceConfigEntity.class));

        Response response = resource.ingestMobileAppPoint(request);

        assertEquals(409, response.getStatus());
    }
}

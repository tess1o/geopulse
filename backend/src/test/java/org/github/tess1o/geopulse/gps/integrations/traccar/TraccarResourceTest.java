package org.github.tess1o.geopulse.gps.integrations.traccar;

import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.gps.integrations.traccar.model.TraccarDevice;
import org.github.tess1o.geopulse.gps.integrations.traccar.model.TraccarPositionData;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.service.GpsSourceService;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class TraccarResourceTest {

    @Mock
    private GpsPointService gpsPointService;

    @Mock
    private GpsSourceService gpsSourceService;

    private TraccarResource resource;

    @BeforeEach
    void setUp() {
        resource = new TraccarResource(gpsPointService, gpsSourceService);
    }

    @Test
    void returns401WhenAuthorizationHeaderIsInvalid() {
        Response response = resource.handleTraccar(payloadWithUniqueId("phone-a"), "Token abc");

        assertEquals(401, response.getStatus());
        verifyNoInteractions(gpsSourceService);
        verifyNoInteractions(gpsPointService);
    }

    @Test
    void returns401WhenNoConfigExistsForToken() {
        when(gpsSourceService.findAllActiveByTokenAndSourceType("shared-token", GpsSourceType.TRACCAR))
                .thenReturn(List.of());

        Response response = resource.handleTraccar(payloadWithUniqueId("phone-a"), "Bearer shared-token");

        assertEquals(401, response.getStatus());
        verify(gpsSourceService).findAllActiveByTokenAndSourceType("shared-token", GpsSourceType.TRACCAR);
        verifyNoInteractions(gpsPointService);
    }

    @Test
    void prefersExactDeviceMatchOverWildcard() {
        UUID exactUserId = UUID.randomUUID();
        UUID wildcardUserId = UUID.randomUUID();
        GpsSourceConfigEntity exactConfig = traccarConfig(exactUserId, "shared-token", "phone-a");
        GpsSourceConfigEntity wildcardConfig = traccarConfig(wildcardUserId, "shared-token", null);

        when(gpsSourceService.findAllActiveByTokenAndSourceType("shared-token", GpsSourceType.TRACCAR))
                .thenReturn(List.of(exactConfig, wildcardConfig));

        Response response = resource.handleTraccar(payloadWithUniqueId("  PHONE-A "), "Bearer shared-token");

        assertEquals(200, response.getStatus());
        verify(gpsPointService).saveTraccarGpsPoint(any(TraccarPositionData.class), eq(exactUserId), eq(GpsSourceType.TRACCAR), eq(exactConfig));
        verify(gpsPointService, never()).saveTraccarGpsPoint(any(TraccarPositionData.class), eq(wildcardUserId), eq(GpsSourceType.TRACCAR), eq(wildcardConfig));
    }

    @Test
    void fallsBackToWildcardWhenNoExactDeviceMatchExists() {
        UUID wildcardUserId = UUID.randomUUID();
        UUID otherExactUserId = UUID.randomUUID();
        GpsSourceConfigEntity wildcardConfig = traccarConfig(wildcardUserId, "shared-token", "   ");
        GpsSourceConfigEntity otherExactConfig = traccarConfig(otherExactUserId, "shared-token", "phone-b");

        when(gpsSourceService.findAllActiveByTokenAndSourceType("shared-token", GpsSourceType.TRACCAR))
                .thenReturn(List.of(wildcardConfig, otherExactConfig));

        Response response = resource.handleTraccar(payloadWithUniqueId("phone-c"), "Bearer shared-token");

        assertEquals(200, response.getStatus());
        verify(gpsPointService).saveTraccarGpsPoint(any(TraccarPositionData.class), eq(wildcardUserId), eq(GpsSourceType.TRACCAR), eq(wildcardConfig));
        verify(gpsPointService, never()).saveTraccarGpsPoint(any(TraccarPositionData.class), eq(otherExactUserId), eq(GpsSourceType.TRACCAR), eq(otherExactConfig));
    }

    @Test
    void missingIncomingUniqueIdMatchesWildcardOnly() {
        UUID wildcardUserId = UUID.randomUUID();
        UUID exactUserId = UUID.randomUUID();
        GpsSourceConfigEntity wildcardConfig = traccarConfig(wildcardUserId, "shared-token", null);
        GpsSourceConfigEntity exactConfig = traccarConfig(exactUserId, "shared-token", "phone-a");

        when(gpsSourceService.findAllActiveByTokenAndSourceType("shared-token", GpsSourceType.TRACCAR))
                .thenReturn(List.of(wildcardConfig, exactConfig));

        Response response = resource.handleTraccar(new TraccarPositionData(), "Bearer shared-token");

        assertEquals(200, response.getStatus());
        verify(gpsPointService).saveTraccarGpsPoint(any(TraccarPositionData.class), eq(wildcardUserId), eq(GpsSourceType.TRACCAR), eq(wildcardConfig));
        verify(gpsPointService, never()).saveTraccarGpsPoint(any(TraccarPositionData.class), eq(exactUserId), eq(GpsSourceType.TRACCAR), eq(exactConfig));
    }

    @Test
    void validTokenWithNoEligibleRouteReturns200WithoutSaving() {
        GpsSourceConfigEntity exactConfig = traccarConfig(UUID.randomUUID(), "shared-token", "phone-a");
        when(gpsSourceService.findAllActiveByTokenAndSourceType("shared-token", GpsSourceType.TRACCAR))
                .thenReturn(List.of(exactConfig));

        Response response = resource.handleTraccar(payloadWithUniqueId("phone-b"), "Bearer shared-token");

        assertEquals(200, response.getStatus());
        verifyNoInteractions(gpsPointService);
    }

    private TraccarPositionData payloadWithUniqueId(String uniqueId) {
        TraccarDevice device = new TraccarDevice();
        device.setUniqueId(uniqueId);

        TraccarPositionData data = new TraccarPositionData();
        data.setDevice(device);
        return data;
    }

    private GpsSourceConfigEntity traccarConfig(UUID userId, String token, String deviceId) {
        UserEntity user = new UserEntity();
        user.setId(userId);

        GpsSourceConfigEntity config = new GpsSourceConfigEntity();
        config.setUser(user);
        config.setSourceType(GpsSourceType.TRACCAR);
        config.setToken(token);
        config.setDeviceId(deviceId);
        config.setActive(true);
        return config;
    }
}

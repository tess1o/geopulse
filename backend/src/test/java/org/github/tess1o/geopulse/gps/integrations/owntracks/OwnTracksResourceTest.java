package org.github.tess1o.geopulse.gps.integrations.owntracks;

import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.gps.model.GpsAuthenticationResult;
import org.github.tess1o.geopulse.gps.integrations.owntracks.service.OwnTracksPoiService;
import org.github.tess1o.geopulse.gps.integrations.owntracks.service.OwnTracksTagService;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.gps.service.auth.GpsIntegrationAuthenticatorRegistry;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class OwnTracksResourceTest {

    @Mock
    private GpsPointService gpsPointService;

    @Mock
    private GpsIntegrationAuthenticatorRegistry authRegistry;

    @Mock
    private OwnTracksPoiService ownTracksPoiService;

    @Mock
    private OwnTracksTagService ownTracksTagService;

    private OwnTracksResource resource;

    @BeforeEach
    void setUp() {
        resource = new OwnTracksResource(
                gpsPointService,
                authRegistry,
                ownTracksPoiService,
                ownTracksTagService
        );
    }

    @Test
    void nonLocationPayloadReturnsEmptyJsonArray() {
        Response response = resource.handleOwnTracks(Map.of("_type", "lwt"), "Basic token", "device-a");

        assertEquals(200, response.getStatus());
        assertEquals("[]", response.getEntity());
        verifyNoInteractions(authRegistry);
        verifyNoInteractions(gpsPointService);
    }

    @Test
    void locationPayloadReturnsEmptyJsonArrayOnSuccess() {
        UUID userId = UUID.randomUUID();
        GpsSourceConfigEntity config = new GpsSourceConfigEntity();
        when(authRegistry.authenticate(eq(GpsSourceType.OWNTRACKS), anyString()))
                .thenReturn(Optional.of(new GpsAuthenticationResult(userId, config)));

        Map<String, Object> payload = Map.of(
                "_type", "location",
                "lat", 42.7,
                "lon", 23.3,
                "tst", 1715770000L
        );

        Response response = resource.handleOwnTracks(payload, "Basic token", "device-a");

        assertEquals(200, response.getStatus());
        assertEquals("[]", response.getEntity());
        verify(gpsPointService).saveOwnTracksGpsPoint(any(), eq(userId), eq("device-a"), eq(GpsSourceType.OWNTRACKS), eq(config));
    }

    @Test
    void locationPayloadReturns401WhenAuthenticationFails() {
        when(authRegistry.authenticate(eq(GpsSourceType.OWNTRACKS), anyString()))
                .thenReturn(Optional.empty());

        Response response = resource.handleOwnTracks(
                Map.of("_type", "location", "lat", 42.7, "lon", 23.3, "tst", 1715770000L),
                "Basic bad-token",
                "device-a"
        );

        assertEquals(401, response.getStatus());
        verify(gpsPointService, never()).saveOwnTracksGpsPoint(any(), any(), anyString(), any(), any());
    }
}

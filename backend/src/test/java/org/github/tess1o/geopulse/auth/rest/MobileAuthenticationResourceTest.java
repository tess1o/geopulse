package org.github.tess1o.geopulse.auth.rest;

import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.auth.model.MobileAuthInitResponse;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.auth.service.MobileDeepLinkService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class MobileAuthenticationResourceTest {

    @Mock
    CurrentUserService currentUserService;

    @Mock
    MobileDeepLinkService mobileDeepLinkService;

    MobileAuthenticationResource resource;

    @BeforeEach
    void setUp() {
        resource = new MobileAuthenticationResource();
        resource.currentUserService = currentUserService;
        resource.mobileDeepLinkService = mobileDeepLinkService;
    }

    @Test
    void generateCode_returnsJsonFromService() {
        UUID userId = UUID.randomUUID();

        var serviceResponse = MobileAuthInitResponse.builder()
                .code("generated-code")
                .deeplinkUrl("app://auth/code/exchange")
                .build();

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(mobileDeepLinkService.generateAuthenticationLink(userId)).thenReturn(serviceResponse);

        Response response = resource.generateCode();

        assertEquals(200, response.getStatus());
        ApiResponse<?> apiResponse = (ApiResponse<?>) response.getEntity();
        assertEquals(serviceResponse, apiResponse.getData());
        verify(mobileDeepLinkService).generateAuthenticationLink(userId);
    }
}

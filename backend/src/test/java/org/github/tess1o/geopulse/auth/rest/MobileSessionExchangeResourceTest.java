package org.github.tess1o.geopulse.auth.rest;

import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.model.MobileSessionExchangeRequest;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.auth.service.MobileDeepLinkService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class MobileSessionExchangeResourceTest {

    @Mock
    MobileDeepLinkService mobileDeepLinkService;

    @Mock
    AuthenticationService authenticationService;

    MobileSessionExchangeResource resource;

    @BeforeEach
    void setUp() {
        resource = new MobileSessionExchangeResource();
        resource.mobileDeepLinkService = mobileDeepLinkService;
    }

    @Test
    void exchangeSessionCode_returnsBrowserResponseWithTokens() {
        var accessToken = UUID.randomUUID().toString();
        var refreshToken = UUID.randomUUID().toString();
        var expirationTime = 604800L;

        MobileSessionExchangeRequest request = new MobileSessionExchangeRequest("valid-code");
        AuthResponse authResponse = AuthResponse.builder()
                .id(UUID.randomUUID().toString())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expirationTime)
                .build();

        when(mobileDeepLinkService.exchangeSessionCode("valid-code")).thenReturn(Optional.of(authResponse));

        Response response = resource.exchangeSessionCode(request);

        assertEquals(200, response.getStatus());
        ApiResponse<?> apiResponse = (ApiResponse<?>) response.getEntity();
        var data = (AuthResponse) apiResponse.getData();

        assertEquals(accessToken, data.getAccessToken());
        assertEquals(refreshToken, data.getRefreshToken());
        assertEquals(expirationTime, data.getExpiresIn());
        assertEquals("no-store", response.getHeaderString("Cache-Control"));
        assertEquals("no-cache", response.getHeaderString("Pragma"));
    }

    @Test
    void exchangeSessionCode_returnsGoneWhenSessionCodeInvalid() {
        MobileSessionExchangeRequest request = new MobileSessionExchangeRequest("missing-code");
        when(mobileDeepLinkService.exchangeSessionCode("missing-code")).thenReturn(Optional.empty());

        Response response = resource.exchangeSessionCode(request);

        assertEquals(410, response.getStatus());
        ApiResponse<?> apiResponse = (ApiResponse<?>) response.getEntity();
        assertEquals("Mobile session code is expired or invalid", apiResponse.getMessage());
    }

    @Test
    void exchangeSessionCode_returnsBadRequestWhenBodyIsMissing() {
        Response response = resource.exchangeSessionCode(null);

        assertEquals(400, response.getStatus());
        ApiResponse<?> apiResponse = (ApiResponse<?>) response.getEntity();
        assertNotNull(apiResponse);
        assertEquals("sessionCode is required", apiResponse.getMessage());
    }
}

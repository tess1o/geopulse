package org.github.tess1o.geopulse.auth.rest;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.github.tess1o.geopulse.auth.config.AuthConfigurationService;
import org.github.tess1o.geopulse.auth.dto.AuthStatusResponse;
import org.github.tess1o.geopulse.auth.dto.DemoLoginRequest;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.model.BrowserAuthResponse;
import org.github.tess1o.geopulse.auth.model.LoginRequest;
import org.github.tess1o.geopulse.auth.model.TokenRefreshRequest;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.auth.service.BrowserAuthResponseMapper;
import org.github.tess1o.geopulse.auth.service.CookieService;
import org.github.tess1o.geopulse.auth.service.DemoModeService;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import java.util.Optional;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = "User: Authentication", description = "Login, refresh sessions, logout, and inspect authentication status.")
public class AuthenticationResource {

    private final AuthenticationService authenticationService;
    private final CookieService cookieService;
    private final BrowserAuthResponseMapper browserAuthResponseMapper;
    private final DemoModeService demoModeService;
    private final UserService userService;
    private AuthConfigurationService authConfigurationService;

    @Inject
    public AuthenticationResource(AuthenticationService authenticationService,
                                  CookieService cookieService,
                                  BrowserAuthResponseMapper browserAuthResponseMapper,
                                  AuthConfigurationService authConfigurationService,
                                  DemoModeService demoModeService,
                                  UserService userService) {
        this.authenticationService = authenticationService;
        this.cookieService = cookieService;
        this.browserAuthResponseMapper = browserAuthResponseMapper;
        this.authConfigurationService = authConfigurationService;
        this.demoModeService = demoModeService;
        this.userService = userService;
    }

    /**
     * Login a user and return JWT tokens.
     * This endpoint uses Basic Auth for authentication.
     *
     * @return JWT tokens if authentication is successful
     */
    @POST
    @Path("/sessions")
    @APIResponseSchema(value = BrowserAuthResponse.class, responseCode = "200",
            responseDescription = "Authenticated browser session")
    public Response loginUser(LoginRequest request) {
        if (!authConfigurationService.isPasswordLoginEnabledForUser(request.getEmail())) {
            throw new GeoPulseException(PASSWORD_LOGIN_DISABLED, "Password login is currently disabled");
        }
        AuthResponse authResponse = authenticationService.authenticate(request.getEmail(), request.getPassword());
        return createBrowserLoginResponse(authResponse);
    }

    @POST
    @Path("/demo-sessions")
    @APIResponseSchema(value = BrowserAuthResponse.class, responseCode = "200",
            responseDescription = "Authenticated demo browser session")
    public Response demoLogin(DemoLoginRequest request) {
        if (!demoModeService.isEnabled()) {
            throw new GeoPulseException(DEMO_LOGIN_UNAVAILABLE, "Demo login is not available");
        }

        String personaId = request != null ? request.getPersonaId() : null;
        if (personaId == null || personaId.isBlank()) {
            throw new GeoPulseException(INVALID_DEMO_PERSONA, "Demo persona is required");
        }

        Optional<String> personaEmail = demoModeService.findPersonaEmail(personaId);
        if (personaEmail.isEmpty()) {
            throw new GeoPulseException(DEMO_PERSONA_NOT_FOUND, "Demo persona is not available");
        }

        UserEntity user = userService.findByEmail(personaEmail.get())
                .orElseThrow(() -> new GeoPulseException(DEMO_PERSONA_NOT_FOUND, "Demo user is not available"));
        AuthResponse authResponse = authenticationService.createAuthResponse(user);
        return createBrowserLoginResponse(authResponse);
    }

    /**
     * API Login endpoint that returns JWT tokens directly (for API clients).
     * Unlike the main /login endpoint which uses cookies, this returns tokens in the response body.
     *
     * @return JWT tokens if authentication is successful
     */
    @POST
    @Path("/api-sessions")
    public AuthResponse apiLogin(LoginRequest request) {
        if (!authConfigurationService.isPasswordLoginEnabledForUser(request.getEmail())) {
            throw new GeoPulseException(PASSWORD_LOGIN_DISABLED, "Password login is currently disabled");
        }
        return authenticationService.authenticate(request.getEmail(), request.getPassword());
    }

    /**
     * Refresh an access token using a refresh token (for token-based authentication).
     * This endpoint is primarily used by API clients and tests.
     *
     * @param request The token refresh request
     * @return A new access token if the refresh token is valid
     */
    @POST
    @Path("/api-sessions/current/refresh")
    public org.github.tess1o.geopulse.user.model.RefreshTokenResponse refreshToken(
            @Valid TokenRefreshRequest request) {
        return authenticationService.refreshToken(request.getRefreshToken());
    }

    /**
     * Refresh tokens using cookies.
     * Extracts refresh token from httpOnly cookie and returns new tokens as cookies.
     *
     * @param refreshTokenCookie The refresh token from httpOnly cookie
     * @return Success response with new tokens set as cookies
     */
    @POST
    @Path("/sessions/current/refresh")
    @APIResponse(responseCode = "204", description = "Authentication cookies refreshed")
    public Response refreshTokenCookie(@CookieParam("refresh_token") String refreshTokenCookie) {
        if (refreshTokenCookie == null || refreshTokenCookie.isEmpty()) {
            throw new GeoPulseException(REFRESH_TOKEN_REQUIRED, "No refresh token cookie found");
        }
        var refreshResponse = authenticationService.refreshToken(refreshTokenCookie);

        var newAccessTokenCookie = cookieService.createAccessTokenCookie(
                refreshResponse.accessToken(),
                refreshResponse.expiresIn()
        );
        var newRefreshTokenCookie = cookieService.createRefreshTokenCookie(
                refreshResponse.refreshToken(),
                authenticationService.getRefreshTokenLifespan()
        );
        var newTokenExpirationCookie = cookieService.createTokenExpirationCookie(refreshResponse.expiresIn());

        return Response.noContent()
                .cookie(newAccessTokenCookie)
                .cookie(newRefreshTokenCookie)
                .cookie(newTokenExpirationCookie)
                .build();
    }


    /**
     * Logout user by clearing authentication cookies.
     *
     * @return Success response
     */
    @DELETE
    @Path("/sessions/current")
    @APIResponse(responseCode = "204", description = "Authentication cookies cleared")
    public Response logout() {
        var logoutCookies = cookieService.createLogoutCookies();

        var responseBuilder = Response.noContent();
        for (var cookie : logoutCookies) {
            responseBuilder.cookie(cookie);
        }

        return responseBuilder.build();
    }

    @GET
    @Path("/sessions/current")
    public AuthStatusResponse getAuthStatus() {
        boolean demoModeEnabled = demoModeService.isEnabled();
        AuthStatusResponse status = AuthStatusResponse.builder()
                .passwordRegistrationEnabled(!demoModeEnabled && authConfigurationService.isPasswordRegistrationEnabled())
                .oidcRegistrationEnabled(!demoModeEnabled && authConfigurationService.isOidcRegistrationEnabled())
                .passwordLoginEnabled(authConfigurationService.isPasswordLoginEnabled())
                .oidcLoginEnabled(authConfigurationService.isOidcLoginEnabled())
                .adminLoginBypassEnabled(authConfigurationService.isAdminLoginBypassEnabled())
                .guestRootRedirectToLoginEnabled(authConfigurationService.isGuestRootRedirectToLoginEnabled())
                .demoModeEnabled(demoModeEnabled)
                .demoAdminReadOnlyEnabled(demoModeService.isAdminReadOnlyEnabled())
                .demoPersonas(demoModeService.getPublicPersonas())
                .build();
        return status;
    }

    private Response createBrowserLoginResponse(AuthResponse authResponse) {
        var accessTokenCookie = cookieService.createAccessTokenCookie(authResponse.getAccessToken(), authResponse.getExpiresIn());
        var refreshTokenCookie = cookieService.createRefreshTokenCookie(authResponse.getRefreshToken(), authenticationService.getRefreshTokenLifespan());
        var tokenExpirationCookie = cookieService.createTokenExpirationCookie(authResponse.getExpiresIn());

        return Response.ok(browserAuthResponseMapper.toBrowserAuthResponse(authResponse, null))
                .cookie(accessTokenCookie)
                .cookie(refreshTokenCookie)
                .cookie(tokenExpirationCookie)
                .build();
    }

}

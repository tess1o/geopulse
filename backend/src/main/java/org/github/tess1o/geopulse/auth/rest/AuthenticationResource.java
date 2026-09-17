package org.github.tess1o.geopulse.auth.rest;

import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.github.tess1o.geopulse.auth.config.AuthConfigurationService;
import org.github.tess1o.geopulse.auth.dto.AuthStatusResponse;
import org.github.tess1o.geopulse.auth.dto.DemoLoginRequest;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.model.BrowserAuthResponse;
import org.github.tess1o.geopulse.auth.model.LoginRequest;
import org.github.tess1o.geopulse.auth.model.TokenRefreshRequest;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.auth.service.BrowserAuthResponseMapper;
import org.github.tess1o.geopulse.auth.service.CookieService;
import org.github.tess1o.geopulse.auth.service.DemoModeService;
import org.github.tess1o.geopulse.user.exceptions.UserNotFoundException;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;

import java.util.Optional;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Slf4j
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
    @Path("/login")
    @APIResponseSchema(value = BrowserAuthResponse.class, responseCode = "200",
            responseDescription = "Authenticated browser session")
    public Response loginUser(LoginRequest request) {
        if (!authConfigurationService.isPasswordLoginEnabledForUser(request.getEmail())) {
            log.warn("Password login blocked by configuration for email={}", request.getEmail());
            throw problem(PASSWORD_LOGIN_DISABLED, "Password login is currently disabled");
        }
        try {
            AuthResponse authResponse = authenticationService.authenticate(request.getEmail(), request.getPassword());
            return createBrowserLoginResponse(authResponse);
        } catch (UserNotFoundException e) {
            log.warn("Login failed: user not found for email={}", request.getEmail());
            throw problem(INVALID_CREDENTIALS, "User is not found");
        } catch (InvalidPasswordException e) {
            log.warn("Login failed: invalid password for email={}", request.getEmail());
            throw problem(INVALID_CREDENTIALS, "Invalid password");
        } catch (IllegalArgumentException e) {
            log.warn("Login failed: forbidden for email={}, reason={}", request.getEmail(), e.getMessage());
            throw problem(ACCESS_DENIED, e.getMessage());
        } catch (Exception e) {
            log.error("Authentication failed for user {}", request.getEmail(), e);
            throw problem(AUTHENTICATION_FAILED, "Authentication failed");
        }
    }

    @POST
    @Path("/demo-login")
    @APIResponseSchema(value = BrowserAuthResponse.class, responseCode = "200",
            responseDescription = "Authenticated demo browser session")
    public Response demoLogin(DemoLoginRequest request) {
        if (!demoModeService.isEnabled()) {
            throw problem(DEMO_LOGIN_UNAVAILABLE, "Demo login is not available");
        }

        String personaId = request != null ? request.getPersonaId() : null;
        if (personaId == null || personaId.isBlank()) {
            throw problem(INVALID_DEMO_PERSONA, "Demo persona is required");
        }

        Optional<String> personaEmail = demoModeService.findPersonaEmail(personaId);
        if (personaEmail.isEmpty()) {
            throw problem(DEMO_PERSONA_NOT_FOUND, "Demo persona is not available");
        }

        try {
            UserEntity user = userService.findByEmail(personaEmail.get())
                    .orElseThrow(() -> new UserNotFoundException("Demo user not found"));
            AuthResponse authResponse = authenticationService.createAuthResponse(user);
            return createBrowserLoginResponse(authResponse);
        } catch (UserNotFoundException e) {
            log.warn("Demo login failed: user not found for personaId={}", personaId);
            throw problem(DEMO_PERSONA_NOT_FOUND, "Demo user is not available");
        } catch (IllegalArgumentException e) {
            log.warn("Demo login failed: forbidden for personaId={}, reason={}", personaId, e.getMessage());
            throw problem(ACCESS_DENIED, e.getMessage());
        } catch (Exception e) {
            log.error("Demo login failed for personaId={}", personaId, e);
            throw problem(AUTHENTICATION_FAILED, "Demo login failed");
        }
    }

    /**
     * API Login endpoint that returns JWT tokens directly (for API clients).
     * Unlike the main /login endpoint which uses cookies, this returns tokens in the response body.
     *
     * @return JWT tokens if authentication is successful
     */
    @POST
    @Path("/api-login")
    public AuthResponse apiLogin(LoginRequest request) {
        if (!authConfigurationService.isPasswordLoginEnabledForUser(request.getEmail())) {
            log.warn("API login blocked by configuration for email={}", request.getEmail());
            throw problem(PASSWORD_LOGIN_DISABLED, "Password login is currently disabled");
        }
        try {
            return authenticationService.authenticate(request.getEmail(), request.getPassword());
        } catch (UserNotFoundException e) {
            log.warn("API login failed: user not found for email={}", request.getEmail());
            throw problem(INVALID_CREDENTIALS, "User is not found");
        } catch (InvalidPasswordException e) {
            log.warn("API login failed: invalid password for email={}", request.getEmail());
            throw problem(INVALID_CREDENTIALS, "Invalid password");
        } catch (IllegalArgumentException e) {
            log.warn("API login failed: forbidden for email={}, reason={}", request.getEmail(), e.getMessage());
            throw problem(ACCESS_DENIED, e.getMessage());
        } catch (Exception e) {
            log.error("API authentication failed for user {}", request.getEmail(), e);
            throw problem(AUTHENTICATION_FAILED, "Authentication failed");
        }
    }

    /**
     * Refresh an access token using a refresh token (for token-based authentication).
     * This endpoint is primarily used by API clients and tests.
     *
     * @param request The token refresh request
     * @return A new access token if the refresh token is valid
     */
    @POST
    @Path("/refresh")
    public org.github.tess1o.geopulse.user.model.RefreshTokenResponse refreshToken(
            @Valid TokenRefreshRequest request) {
        try {
            return authenticationService.refreshToken(request.getRefreshToken());
        } catch (ParseException e) {
            log.warn("Token refresh failed: invalid refresh token");
            throw problem(INVALID_REFRESH_TOKEN, "Invalid refresh token");
        } catch (UserNotFoundException e) {
            log.warn("Token refresh failed: user not found");
            throw problem(AUTHENTICATION_REQUIRED, "User is not found");
        } catch (IllegalArgumentException e) {
            log.warn("Token refresh failed: unauthorized reason={}", e.getMessage());
            throw problem(AUTHENTICATION_REQUIRED, e.getMessage());
        } catch (Exception e) {
            log.error("Refresh token request failed", e);
            throw problem(AUTHENTICATION_FAILED, "Token refresh failed");
        }
    }

    /**
     * Refresh tokens using cookies.
     * Extracts refresh token from httpOnly cookie and returns new tokens as cookies.
     *
     * @param refreshTokenCookie The refresh token from httpOnly cookie
     * @return Success response with new tokens set as cookies
     */
    @POST
    @Path("/refresh-cookie")
    @APIResponse(responseCode = "204", description = "Authentication cookies refreshed")
    public Response refreshTokenCookie(@CookieParam("refresh_token") String refreshTokenCookie) {
        if (refreshTokenCookie == null || refreshTokenCookie.isEmpty()) {
            throw problem(REFRESH_TOKEN_REQUIRED, "No refresh token cookie found");
        }
        try {
            // Use existing refresh token logic
            var refreshResponse = authenticationService.refreshToken(refreshTokenCookie);

            // Create new cookies with refreshed tokens
            var newAccessTokenCookie = cookieService.createAccessTokenCookie(
                    refreshResponse.accessToken(),
                    refreshResponse.expiresIn()
            );
            var newRefreshTokenCookie = cookieService.createRefreshTokenCookie(
                    refreshResponse.refreshToken(),
                    authenticationService.getRefreshTokenLifespan() // 7 days
            );
            var newTokenExpirationCookie = cookieService.createTokenExpirationCookie(refreshResponse.expiresIn());

            // Return success response with new cookies
            return Response.noContent()
                    .cookie(newAccessTokenCookie)
                    .cookie(newRefreshTokenCookie)
                    .cookie(newTokenExpirationCookie)
                    .build();

        } catch (ParseException e) {
            throw problem(INVALID_REFRESH_TOKEN, "Invalid refresh token");
        } catch (UserNotFoundException e) {
            throw problem(AUTHENTICATION_REQUIRED, "User is not found");
        } catch (IllegalArgumentException e) {
            throw problem(AUTHENTICATION_REQUIRED, e.getMessage());
        } catch (Exception e) {
            log.error("Cookie-based refresh token request failed", e);
            throw problem(AUTHENTICATION_FAILED, "Token refresh failed");
        }
    }


    /**
     * Logout user by clearing authentication cookies.
     *
     * @return Success response
     */
    @POST
    @Path("/logout")
    @APIResponse(responseCode = "204", description = "Authentication cookies cleared")
    public Response logout() {
        try {
            var logoutCookies = cookieService.createLogoutCookies();

            var responseBuilder = Response.noContent();
            for (var cookie : logoutCookies) {
                responseBuilder.cookie(cookie);
            }

            return responseBuilder.build();
        } catch (Exception e) {
            log.error("Failed to logout", e);
            throw problem(AUTHENTICATION_FAILED, "Logout failed");
        }
    }

    @GET
    @Path("/status")
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

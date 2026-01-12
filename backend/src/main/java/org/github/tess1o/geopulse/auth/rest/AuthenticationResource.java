package org.github.tess1o.geopulse.auth.rest;

import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.config.AuthConfigurationService;
import org.github.tess1o.geopulse.auth.dto.AuthStatusResponse;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.model.LoginRequest;
import org.github.tess1o.geopulse.auth.model.TokenRefreshRequest;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.auth.service.CookieService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.user.exceptions.UserNotFoundException;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Slf4j
public class AuthenticationResource {

    private final AuthenticationService authenticationService;
    private final CookieService cookieService;
    private AuthConfigurationService authConfigurationService;

    @Inject
    public AuthenticationResource(AuthenticationService authenticationService,
                                  CookieService cookieService,
                                  AuthConfigurationService authConfigurationService) {
        this.authenticationService = authenticationService;
        this.cookieService = cookieService;
        this.authConfigurationService = authConfigurationService;
    }

    /**
     * Login a user and return JWT tokens.
     * This endpoint uses Basic Auth for authentication.
     *
     * @return JWT tokens if authentication is successful
     */
    @POST
    @Path("/login")
    public Response loginUser(LoginRequest request) {
        try {
            // Check if password login is enabled (with admin bypass)
            if (!authConfigurationService.isPasswordLoginEnabledForUser(request.getEmail())) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(ApiResponse.error("Password login is currently disabled"))
                        .build();
            }

            AuthResponse authResponse = authenticationService.authenticate(request.getEmail(), request.getPassword());

            // Create cookies for web app
            var accessTokenCookie = cookieService.createAccessTokenCookie(authResponse.getAccessToken(), authResponse.getExpiresIn());
            var refreshTokenCookie = cookieService.createRefreshTokenCookie(authResponse.getRefreshToken(), authenticationService.getRefreshTokenLifespan());
            var tokenExpirationCookie = cookieService.createTokenExpirationCookie(authResponse.getExpiresIn());

            return Response.ok(ApiResponse.success(authResponse))
                    .cookie(accessTokenCookie)
                    .cookie(refreshTokenCookie)
                    .cookie(tokenExpirationCookie)
                    .build();
        } catch (UserNotFoundException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponse.error("User is not found"))
                    .build();
        } catch (InvalidPasswordException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponse.error("Invalid password"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.info("Authentication failed for user {}", request.getEmail(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Authentication failed"))
                    .build();
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
    public Response apiLogin(LoginRequest request) {
        try {
            // Check if password login is enabled (with admin bypass)
            if (!authConfigurationService.isPasswordLoginEnabledForUser(request.getEmail())) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(ApiResponse.error("Password login is currently disabled"))
                        .build();
            }

            AuthResponse authResponse = authenticationService.authenticate(request.getEmail(), request.getPassword());

            // API mode: Return tokens in response body, no cookies
            return Response.ok(ApiResponse.success(authResponse))
                    .build();
        } catch (UserNotFoundException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponse.error("User is not found"))
                    .build();
        } catch (InvalidPasswordException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponse.error("Invalid password"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.info("API authentication failed for user {}", request.getEmail(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Authentication failed"))
                    .build();
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
    public Response refreshToken(@Valid TokenRefreshRequest request) {
        try {
            return Response.ok(authenticationService.refreshToken(request.getRefreshToken())).build();
        } catch (ParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid refresh token"))
                    .build();
        } catch (UserNotFoundException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponse.error("User is not found"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Refresh token request failed", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Token refresh failed"))
                    .build();
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
    public Response refreshTokenCookie(@CookieParam("refresh_token") String refreshTokenCookie) {
        try {
            if (refreshTokenCookie == null || refreshTokenCookie.isEmpty()) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ApiResponse.error("No refresh token cookie found"))
                        .build();
            }

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
            return Response.ok(ApiResponse.success("Tokens refreshed successfully"))
                    .cookie(newAccessTokenCookie)
                    .cookie(newRefreshTokenCookie)
                    .cookie(newTokenExpirationCookie)
                    .build();

        } catch (ParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid refresh token"))
                    .build();
        } catch (UserNotFoundException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponse.error("User is not found"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Cookie-based refresh token request failed", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Token refresh failed"))
                    .build();
        }
    }


    /**
     * Logout user by clearing authentication cookies.
     *
     * @return Success response
     */
    @POST
    @Path("/logout")
    public Response logout() {
        try {
            var logoutCookies = cookieService.createLogoutCookies();

            var responseBuilder = Response.ok(ApiResponse.success("Logged out successfully"));
            for (var cookie : logoutCookies) {
                responseBuilder.cookie(cookie);
            }

            return responseBuilder.build();
        } catch (Exception e) {
            log.error("Failed to logout", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Logout failed"))
                    .build();
        }
    }

    @GET
    @Path("/status")
    public Response getAuthStatus() {
        AuthStatusResponse status = AuthStatusResponse.builder()
                .passwordRegistrationEnabled(authConfigurationService.isPasswordRegistrationEnabled())
                .oidcRegistrationEnabled(authConfigurationService.isOidcRegistrationEnabled())
                .passwordLoginEnabled(authConfigurationService.isPasswordLoginEnabled())
                .oidcLoginEnabled(authConfigurationService.isOidcLoginEnabled())
                .build();
        return Response.ok(ApiResponse.success(status)).build();
    }
}

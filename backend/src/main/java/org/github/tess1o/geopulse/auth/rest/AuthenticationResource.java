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
import org.github.tess1o.geopulse.auth.dto.LogoutResponse;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.model.BrowserAuthResponse;
import org.github.tess1o.geopulse.auth.model.LoginRequest;
import org.github.tess1o.geopulse.auth.model.TokenRefreshRequest;
import org.github.tess1o.geopulse.auth.oidc.service.OidcLogoutService;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.auth.service.BrowserSignInResponder;
import org.github.tess1o.geopulse.auth.service.CookieService;
import org.github.tess1o.geopulse.auth.service.DemoModeService;
import org.github.tess1o.geopulse.auth.service.SessionContextFactory;
import org.github.tess1o.geopulse.auth.session.model.RevokedSession;
import org.github.tess1o.geopulse.auth.session.model.SessionAuthMethod;
import org.github.tess1o.geopulse.auth.session.model.SessionRevocationReason;
import org.github.tess1o.geopulse.auth.session.service.SessionTokenService;
import org.github.tess1o.geopulse.auth.session.service.UserSessionService;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = ApiTags.SESSIONS)
public class AuthenticationResource {

    private final AuthenticationService authenticationService;
    private final CookieService cookieService;
    private final BrowserSignInResponder browserSignInResponder;
    private final DemoModeService demoModeService;
    private final UserService userService;
    private final AuthConfigurationService authConfigurationService;
    private final UserSessionService userSessionService;
    private final OidcLogoutService oidcLogoutService;
    private final SessionContextFactory sessionContextFactory;

    @Inject
    public AuthenticationResource(AuthenticationService authenticationService,
                                  CookieService cookieService,
                                  BrowserSignInResponder browserSignInResponder,
                                  AuthConfigurationService authConfigurationService,
                                  DemoModeService demoModeService,
                                  UserService userService,
                                  UserSessionService userSessionService,
                                  OidcLogoutService oidcLogoutService,
                                  SessionContextFactory sessionContextFactory) {
        this.authenticationService = authenticationService;
        this.cookieService = cookieService;
        this.browserSignInResponder = browserSignInResponder;
        this.authConfigurationService = authConfigurationService;
        this.demoModeService = demoModeService;
        this.userService = userService;
        this.userSessionService = userSessionService;
        this.oidcLogoutService = oidcLogoutService;
        this.sessionContextFactory = sessionContextFactory;
    }

    /**
     * Sign in a browser user and start a session stored in an HttpOnly cookie.
     */
    @POST
    @Path("/sessions")
    @APIResponseSchema(value = BrowserAuthResponse.class, responseCode = "200",
            responseDescription = "Authenticated browser session")
    @Operation(summary = "Sign in with email and password",
            description = "Signs in a browser user. On success the session is stored in a secure HTTP-only "
                    + "session cookie and the response describes the signed-in user. Fails when password login is "
                    + "disabled for this user. API clients that cannot use cookies should call "
                    + "`POST /api/v1/auth/api-sessions` or, better, use an API token.")
    public Response loginUser(LoginRequest request) {
        if (!authConfigurationService.isPasswordLoginEnabledForUser(request.getEmail())) {
            throw new GeoPulseException(PASSWORD_LOGIN_DISABLED, "Password login is currently disabled");
        }
        UserEntity user = authenticationService.verifyCredentials(request.getEmail(), request.getPassword());
        return browserSignInResponder.signIn(user, sessionContextFactory.context(SessionAuthMethod.PASSWORD), null);
    }

    @POST
    @Path("/demo-sessions")
    @APIResponseSchema(value = BrowserAuthResponse.class, responseCode = "200",
            responseDescription = "Authenticated demo browser session")
    @Operation(summary = "Sign in as a demo persona",
            description = "Signs in as one of the demo accounts listed by `GET /api/v1/auth/sessions/current`. Only "
                    + "available when the server runs in demo mode.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
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
        return browserSignInResponder.signIn(user, sessionContextFactory.context(SessionAuthMethod.DEMO), null);
    }

    /**
     * API sign-in: returns an access token and a refresh token in the body instead of a cookie.
     */
    @POST
    @Path("/api-sessions")
    @Operation(summary = "Sign in and get tokens",
            description = "Signs in with email and password and returns the access token and refresh token in the "
                    + "response body instead of cookies. Send the access token as `Authorization: Bearer <token>` "
                    + "and renew it with `POST /api/v1/auth/api-sessions/current/refresh` before it expires. For "
                    + "scripts and integrations, a long-lived API token is usually simpler.")
    public AuthResponse apiLogin(LoginRequest request) {
        if (!authConfigurationService.isPasswordLoginEnabledForUser(request.getEmail())) {
            throw new GeoPulseException(PASSWORD_LOGIN_DISABLED, "Password login is currently disabled");
        }
        UserEntity user = authenticationService.verifyCredentials(request.getEmail(), request.getPassword());
        return authenticationService.createApiSession(user, sessionContextFactory.context(SessionAuthMethod.PASSWORD));
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
    @Operation(summary = "Refresh tokens",
            description = "Exchanges a refresh token obtained from `POST /api/v1/auth/api-sessions` (or the mobile "
                    + "sign-in) for a new access token and refresh token. Each refresh token works once: the "
                    + "previous one stops working, and presenting an already used refresh token again signs the "
                    + "session out.")
    public org.github.tess1o.geopulse.user.model.RefreshTokenResponse refreshToken(
            @Valid TokenRefreshRequest request) {
        return authenticationService.refreshToken(request.getRefreshToken());
    }

    /**
     * Sign out: end the current session and clear the browser cookies. For OIDC browser sessions the
     * response may carry the provider's logout URL (best effort, see {@link OidcLogoutService}).
     */
    @DELETE
    @Path("/sessions/current")
    @APIResponseSchema(value = LogoutResponse.class, responseCode = "200",
            responseDescription = "Session ended and cookies cleared")
    @Operation(summary = "Sign out",
            description = "Ends the current session: the browser session cookie, or the API session whose access "
                    + "token is sent as `Authorization: Bearer`. The session stops working immediately and the "
                    + "session cookies are cleared. When the session was started with an OIDC provider that supports "
                    + "RP-initiated logout, `idpLogoutUrl` is the provider URL that ends the provider session too; "
                    + "otherwise it is null.")
    public Response logout(
            @Parameter(hidden = true) @HeaderParam("Authorization") String authorization,
            @Parameter(hidden = true) @CookieParam(CookieService.SESSION_COOKIE) String sessionCookie) {
        String bearer = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()).trim()
                : null;
        // An API session signs out with its access token; a browser with its session cookie.
        String credential = bearer != null && bearer.startsWith(SessionTokenService.ACCESS_TOKEN_PREFIX)
                ? bearer
                : sessionCookie;
        Optional<RevokedSession> revoked = userSessionService.revokeByToken(credential, SessionRevocationReason.LOGOUT);
        String idpLogoutUrl = revoked.flatMap(oidcLogoutService::buildRpLogoutUrl).orElse(null);

        var responseBuilder = Response.ok(new LogoutResponse(idpLogoutUrl))
                .header("Cache-Control", "no-store");
        for (var cookie : cookieService.createLogoutCookies()) {
            responseBuilder.cookie(cookie);
        }
        return responseBuilder.build();
    }

    @GET
    @Path("/sessions/current")
    @Operation(summary = "Get sign-in options",
            description = "Returns which sign-in and registration methods are enabled on this server (password, "
                    + "OIDC), whether demo mode is on, and the available demo personas. It does not say who is "
                    + "signed in; use `GET /api/v1/users/me` for that.")
    public AuthStatusResponse getAuthStatus() {
        boolean demoModeEnabled = demoModeService.isEnabled();
        AuthStatusResponse status = AuthStatusResponse.builder()
                .passwordRegistrationEnabled(!demoModeEnabled && authConfigurationService.isPasswordRegistrationEnabled())
                .oidcRegistrationEnabled(!demoModeEnabled && authConfigurationService.isOidcRegistrationEnabled())
                .passwordLoginEnabled(authConfigurationService.isPasswordLoginEnabled())
                .oidcLoginEnabled(authConfigurationService.isOidcLoginEnabled())
                .adminLoginBypassEnabled(authConfigurationService.isAdminLoginBypassEnabled())
                .oidcAutoLaunchEnabled(!demoModeEnabled && authConfigurationService.isOidcAutoLaunchEnabled())
                .guestRootRedirectToLoginEnabled(authConfigurationService.isGuestRootRedirectToLoginEnabled())
                .demoModeEnabled(demoModeEnabled)
                .demoAdminReadOnlyEnabled(demoModeService.isAdminReadOnlyEnabled())
                .demoPersonas(demoModeService.getPublicPersonas())
                .build();
        return status;
    }
}

package org.github.tess1o.geopulse.auth.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.admin.service.AdminBootstrapService;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.session.model.IssuedApiSession;
import org.github.tess1o.geopulse.auth.session.model.IssuedBrowserSession;
import org.github.tess1o.geopulse.auth.session.model.SessionAuthMethod;
import org.github.tess1o.geopulse.auth.session.model.SessionContext;
import org.github.tess1o.geopulse.auth.session.service.UserSessionService;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.user.model.RefreshTokenResponse;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;
import org.github.tess1o.geopulse.user.service.UserService;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.ACCESS_DENIED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_CREDENTIALS;

@ApplicationScoped
public class AuthenticationService {

    private final UserService userService;
    private final SecurePasswordUtils securePasswordUtils;
    private final AdminBootstrapService adminBootstrapService;
    private final DemoModeService demoModeService;
    private final UserSessionService userSessionService;

    @Inject
    public AuthenticationService(UserService userService,
                                 SecurePasswordUtils securePasswordUtils,
                                 AdminBootstrapService adminBootstrapService,
                                 DemoModeService demoModeService,
                                 UserSessionService userSessionService) {
        this.userService = userService;
        this.securePasswordUtils = securePasswordUtils;
        this.adminBootstrapService = adminBootstrapService;
        this.demoModeService = demoModeService;
        this.userSessionService = userSessionService;
    }

    /**
     * Verify an email and password. Session creation then checks the user may sign in.
     */
    public UserEntity verifyCredentials(String email, String password) {
        Optional<UserEntity> userOpt = userService.findByEmail(email);
        String storedHash = userOpt.map(UserEntity::getPasswordHash)
                .filter(hash -> !hash.isBlank())
                .orElseGet(securePasswordUtils::dummyPasswordHash);
        boolean passwordValid = securePasswordUtils.isPasswordValid(password, storedHash);
        if (userOpt.isEmpty() || !passwordValid) {
            throw new GeoPulseException(INVALID_CREDENTIALS, "Invalid email or password");
        }
        return userOpt.get();
    }

    /**
     * Checks an authenticated user may sign in: the account is enabled, and the bootstrap admin
     * gets its role. Every session creation goes through here.
     */
    private UserEntity prepareSignIn(UserEntity user) {
        if (!user.isActive()) {
            throw new GeoPulseException(ACCESS_DENIED, "User account is disabled");
        }
        adminBootstrapService.ensureAdminForAuthenticatedUser(user);
        return user;
    }

    /**
     * Password sign-in for API clients: returns an API session (access and refresh token).
     */
    public AuthResponse authenticate(String email, String password) {
        return createApiSession(verifyCredentials(email, password),
                SessionContext.of(SessionAuthMethod.PASSWORD, null, null));
    }

    /**
     * Start an API session (bearer access token plus rotating refresh token) for a signed-in user.
     */
    public AuthResponse createApiSession(UserEntity user, SessionContext context) {
        IssuedApiSession session = userSessionService.createApiSession(prepareSignIn(user), context);
        AuthResponse response = buildProfile(user);
        response.setAccessToken(session.accessToken());
        response.setRefreshToken(session.refreshToken());
        response.setExpiresIn(session.accessTokenExpiresIn());
        return response;
    }

    /**
     * Start a browser session (opaque session cookie) for a signed-in user.
     */
    public BrowserSignIn createBrowserSession(UserEntity user, SessionContext context) {
        IssuedBrowserSession session = userSessionService.createBrowserSession(prepareSignIn(user), context);
        return new BrowserSignIn(buildProfile(user), session);
    }

    public RefreshTokenResponse refreshToken(String refreshToken) {
        IssuedApiSession session = userSessionService.refresh(refreshToken);
        return new RefreshTokenResponse(
                session.accessToken(),
                session.refreshToken(),
                null, // CSRF token not needed - handled by CsrfProtectionFilter
                session.accessTokenExpiresIn()
        );
    }

    private AuthResponse buildProfile(UserEntity user) {
        return AuthResponse.builder()
                .id(user.getId().toString())
                .email(user.getEmail())
                .role(user.getRole().name())
                .demoMode(demoModeService.isEnabled())
                .canViewAdmin(demoModeService.canViewAdmin(user))
                .adminReadOnly(demoModeService.isAdminReadOnly(user))
                .avatar(user.getAvatar())
                .fullName(user.getFullName())
                .timezone(user.getTimezone())
                .createdAt(user.getCreatedAt())
                .hasPassword(user.getPasswordHash() != null && !user.getPasswordHash().isEmpty())
                .uiPreferences(user.getUiPreferences().withDefaults())
                .timelineDisplay(userService.getTimelineDisplaySettings(user))
                .build();
    }

    public String[] extractUsernameAndPassword(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            throw new IllegalArgumentException("Invalid Basic Auth header");
        }

        // Remove "Basic " prefix
        String base64Credentials = authHeader.substring("Basic ".length()).trim();

        // Decode the Base64 string
        byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
        String credentials = new String(decodedBytes, StandardCharsets.UTF_8);

        // Split into username and password
        int colonIndex = credentials.indexOf(':');
        if (colonIndex == -1) {
            throw new IllegalArgumentException("Invalid credentials format");
        }

        String username = credentials.substring(0, colonIndex);
        String password = credentials.substring(colonIndex + 1);

        return new String[]{username, password};
    }

    /** A new browser session and the profile of the user it belongs to. */
    public record BrowserSignIn(AuthResponse profile, IssuedBrowserSession session) {
    }
}

package org.github.tess1o.geopulse.auth.service;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.github.tess1o.geopulse.admin.service.AdminBootstrapService;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.user.model.RefreshTokenResponse;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;
import org.github.tess1o.geopulse.user.service.UserService;
import org.github.tess1o.geopulse.shared.map.MapRenderMode;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.ACCESS_DENIED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.AUTHENTICATION_REQUIRED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_CREDENTIALS;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_REFRESH_TOKEN;

@ApplicationScoped
public class AuthenticationService {

    @Inject
    JWTParser jwtParser;

    @Inject
    @ConfigProperty(name = "smallrye.jwt.new-token.issuer")
    @StaticInitSafe
    String issuer;

    @Inject
    @ConfigProperty(name = "smallrye.jwt.new-token.lifespan", defaultValue = "1800")
    @Getter
    @StaticInitSafe
    Long accessTokenLifespan;

    @Inject
    @ConfigProperty(name = "jwt.refresh-token.lifespan", defaultValue = "604800") // 7 days
    @Getter
    @StaticInitSafe
    Long refreshTokenLifespan;

    private final UserService userService;
    private final SecurePasswordUtils securePasswordUtils;
    private final AdminBootstrapService adminBootstrapService;
    private final DemoModeService demoModeService;

    @Inject
    public AuthenticationService(UserService userService,
                                 SecurePasswordUtils securePasswordUtils,
                                 AdminBootstrapService adminBootstrapService,
                                 DemoModeService demoModeService) {
        this.userService = userService;
        this.securePasswordUtils = securePasswordUtils;
        this.adminBootstrapService = adminBootstrapService;
        this.demoModeService = demoModeService;
    }

    public String createAccessToken(UserEntity user) {
        return Jwt.issuer(issuer)
                .upn(user.getEmail()) // UserPrincipalName, often email
                .subject(user.getId().toString()) // Subject, typically user ID
                .groups(Set.of(user.getRole().name()))
                .claim("type", "access")
                .claim("userId", user.getId().toString())
                .claim("createdAt", user.getCreatedAt().toString() + "Z")
                .expiresIn(Duration.ofSeconds(accessTokenLifespan))
                .sign();
    }

    public String createRefreshToken(UserEntity user) {
        return Jwt.issuer(issuer)
                .upn(user.getEmail()) // UserPrincipalName, often email
                .subject(user.getId().toString()) // Subject, typically user ID
                .groups(Set.of(user.getRole().name()))
                .claim("type", "refresh")
                .claim("userId", user.getId().toString())
                .expiresIn(Duration.ofSeconds(refreshTokenLifespan))
                .sign();
    }

    public AuthResponse authenticate(String email, String password) {
        Optional<UserEntity> userOpt = userService.findByEmail(email);
        String storedHash = userOpt.map(UserEntity::getPasswordHash)
                .filter(hash -> !hash.isBlank())
                .orElseGet(securePasswordUtils::dummyPasswordHash);
        boolean passwordValid = securePasswordUtils.isPasswordValid(password, storedHash);
        if (userOpt.isEmpty() || !passwordValid) {
            throw new GeoPulseException(INVALID_CREDENTIALS, "Invalid email or password");
        }

        UserEntity user = userOpt.get();
        requireActive(user);
        adminBootstrapService.ensureAdminForAuthenticatedUser(user);
        return getAuthResponse(user);
    }

    /**
     * Create an AuthResponse for an already authenticated user (e.g., via OIDC).
     * This bypasses password validation since the user has already been authenticated by external provider.
     */
    public AuthResponse createAuthResponse(UserEntity user) {
        requireActive(user);
        adminBootstrapService.ensureAdminForAuthenticatedUser(user);
        return getAuthResponse(user);
    }

    private static void requireActive(UserEntity user) {
        if (!user.isActive()) {
            throw new GeoPulseException(ACCESS_DENIED, "User account is disabled");
        }
    }

    private AuthResponse getAuthResponse(UserEntity user) {
        String accessToken = createAccessToken(user);
        String refreshToken = createRefreshToken(user);

        return AuthResponse.builder()
                .id(user.getId().toString())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .email(user.getEmail())
                .role(user.getRole().name())
                .demoMode(demoModeService.isEnabled())
                .canViewAdmin(demoModeService.canViewAdmin(user))
                .adminReadOnly(demoModeService.isAdminReadOnly(user))
                .avatar(user.getAvatar())
                .fullName(user.getFullName())
                .timezone(user.getTimezone())
                .createdAt(user.getCreatedAt())
                .expiresIn(accessTokenLifespan)
                .hasPassword(user.getPasswordHash() != null && !user.getPasswordHash().isEmpty())
                .customMapTileUrl(user.getCustomMapTileUrl())
                .customMapStyleUrl(user.getCustomMapStyleUrl())
                .mapRenderMode(user.getMapRenderMode() != null ? user.getMapRenderMode() : MapRenderMode.VECTOR)
                .defaultRedirectUrl(user.getDefaultRedirectUrl())
                .distanceUnit(user.getDistanceUnit())
                .temperatureUnit(user.getTemperatureUnit())
                .dateFormat(user.getDateFormat())
                .timeFormat(user.getTimeFormat())
                .defaultDateRangePreset(user.getDefaultDateRangePreset())
                .autoShowTripReplayControls(user.getTimelineDisplayAutoShowTripReplayControls() != null
                        ? user.getTimelineDisplayAutoShowTripReplayControls() : true)
                .enable3dBuildingsByDefault(Boolean.TRUE.equals(user.getTimelineDisplayEnable3dBuildingsByDefault()))
                .mapMatchingEnabled(userService.isTimelineDisplayMapMatchingEnabled(user))
                .mapMatchingAvailable(userService.isMapMatchingAvailable())
                .build();
    }

    public RefreshTokenResponse refreshToken(String refreshToken) {
        JsonWebToken jwt;
        try {
            jwt = jwtParser.parse(refreshToken);
        } catch (ParseException e) {
            throw new GeoPulseException(INVALID_REFRESH_TOKEN, "Invalid refresh token", e);
        }

        // Validate it's a refresh token
        String tokenType = jwt.getClaim("type");
        if (!"refresh".equals(tokenType)) {
            throw new GeoPulseException(INVALID_REFRESH_TOKEN, "Invalid refresh token");
        }

        // Get user ID from token
        String userIdStr = jwt.getSubject();
        if (userIdStr == null) {
            throw new GeoPulseException(INVALID_REFRESH_TOKEN, "Invalid refresh token");
        }

        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_REFRESH_TOKEN, "Invalid refresh token", e);
        }
        Optional<UserEntity> userOpt = userService.findById(userId);
        if (userOpt.isEmpty()) {
            throw new GeoPulseException(AUTHENTICATION_REQUIRED, "Authentication required");
        }

        UserEntity user = userOpt.get();

        // Check if user is still active (optional security check)
        if (!user.isActive()) {
            throw new GeoPulseException(AUTHENTICATION_REQUIRED, "Authentication required");
        }

        return new RefreshTokenResponse(
                createAccessToken(user),
                createRefreshToken(user),
                null, // CSRF token not needed - handled by Quarkus REST CSRF
                accessTokenLifespan
        );
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
}

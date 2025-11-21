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
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.user.exceptions.UserNotFoundException;
import org.github.tess1o.geopulse.user.model.RefreshTokenResponse;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;
import org.github.tess1o.geopulse.user.service.UserService;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class AuthenticationService {

    @Inject
    JWTParser jwtParser;

    @Inject
    @ConfigProperty(name = "smallrye.jwt.new-token.issuer")
    @StaticInitSafe
    String issuer;

    @Inject
    @ConfigProperty(name = "geopulse.admin.email", defaultValue = "")
    @StaticInitSafe
    String adminEmail;

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

    @Inject
    public AuthenticationService(UserService userService,
                                 SecurePasswordUtils securePasswordUtils) {
        this.userService = userService;
        this.securePasswordUtils = securePasswordUtils;
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
        if (userOpt.isEmpty()) {
            throw new UserNotFoundException("User not found");
        }

        UserEntity user = userOpt.get();

        // Check if user account is active
        if (!user.isActive()) {
            throw new IllegalArgumentException("User account is disabled");
        }

        // Authenticate user
        if (!securePasswordUtils.isPasswordValid(password, user.getPasswordHash())) {
            throw new InvalidPasswordException("Invalid password");
        }

        // Check if user should be promoted to ADMIN based on admin email
        checkAndPromoteToAdmin(user);

        // Generate JWT tokens
        return getAuthResponse(user);
    }

    /**
     * Check if user's email matches admin email and promote to ADMIN role if needed.
     */
    private void checkAndPromoteToAdmin(UserEntity user) {
        if (adminEmail != null && !adminEmail.isBlank() &&
            adminEmail.equalsIgnoreCase(user.getEmail()) &&
            user.getRole() != Role.ADMIN) {
            userService.updateRole(user.getId(), Role.ADMIN);
            user.setRole(Role.ADMIN); // Update in-memory object for JWT generation
            log.info("Promoted existing user {} to ADMIN role (matches admin email)", user.getEmail());
        }
    }

    /**
     * Create an AuthResponse for an already authenticated user (e.g., via OIDC).
     * This bypasses password validation since the user has already been authenticated by external provider.
     */
    public AuthResponse createAuthResponse(UserEntity user) {
        // Check if user account is active
        if (!user.isActive()) {
            throw new IllegalArgumentException("User account is disabled");
        }

        // Check if user should be promoted to ADMIN based on admin email
        checkAndPromoteToAdmin(user);

        // Generate JWT tokens
        return getAuthResponse(user);
    }

    private AuthResponse getAuthResponse(UserEntity user) {
        String accessToken = createAccessToken(user);
        String refreshToken = createRefreshToken(user);

        log.info("User role we send to frontend: {}", user.getRole().name());

        return AuthResponse.builder()
                .id(user.getId().toString())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .email(user.getEmail())
                .role(user.getRole().name())
                .avatar(user.getAvatar())
                .fullName(user.getFullName())
                .timezone(user.getTimezone())
                .createdAt(user.getCreatedAt())
                .expiresIn(accessTokenLifespan)
                .hasPassword(user.getPasswordHash() != null && !user.getPasswordHash().isEmpty())
                .customMapTileUrl(user.getCustomMapTileUrl())
                .defaultRedirectUrl(user.getDefaultRedirectUrl())
                .measureUnit(user.getMeasureUnit())
                .shareLocationWithFriends(user.isShareLocationWithFriends())
                .build();
    }

    public RefreshTokenResponse refreshToken(String refreshToken) throws ParseException {
        // Parse the refresh token
        JsonWebToken jwt = jwtParser.parse(refreshToken);

        // Validate it's a refresh token
        String tokenType = jwt.getClaim("type");
        if (!"refresh".equals(tokenType)) {
            throw new IllegalArgumentException("Invalid token type");
        }

        // Get user ID from token
        String userIdStr = jwt.getSubject();
        if (userIdStr == null) {
            throw new IllegalArgumentException("Invalid token: missing user ID");
        }

        // Find the user
        Optional<UserEntity> userOpt = userService.findById(UUID.fromString(userIdStr));
        if (userOpt.isEmpty()) {
            throw new UserNotFoundException("User not found");
        }

        UserEntity user = userOpt.get();

        // Check if user is still active (optional security check)
        if (!user.isActive()) {
            throw new IllegalArgumentException("User account is deactivated");
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

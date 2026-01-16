package org.github.tess1o.geopulse.auth.oidc.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.auth.config.AuthConfigurationService;
import org.github.tess1o.geopulse.auth.exceptions.OidcLoginDisabledException;
import org.github.tess1o.geopulse.auth.exceptions.OidcRegistrationDisabledException;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.oidc.dto.*;
import org.github.tess1o.geopulse.auth.oidc.model.OidcProviderConfiguration;
import org.github.tess1o.geopulse.auth.oidc.model.OidcSessionStateEntity;
import org.github.tess1o.geopulse.auth.oidc.model.UserOidcConnectionEntity;
import org.github.tess1o.geopulse.auth.oidc.repository.OidcSessionStateRepository;
import org.github.tess1o.geopulse.auth.oidc.repository.UserOidcConnectionRepository;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.user.model.MeasureUnit;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.github.tess1o.geopulse.auth.exceptions.OidcAccountLinkingRequiredException;

import java.net.URI;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class OidcAuthenticationService {

    @Inject
    OidcProviderService providerService;

    @Inject
    UserService userService;

    @Inject
    AuthenticationService authenticationService;

    @Inject
    UserOidcConnectionRepository connectionRepository;

    @Inject
    OidcSessionStateRepository sessionStateRepository;

    @Inject
    AuthConfigurationService authConfigurationService;

    @Inject
    OidcLinkingTokenService linkingTokenService;

    @ConfigProperty(name = "geopulse.oidc.callback-base-url")
    @StaticInitSafe
    String callbackBaseUrl;

    @ConfigProperty(name = "geopulse.oidc.state-token.expiry-minutes", defaultValue = "10")
    @StaticInitSafe
    int stateTokenExpiryMinutes;

    @ConfigProperty(name = "geopulse.admin.email")
    @StaticInitSafe
    Optional<String> adminEmail;

    // Atomic cache entry to prevent split-brain state between JWKS data and timestamp
    private record CachedJwks(JWKSet jwkSet, Instant cachedAt) {}

    private final Client httpClient = ClientBuilder.newClient();
    private final Map<String, CachedJwks> jwksCache = new ConcurrentHashMap<>();

    @ConfigProperty(name = "geopulse.oidc.jwks-cache.ttl-hours", defaultValue = "24")
    @StaticInitSafe
    int jwksCacheTtlHours;

    /**
     * Helper method to persist OIDC connection with race condition handling.
     * Handles duplicate connection attempts by verifying ownership.
     */
    private void persistConnectionWithRaceHandling(UserOidcConnectionEntity connection, String providerName, String externalUserId) {
        try {
            connectionRepository.persist(connection);
        } catch (jakarta.persistence.PersistenceException e) {
            if (e.getCause() instanceof org.hibernate.exception.ConstraintViolationException) {
                log.warn("Duplicate OIDC connection detected. Provider: {}, ExternalUserId: {}. Verifying ownership.",
                        providerName, externalUserId);

                // Re-query to verify the existing connection
                Optional<UserOidcConnectionEntity> existing = connectionRepository
                        .findByProviderNameAndExternalUserId(providerName, externalUserId);

                if (existing.isPresent() && existing.get().getUserId().equals(connection.getUserId())) {
                    // Same user - safe race condition, just update last login
                    existing.get().setLastLoginAt(Instant.now());
                    log.debug("Race condition resolved: connection already exists for same user");
                } else if (existing.isPresent()) {
                    // Different user - this OIDC account is already claimed
                    throw new IllegalArgumentException("This OIDC account is already linked to another user");
                } else {
                    throw new RuntimeException("Unique constraint violated but connection not found", e);
                }
            } else {
                throw e;
            }
        }
    }

    @Transactional
    public OidcLoginInitResponse initiateLogin(String providerName, UUID linkingUserId, String redirectUri, String linkingToken) {
        OidcProviderConfiguration provider = providerService.findByName(providerName)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerName));

        if (!provider.isEnabled()) {
            throw new IllegalArgumentException("Provider not enabled: " + providerName);
        }

        // Validate that provider has valid metadata
        if (provider.getAuthorizationEndpoint() == null || provider.getTokenEndpoint() == null) {
            log.error("Provider '{}' has invalid metadata. Authorization endpoint: {}, Token endpoint: {}",
                    providerName, provider.getAuthorizationEndpoint(), provider.getTokenEndpoint());
            throw new IllegalStateException("Provider metadata is not available. Please check provider configuration and discovery URL.");
        }

        // Generate state and nonce for security
        String state = generateSecureRandomString(32);
        String nonce = generateSecureRandomString(32);

        // Store session state
        OidcSessionStateEntity sessionState = OidcSessionStateEntity.builder()
                .stateToken(state)
                .nonce(nonce)
                .providerName(providerName)
                .redirectUri(redirectUri) // Store where to redirect after authentication
                .linkingUserId(linkingUserId)
                .linkingToken(linkingToken)
                .expiresAt(Instant.now().plus(stateTokenExpiryMinutes, ChronoUnit.MINUTES))
                .build();

        sessionStateRepository.persist(sessionState);

        // Build authorization URL
        String authUrl = buildAuthorizationUrl(provider, state, nonce);

        return OidcLoginInitResponse.builder()
                .authorizationUrl(authUrl)
                .state(state)
                .redirectUri(redirectUri)
                .build();
    }

    @Transactional // Ensure entire callback operation is atomic
    public AuthResponse handleCallback(OidcCallbackRequest request) {
        OidcSessionStateEntity sessionState = null;
        try {
            // Validate state token
            sessionState = sessionStateRepository.findByStateToken(request.getState())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid state token"));

            if (sessionState.getExpiresAt().isBefore(Instant.now())) {
                throw new IllegalArgumentException("State token expired");
            }

            // Get provider configuration
            final String providerName = sessionState.getProviderName();  // Extract to final variable for lambda
            OidcProviderConfiguration provider = providerService.findByName(providerName)
                    .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerName));

            // Exchange code for tokens
            OidcTokenResponse tokenResponse = exchangeCodeForTokens(provider, request.getCode());

            // Validate ID token and extract user info
            OidcUserInfo userInfo = validateAndExtractUserInfo(tokenResponse, provider, sessionState);

            // Check if OIDC login is enabled (with admin bypass for existing users if enabled)
            Optional<UserOidcConnectionEntity> existingConnection = connectionRepository
                    .findByProviderNameAndExternalUserId(providerName, userInfo.getSubject());

            if (existingConnection.isPresent()) {
                // Existing user - check admin bypass if enabled
                UserEntity user = existingConnection.get().getUser();
                boolean isAdmin = user.getRole() == Role.ADMIN;
                boolean bypassEnabled = authConfigurationService.isAdminLoginBypassEnabled();
                if (!authConfigurationService.isOidcLoginEnabled() && !(isAdmin && bypassEnabled)) {
                    throw new OidcLoginDisabledException("OIDC login is currently disabled");
                }
            } else {
                // New user - no bypass
                if (!authConfigurationService.isOidcLoginEnabled()) {
                    throw new OidcLoginDisabledException("OIDC login is currently disabled");
                }
            }

            // Find or create user (this method handles all user/connection logic)
            UserEntity user = findOrCreateUser(userInfo, sessionState);

            // Generate JWT tokens
            return authenticationService.createAuthResponse(user);

        } catch (OidcRegistrationDisabledException e) {
            log.warn("Registration via OIDC is disabled", e);
            throw e;
        } catch (OidcLoginDisabledException e) {
            log.warn("OIDC login is disabled", e);
            throw e;
        } catch (OidcAccountLinkingRequiredException e) {
            // Re-throw the linking exception so the REST controller can handle it properly
            throw e;
        } catch (Exception e) {
            log.error("OIDC callback failed: {}", e.getMessage());
            throw new RuntimeException("OIDC authentication failed.", e);
        } finally {
            // Clean up session state in all cases (success or failure)
            // Single cleanup path eliminates redundant database queries
            if (sessionState != null) {
                try {
                    sessionStateRepository.delete(sessionState);
                } catch (Exception cleanupError) {
                    log.warn("Failed to clean up session state for state token: {}", request.getState(), cleanupError);
                }
            }
        }
    }

    private UserEntity findOrCreateUser(OidcUserInfo userInfo, OidcSessionStateEntity sessionState) {
        // Check if this is an account linking flow
        if (sessionState.getLinkingUserId() != null) {
            return linkExistingAccount(userInfo, sessionState);
        }

        // Check if user already exists with this OIDC connection
        Optional<UserOidcConnectionEntity> existingConnection =
                connectionRepository.findByProviderNameAndExternalUserId(
                        sessionState.getProviderName(), userInfo.getSubject());

        if (existingConnection.isPresent()) {
            // Update last login
            UserOidcConnectionEntity connection = existingConnection.get();
            connection.setLastLoginAt(Instant.now());
            return connection.getUser();
        }

        // Check if user exists with same email
        Optional<UserEntity> existingUser = userService.findByEmail(userInfo.getEmail());

        if (existingUser.isPresent()) {
            UserEntity user = existingUser.get();

            // If auto-link is enabled, automatically link the OIDC account
            if (authConfigurationService.isAutoLinkAccountsEnabled()) {
                log.warn("Auto-linking OIDC account - Provider: {}, Email: {}, User ID: {}. " +
                        "This bypasses verification. Ensure you trust your OIDC provider.",
                        sessionState.getProviderName(), userInfo.getEmail(), user.getId());

                // Check if this OIDC account is already linked to a different user
                Optional<UserOidcConnectionEntity> existingProviderConnection =
                        connectionRepository.findByProviderNameAndExternalUserId(
                                sessionState.getProviderName(), userInfo.getSubject());

                if (existingProviderConnection.isPresent()) {
                    if (!existingProviderConnection.get().getUserId().equals(user.getId())) {
                        throw new IllegalArgumentException(
                                "This OIDC account is already linked to a different user");
                    }
                    // Already linked to this user - just update last login
                    existingProviderConnection.get().setLastLoginAt(Instant.now());
                    return user;
                }

                // Create new OIDC connection for existing user
                UserOidcConnectionEntity connection = UserOidcConnectionEntity.builder()
                        .userId(user.getId())
                        .providerName(sessionState.getProviderName())
                        .externalUserId(userInfo.getSubject())
                        .displayName(userInfo.getName())
                        .avatarUrl(userInfo.getPicture())
                        .lastLoginAt(Instant.now())
                        .build();

                persistConnectionWithRaceHandling(connection, sessionState.getProviderName(), userInfo.getSubject());
                log.info("Auto-linked {} provider to user {}", sessionState.getProviderName(), user.getEmail());
                return user;
            }

            // Auto-link disabled - require account linking verification
            // Get existing OIDC connections for this user
            List<String> linkedProviders = connectionRepository.findByUserId(user.getId())
                    .stream()
                    .map(UserOidcConnectionEntity::getProviderName)
                    .collect(Collectors.toList());

            // Generate secure linking token with original user info
            String linkingToken = linkingTokenService.generateLinkingToken(
                    userInfo.getEmail(),
                    sessionState.getProviderName(),
                    sessionState.getStateToken(),
                    userInfo
            );

            // Throw specialized exception with account linking information
            throw new OidcAccountLinkingRequiredException(
                    userInfo.getEmail(),
                    sessionState.getProviderName(),
                    linkingToken,
                    user.getPasswordHash() != null && !user.getPasswordHash().trim().isEmpty(),
                    linkedProviders
            );
        }

        // Create new user with OIDC connection
        if (!authConfigurationService.isOidcRegistrationEnabled()) {
            throw new OidcRegistrationDisabledException("New user registration via OIDC is disabled.");
        }
        return createNewUserWithOidcConnection(userInfo, sessionState.getProviderName());
    }

    private UserEntity createNewUserWithOidcConnection(OidcUserInfo userInfo, String providerName) {
        // Determine role - check if email matches admin email
        Role role = Role.USER;
        if (adminEmail.isPresent() && !adminEmail.get().isBlank() && adminEmail.get().equalsIgnoreCase(userInfo.getEmail())) {
            role = Role.ADMIN;
            log.info("Promoting OIDC user {} to ADMIN role (matches admin email)", userInfo.getEmail());
        }

        // Create new user (NULL password for OIDC-only users)
        UserEntity user = UserEntity.builder()
                .email(userInfo.getEmail())
                .fullName(userInfo.getName())
                .role(role)
                .isActive(true)
                .emailVerified(true) // OIDC emails are considered verified
                .passwordHash(null) // NULL password hash for OIDC-only users
                .measureUnit(MeasureUnit.METRIC)
                .build();

        userService.persist(user);

        // Create OIDC connection (no redundant email storage)
        UserOidcConnectionEntity connection = UserOidcConnectionEntity.builder()
                .userId(user.getId())
                .providerName(providerName)
                .externalUserId(userInfo.getSubject())
                .displayName(userInfo.getName())
                .avatarUrl(userInfo.getPicture())
                .lastLoginAt(Instant.now())
                .build();

        try {
            connectionRepository.persist(connection);
        } catch (jakarta.persistence.PersistenceException e) {
            // Special handling for new user creation: rollback entire transaction
            if (e.getCause() instanceof org.hibernate.exception.ConstraintViolationException) {
                log.error("Race condition during user creation: OIDC connection already exists. " +
                        "Provider: {}, ExternalUserId: {}. Rolling back transaction.",
                        providerName, userInfo.getSubject());
                throw new IllegalArgumentException(
                        "OIDC account already linked during concurrent registration. Please retry.", e);
            }
            throw e;
        }

        return user;
    }

    private UserEntity linkExistingAccount(OidcUserInfo userInfo, OidcSessionStateEntity sessionState) {
        UserEntity user = userService.findById(sessionState.getLinkingUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found for linking"));

        // Check if this is a verification-for-linking flow (has linking token)
        if (sessionState.getLinkingToken() != null) {
            return handleLinkingVerification(user, sessionState);
        }

        // Regular linking flow - link the current provider
        // Check if this OIDC account is already linked to any user
        Optional<UserOidcConnectionEntity> existingConnection =
                connectionRepository.findByProviderNameAndExternalUserId(
                        sessionState.getProviderName(), userInfo.getSubject());

        if (existingConnection.isPresent()) {
            if (!existingConnection.get().getUserId().equals(user.getId())) {
                throw new IllegalArgumentException("This OIDC account is already linked to another user");
            }
            // Already linked to this user - just update last login
            existingConnection.get().setLastLoginAt(Instant.now());
            return user;
        }

        // Create new OIDC connection for existing user
        UserOidcConnectionEntity connection = UserOidcConnectionEntity.builder()
                .userId(user.getId())
                .providerName(sessionState.getProviderName())
                .externalUserId(userInfo.getSubject())
                .displayName(userInfo.getName())
                .avatarUrl(userInfo.getPicture())
                .lastLoginAt(Instant.now())
                .build();

        persistConnectionWithRaceHandling(connection, sessionState.getProviderName(), userInfo.getSubject());
        return user;
    }

    private UserEntity handleLinkingVerification(UserEntity user, OidcSessionStateEntity sessionState) {
        // Get the original linking data
        OidcLinkingTokenService.LinkingTokenData tokenData = linkingTokenService.validateAndConsumeToken(sessionState.getLinkingToken());
        if (tokenData == null) {
            throw new IllegalArgumentException("Invalid linking token during verification");
        }

        // Create OIDC connection for the ORIGINAL provider (not the verification provider)
        UserOidcConnectionEntity connection = UserOidcConnectionEntity.builder()
                .userId(user.getId())
                .providerName(tokenData.newProvider()) // Use the original provider being linked
                .externalUserId(tokenData.originalUserInfo().getSubject())
                .displayName(tokenData.originalUserInfo().getName())
                .avatarUrl(tokenData.originalUserInfo().getPicture())
                .lastLoginAt(Instant.now())
                .build();

        persistConnectionWithRaceHandling(connection, tokenData.newProvider(), tokenData.originalUserInfo().getSubject());
        log.info("Successfully linked {} provider to user {}", tokenData.newProvider(), user.getEmail());
        return user;
    }

    private String generateSecureRandomString(int length) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String buildAuthorizationUrl(OidcProviderConfiguration provider, String state, String nonce) {
        // Build OAuth2/OIDC authorization URL
        StringBuilder url = new StringBuilder(provider.getAuthorizationEndpoint());
        url.append("?response_type=code");
        url.append("&client_id=").append(provider.getClientId());
        url.append("&redirect_uri=").append(getCallbackUrl());
        url.append("&scope=").append(provider.getScopes().replace(" ", "%20"));
        url.append("&state=").append(state);
        url.append("&nonce=").append(nonce);

        return url.toString();
    }

    private String getCallbackUrl() {
        return callbackBaseUrl + "/oidc/callback";
    }

    private OidcTokenResponse exchangeCodeForTokens(OidcProviderConfiguration provider, String code) {
        String redirectUri = getCallbackUrl();
        log.debug("Exchanging authorization code for tokens with provider: {}", provider.getName());

        Form form = new Form()
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", redirectUri)
                .param("client_id", provider.getClientId())
                .param("client_secret", provider.getClientSecret());

        try (var response = httpClient.target(provider.getTokenEndpoint())
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.form(form))) {

            if (response.getStatus() != 200) {
                String errorBody = response.readEntity(String.class);
                log.error("Failed to exchange code for token. Provider: {}, Status: {}, Body: {}",
                        provider.getName(), response.getStatus(), errorBody);
                throw new RuntimeException("Failed to exchange authorization code for token. Status: " + response.getStatus());
            }

            return response.readEntity(OidcTokenResponse.class);
        }
    }

    private OidcUserInfo validateAndExtractUserInfo(OidcTokenResponse tokenResponse, OidcProviderConfiguration provider, OidcSessionStateEntity sessionState) {
        try {
            log.debug("Validating ID token from provider: {}", provider.getName());
            SignedJWT signedJWT = SignedJWT.parse(tokenResponse.getIdToken());

            // 1. Fetch JWKS and find the correct key
            JWKSet jwkSet = getJwkSet(provider);
            String keyID = signedJWT.getHeader().getKeyID();
            JWK jwk = jwkSet.getKeyByKeyId(keyID);
            if (jwk == null) {
                log.warn("Could not find matching JWK for key ID: {} in provider: {}", keyID, provider.getName());
                throw new SecurityException("Could not find matching JWK for key ID: " + keyID);
            }

            // 2. Verify the token signature
            // TODO: This implementation assumes RSA (RS256) keys. To support other algorithms like ECDSA,
            // a strategy pattern would be needed to select the correct JWSVerifier based on the JWT's "alg" header.
            JWSVerifier verifier = new RSASSAVerifier((RSAKey) jwk);
            if (!signedJWT.verify(verifier)) {
                log.warn("ID token signature validation failed for provider: {}", provider.getName());
                throw new SecurityException("ID token signature validation failed.");
            }

            // 3. Validate the claims
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            // Issuer
            if (!claims.getIssuer().equals(provider.getIssuer())) {
                log.error("ID token issuer mismatch. Provider: {}, Expected: {}, Got: {}",
                        provider.getName(), provider.getIssuer(), claims.getIssuer());
                throw new SecurityException("ID token issuer mismatch.");
            }

            // Audience
            if (!claims.getAudience().contains(provider.getClientId())) {
                log.error("ID token audience mismatch. Provider: {}, Expected: {}, Got: {}",
                        provider.getName(), provider.getClientId(), claims.getAudience());
                throw new SecurityException("ID token audience mismatch.");
            }

            // Expiration
            if (Instant.now().isAfter(claims.getExpirationTime().toInstant())) {
                log.error("ID token expired. Provider: {}, Expiration: {}, Now: {}",
                        provider.getName(), claims.getExpirationTime().toInstant(), Instant.now());
                throw new SecurityException("ID token has expired.");
            }

            // Nonce
            if (sessionState.getNonce() == null || !sessionState.getNonce().equals(claims.getClaim("nonce"))) {
                log.error("ID token nonce mismatch. Provider: {}, Expected: {}, Got: {}",
                        provider.getName(), sessionState.getNonce(), claims.getClaim("nonce"));
                throw new SecurityException("ID token nonce mismatch. Possible replay attack.");
            }

            log.info("ID token validated successfully for subject: {}", claims.getSubject());

            // 4. Extract user info from claims
            String email = claims.getStringClaim("email");
            if (email == null || email.isBlank()) {
                log.warn("ID token missing required 'email' claim for provider: {}", provider.getName());
                throw new SecurityException("ID token missing required 'email' claim");
            }

            return OidcUserInfo.builder()
                    .subject(claims.getSubject())
                    .email(email)
                    .name(claims.getStringClaim("name"))
                    .picture(claims.getStringClaim("picture"))
                    .emailVerified(claims.getBooleanClaim("email_verified"))
                    .build();

        } catch (ParseException | JOSEException e) {
            log.error("Failed to parse or verify ID token: {}", e.getMessage());
            throw new SecurityException("Failed to parse or verify ID token.", e);
        }
    }

    private JWKSet getJwkSet(OidcProviderConfiguration provider) {
        String jwksUri = provider.getJwksUri();
        CachedJwks cached = jwksCache.get(jwksUri);

        // Check if cache is expired or missing
        boolean needsRefresh = cached == null ||
                cached.cachedAt.plus(jwksCacheTtlHours, ChronoUnit.HOURS).isBefore(Instant.now());

        if (needsRefresh) {
            try {
                log.debug("Fetching JWKS from: {} (cache expired or missing)", jwksUri);
                JWKSet jwkSet = JWKSet.load(new URI(jwksUri).toURL());
                // Store both JWKS and timestamp atomically in single object
                jwksCache.put(jwksUri, new CachedJwks(jwkSet, Instant.now()));
                return jwkSet;
            } catch (Exception e) {
                log.error("Failed to load JWKS from URI: {}", jwksUri, e);
                // If we have a stale cached version, use it as fallback
                if (cached != null) {
                    log.warn("Using stale JWKS cache for provider: {} due to fetch failure", provider.getName());
                    return cached.jwkSet;
                }
                throw new RuntimeException("Could not load JWKS for provider: " + provider.getName(), e);
            }
        }

        return cached.jwkSet;
    }
}
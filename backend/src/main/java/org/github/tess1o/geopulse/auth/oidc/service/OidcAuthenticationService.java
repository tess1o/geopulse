package org.github.tess1o.geopulse.auth.oidc.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.oidc.dto.*;
import org.github.tess1o.geopulse.auth.oidc.model.OidcProviderConfiguration;
import org.github.tess1o.geopulse.auth.oidc.model.OidcSessionStateEntity;
import org.github.tess1o.geopulse.auth.oidc.model.UserOidcConnectionEntity;
import org.github.tess1o.geopulse.auth.oidc.repository.OidcSessionStateRepository;
import org.github.tess1o.geopulse.auth.oidc.repository.UserOidcConnectionRepository;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
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
    OidcLinkingTokenService linkingTokenService;
    
    @ConfigProperty(name = "geopulse.oidc.callback-base-url")
    String callbackBaseUrl;
    
    @ConfigProperty(name = "geopulse.oidc.state-token.expiry-minutes", defaultValue = "10")
    int stateTokenExpiryMinutes;

    private final Client httpClient = ClientBuilder.newClient();
    private final Map<String, JWKSet> jwksCache = new ConcurrentHashMap<>();

    @Transactional
    public OidcLoginInitResponse initiateLogin(String providerName, UUID linkingUserId, String redirectUri, String linkingToken) {
        OidcProviderConfiguration provider = providerService.findByName(providerName)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerName));
        
        if (!provider.isEnabled()) {
            throw new IllegalArgumentException("Provider not enabled: " + providerName);
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
        try {
            // Validate state token
            OidcSessionStateEntity sessionState = sessionStateRepository.findByStateToken(request.getState())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid state token"));
            
            if (sessionState.getExpiresAt().isBefore(Instant.now())) {
                // Clean up expired session state
                sessionStateRepository.delete(sessionState);
                throw new IllegalArgumentException("State token expired");
            }
            
            // Get provider configuration
            OidcProviderConfiguration provider = providerService.findByName(sessionState.getProviderName())
                    .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + sessionState.getProviderName()));
            
            // Exchange code for tokens
            OidcTokenResponse tokenResponse = exchangeCodeForTokens(provider, request.getCode());
            
            // Validate ID token and extract user info
            OidcUserInfo userInfo = validateAndExtractUserInfo(tokenResponse, provider, sessionState);
            
            // Find or create user (this method handles all user/connection logic)
            UserEntity user = findOrCreateUser(userInfo, sessionState);
            
            // Clean up session state after successful authentication
            sessionStateRepository.delete(sessionState);
            
            // Generate JWT tokens
            return authenticationService.createAuthResponse(user);
            
        } catch (OidcAccountLinkingRequiredException e) {
            // Clean up session state but let the linking exception bubble up
            try {
                OidcSessionStateEntity sessionState = sessionStateRepository.findByStateToken(request.getState()).orElse(null);
                if (sessionState != null) {
                    sessionStateRepository.delete(sessionState);
                }
            } catch (Exception cleanupError) {
                log.warn("Failed to clean up session state after linking error", cleanupError);
            }
            // Re-throw the linking exception so the REST controller can handle it properly
            throw e;
        } catch (Exception e) {
            // Clean up session state on any error
            try {
                OidcSessionStateEntity sessionState = sessionStateRepository.findByStateToken(request.getState()).orElse(null);
                if (sessionState != null) {
                    sessionStateRepository.delete(sessionState);
                }
            } catch (Exception cleanupError) {
                log.warn("Failed to clean up session state after error", cleanupError);
            }
            // It's better to throw a more specific, less revealing exception to the client
            throw new RuntimeException("OIDC authentication failed.", e);
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
            // Email exists but no OIDC connection - require account linking verification
            UserEntity user = existingUser.get();
            
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
        return createNewUserWithOidcConnection(userInfo, sessionState.getProviderName());
    }
    
    private UserEntity createNewUserWithOidcConnection(OidcUserInfo userInfo, String providerName) {
        // Create new user (NULL password for OIDC-only users)
        UserEntity user = UserEntity.builder()
                .email(userInfo.getEmail())
                .fullName(userInfo.getName())
                .role("USER")
                .isActive(true)
                .emailVerified(true) // OIDC emails are considered verified
                .passwordHash(null) // NULL password hash for OIDC-only users
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
        
        connectionRepository.persist(connection);
        
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
        
        connectionRepository.persist(connection);
        
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
        
        connectionRepository.persist(connection);
        
        log.info("Successfully linked {} provider to user {} after OIDC verification", 
                tokenData.newProvider(), user.getEmail());
        
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
        log.info("Exchanging authorization code for tokens with provider: {}", provider.getName());

        Form form = new Form()
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", getCallbackUrl())
                .param("client_id", provider.getClientId())
                .param("client_secret", provider.getClientSecret());

        try (var response = httpClient.target(provider.getTokenEndpoint())
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.form(form))) {

            if (response.getStatus() != 200) {
                String errorBody = response.readEntity(String.class);
                log.error("Failed to exchange code for token. Provider: {}, Status: {}, Body: {}",
                        provider.getName(), response.getStatus(), errorBody);
                throw new RuntimeException("Failed to exchange authorization code for token.");
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
                throw new SecurityException("Could not find matching JWK for key ID: " + keyID);
            }

            // 2. Verify the token signature
            // TODO: This implementation assumes RSA (RS256) keys. To support other algorithms like ECDSA,
            // a strategy pattern would be needed to select the correct JWSVerifier based on the JWT's "alg" header.
            JWSVerifier verifier = new RSASSAVerifier((RSAKey) jwk);
            if (!signedJWT.verify(verifier)) {
                throw new SecurityException("ID token signature validation failed.");
            }

            // 3. Validate the claims
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            
            // Issuer
            if (!claims.getIssuer().equals(provider.getIssuer())) {
                throw new SecurityException("ID token issuer mismatch.");
            }

            // Audience
            if (!claims.getAudience().contains(provider.getClientId())) {
                throw new SecurityException("ID token audience mismatch.");
            }

            // Expiration
            if (Instant.now().isAfter(claims.getExpirationTime().toInstant())) {
                throw new SecurityException("ID token has expired.");
            }

            // Nonce
            if (sessionState.getNonce() == null || !sessionState.getNonce().equals(claims.getClaim("nonce"))) {
                throw new SecurityException("ID token nonce mismatch. Possible replay attack.");
            }

            log.info("ID token validated successfully for subject: {}", claims.getSubject());

            // 4. Extract user info from claims
            return OidcUserInfo.builder()
                    .subject(claims.getSubject())
                    .email(claims.getStringClaim("email"))
                    .name(claims.getStringClaim("name"))
                    .picture(claims.getStringClaim("picture"))
                    .emailVerified(claims.getBooleanClaim("email_verified"))
                    .build();

        } catch (ParseException | JOSEException e) {
            throw new SecurityException("Failed to parse or verify ID token.", e);
        }
    }

    private JWKSet getJwkSet(OidcProviderConfiguration provider) {
        return jwksCache.computeIfAbsent(provider.getJwksUri(), uri -> {
            try {
                log.info("Fetching JWKS from: {}", uri);
                return JWKSet.load(new URI(uri).toURL());
            } catch (Exception e) {
                log.error("Failed to load JWKS from URI: {}", uri, e);
                throw new RuntimeException("Could not load JWKS for provider: " + provider.getName(), e);
            }
        });
    }
}
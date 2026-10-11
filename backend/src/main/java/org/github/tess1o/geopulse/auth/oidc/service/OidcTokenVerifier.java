package org.github.tess1o.geopulse.auth.oidc.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.auth.oidc.model.OidcProviderConfiguration;

import java.net.URI;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifies JWTs issued by an OIDC provider (ID tokens and back-channel logout tokens): the
 * signature against the provider's published keys (RSA or EC), the issuer and the audience.
 * Token-specific claims are checked by the caller.
 */
@ApplicationScoped
@Slf4j
public class OidcTokenVerifier {

    // Atomic cache entry to prevent split-brain state between JWKS data and timestamp
    private record CachedJwks(JWKSet jwkSet, Instant cachedAt) {}

    private static final Duration FORCED_REFRESH_INTERVAL = Duration.ofMinutes(1);

    private final Map<String, CachedJwks> jwksCache = new ConcurrentHashMap<>();

    @Inject
    SystemSettingsService settingsService;

    @ConfigProperty(name = "geopulse.oidc.jwks-cache.ttl-hours", defaultValue = "24")
    @StaticInitSafe
    int jwksCacheTtlHours;

    public JWTClaimsSet verify(String token, OidcProviderConfiguration provider) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            verifySignature(signedJWT, provider);

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            if (claims.getIssuer() == null || !claims.getIssuer().equals(provider.getIssuer())) {
                log.error("Token issuer mismatch. Provider: {}, Expected: {}, Got: {}",
                        provider.getName(), provider.getIssuer(), claims.getIssuer());
                throw new SecurityException("Token issuer mismatch.");
            }
            if (claims.getAudience() == null || !claims.getAudience().contains(provider.getClientId())) {
                log.error("Token audience mismatch. Provider: {}, Expected: {}, Got: {}",
                        provider.getName(), provider.getClientId(), claims.getAudience());
                throw new SecurityException("Token audience mismatch.");
            }
            return claims;
        } catch (ParseException | JOSEException e) {
            log.error("Failed to parse or verify token from provider {}: {}", provider.getName(), e.getMessage());
            throw new SecurityException("Failed to parse or verify token.", e);
        }
    }

    private void verifySignature(SignedJWT signedJWT, OidcProviderConfiguration provider) throws JOSEException {
        String keyId = signedJWT.getHeader().getKeyID();
        JWK jwk = findKey(getJwkSet(provider, false), keyId);
        if (jwk == null) {
            // The provider may have rotated its keys since they were cached.
            jwk = findKey(getJwkSet(provider, true), keyId);
        }
        if (jwk == null) {
            log.warn("Could not find matching JWK for key ID: {} in provider: {}", keyId, provider.getName());
            throw new SecurityException("Could not find matching JWK for key ID: " + keyId);
        }

        if (!signedJWT.verify(verifierFor(jwk))) {
            log.warn("Token signature validation failed for provider: {}", provider.getName());
            throw new SecurityException("Token signature validation failed.");
        }
    }

    private static JWK findKey(JWKSet jwkSet, String keyId) {
        if (keyId != null) {
            return jwkSet.getKeyByKeyId(keyId);
        }
        // Without a key ID the token can only be matched when the provider publishes a single key.
        List<JWK> keys = jwkSet.getKeys();
        return keys.size() == 1 ? keys.getFirst() : null;
    }

    private static JWSVerifier verifierFor(JWK jwk) throws JOSEException {
        if (jwk instanceof RSAKey rsaKey) {
            return new RSASSAVerifier(rsaKey);
        }
        if (jwk instanceof ECKey ecKey) {
            return new ECDSAVerifier(ecKey);
        }
        throw new SecurityException("Unsupported signing key type: " + jwk.getKeyType());
    }

    private JWKSet getJwkSet(OidcProviderConfiguration provider, boolean forceRefresh) {
        String jwksUri = provider.getJwksUri();
        CachedJwks cached = jwksCache.get(jwksUri);

        int ttlHours = settingsService == null
                ? jwksCacheTtlHours
                : settingsService.getInteger("auth.oidc.jwks-cache.ttl-hours");
        // A forced refresh (unknown key ID) is limited to once a minute, so tokens with made-up key IDs
        // sent to the public back-channel endpoint cannot make GeoPulse hammer the provider.
        boolean forcedRefreshAllowed = forceRefresh
                && (cached == null || cached.cachedAt.plus(FORCED_REFRESH_INTERVAL).isBefore(Instant.now()));
        boolean needsRefresh = forcedRefreshAllowed || cached == null ||
                cached.cachedAt.plus(Math.max(1, ttlHours), ChronoUnit.HOURS).isBefore(Instant.now());

        if (needsRefresh) {
            try {
                log.debug("Fetching JWKS from: {} (cache expired, missing or key not found)", jwksUri);
                JWKSet jwkSet = JWKSet.load(new URI(jwksUri).toURL());
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

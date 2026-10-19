package org.github.tess1o.geopulse.auth.oidc.service;

import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.auth.oidc.model.OidcProviderConfiguration;
import org.github.tess1o.geopulse.auth.session.model.RevokedSession;
import org.github.tess1o.geopulse.auth.session.model.SessionAuthMethod;
import org.github.tess1o.geopulse.auth.session.model.SessionClientType;
import org.github.tess1o.geopulse.auth.session.service.UserSessionService;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.OIDC_LOGOUT_TOKEN_INVALID;

/**
 * Single logout with OIDC providers, in both directions:
 * <ul>
 *   <li>Back-channel logout (provider to GeoPulse): the provider posts a signed logout token and
 *       GeoPulse revokes the matching sessions.</li>
 *   <li>RP-initiated logout (GeoPulse to provider): after a browser signs out of GeoPulse it is sent
 *       to the provider's {@code end_session_endpoint}. Best effort: providers without that endpoint,
 *       or any failure, simply skip this step.</li>
 * </ul>
 */
@ApplicationScoped
@Slf4j
public class OidcLogoutService {

    static final String BACKCHANNEL_LOGOUT_EVENT = "http://schemas.openid.net/event/backchannel-logout";
    static final String RP_LOGOUT_ENABLED_SETTING = "auth.oidc.rp-logout.enabled";

    /** How old a logout token may be, and how far in the future its {@code iat} may lie (clock skew). */
    static final Duration LOGOUT_TOKEN_MAX_AGE = Duration.ofMinutes(5);
    static final Duration ALLOWED_CLOCK_SKEW = Duration.ofMinutes(2);

    @Inject
    OidcProviderService providerService;

    @Inject
    OidcTokenVerifier tokenVerifier;

    @Inject
    OidcAuthenticationService oidcAuthenticationService;

    @Inject
    UserSessionService userSessionService;

    @Inject
    SystemSettingsService settingsService;

    /**
     * Validates a back-channel logout token (OpenID Connect Back-Channel Logout 1.0, section 2.6)
     * and revokes the sessions it names. Returns the number of revoked sessions; zero is a valid
     * outcome (already signed out, or the session belongs to another relying party).
     */
    public int handleBackchannelLogout(String providerName, String logoutToken) {
        if (logoutToken == null || logoutToken.isBlank()) {
            throw invalid("logout_token is required");
        }
        OidcProviderConfiguration provider = providerService.findByName(providerName)
                .filter(OidcProviderConfiguration::isEnabled)
                .orElseThrow(() -> invalid("Unknown OIDC provider"));

        JWTClaimsSet claims;
        try {
            claims = tokenVerifier.verify(logoutToken, provider);
        } catch (SecurityException e) {
            throw new GeoPulseException(OIDC_LOGOUT_TOKEN_INVALID, "Logout token is invalid", e);
        }

        Instant issuedAt = validateLogoutClaims(claims);
        String subject = claims.getSubject();
        String sessionId = stringClaim(claims, "sid");
        return userSessionService.revokeByIdentityProvider(provider.getName(), subject, sessionId,
                issuedAt.plus(ALLOWED_CLOCK_SKEW));
    }

    private Instant validateLogoutClaims(JWTClaimsSet claims) {
        Instant now = Instant.now();
        if (claims.getIssueTime() == null) {
            throw invalid("Logout token has no iat claim");
        }
        Instant issuedAt = claims.getIssueTime().toInstant();
        if (issuedAt.isAfter(now.plus(ALLOWED_CLOCK_SKEW)) || issuedAt.isBefore(now.minus(LOGOUT_TOKEN_MAX_AGE))) {
            throw invalid("Logout token iat is outside the accepted window");
        }
        if (claims.getExpirationTime() != null && claims.getExpirationTime().toInstant().plus(ALLOWED_CLOCK_SKEW).isBefore(now)) {
            throw invalid("Logout token has expired");
        }
        if (claims.getJWTID() == null || claims.getJWTID().isBlank()) {
            throw invalid("Logout token has no jti claim");
        }
        Object events = claims.getClaim("events");
        if (!(events instanceof Map<?, ?> eventMap) || !eventMap.containsKey(BACKCHANNEL_LOGOUT_EVENT)) {
            throw invalid("Logout token is not a back-channel logout event");
        }
        if (claims.getClaim("nonce") != null) {
            throw invalid("Logout token must not contain a nonce");
        }
        if (claims.getSubject() == null && stringClaim(claims, "sid") == null) {
            throw invalid("Logout token has neither sub nor sid");
        }
        return issuedAt;
    }

    /**
     * Builds the URL that ends the identity provider session of a browser session that just signed
     * out, or empty when that is not possible or not wanted. Never throws: GeoPulse logout must not
     * depend on the provider.
     */
    public Optional<String> buildRpLogoutUrl(RevokedSession session) {
        if (session == null
                || session.clientType() != SessionClientType.BROWSER
                || session.authMethod() != SessionAuthMethod.OIDC
                || session.oidcProvider() == null) {
            return Optional.empty();
        }
        try {
            if (!settingsService.getBoolean(RP_LOGOUT_ENABLED_SETTING)) {
                return Optional.empty();
            }
            Optional<OidcProviderConfiguration> provider = providerService.findByName(session.oidcProvider());
            if (provider.isEmpty() || !provider.get().isEnabled()) {
                return Optional.empty();
            }
            String endSessionEndpoint = provider.get().getEndSessionEndpoint();
            if (endSessionEndpoint == null || endSessionEndpoint.isBlank()) {
                log.debug("Provider {} has no end_session_endpoint; skipping RP-initiated logout",
                        session.oidcProvider());
                return Optional.empty();
            }

            StringBuilder url = new StringBuilder(endSessionEndpoint);
            url.append(endSessionEndpoint.contains("?") ? '&' : '?');
            url.append("client_id=").append(encode(provider.get().getClientId()));
            url.append("&post_logout_redirect_uri=").append(encode(oidcAuthenticationService.getPostLogoutRedirectUrl()));
            if (session.idpIdToken() != null && !session.idpIdToken().isBlank()) {
                url.append("&id_token_hint=").append(encode(session.idpIdToken()));
            }
            return Optional.of(url.toString());
        } catch (Exception e) {
            log.warn("Could not build RP-initiated logout URL for provider {}: {}",
                    session.oidcProvider(), e.getMessage());
            return Optional.empty();
        }
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {
        try {
            String value = claims.getStringClaim(name);
            return value == null || value.isBlank() ? null : value;
        } catch (ParseException e) {
            throw invalid("Logout token claim " + name + " is not a string", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static GeoPulseException invalid(String detail) {
        return invalid(detail, null);
    }

    private static GeoPulseException invalid(String detail, Throwable cause) {
        return new GeoPulseException(OIDC_LOGOUT_TOKEN_INVALID, detail, cause);
    }
}

package org.github.tess1o.geopulse.auth.session.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.auth.session.model.*;
import org.github.tess1o.geopulse.auth.session.repository.UserSessionRepository;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_REFRESH_TOKEN;

/**
 * Server-side sessions: the single source of truth for every sign-in.
 *
 * <p>A session ends when it is revoked, when it is idle for longer than the idle timeout, or when
 * it reaches the absolute timeout. Every check reads the database, so revocation takes effect on
 * the next request on any backend instance.</p>
 */
@ApplicationScoped
@Slf4j
public class UserSessionService {

    /** How often {@code last_seen_at} and the idle expiry are written for an active session. */
    static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    /**
     * An already rotated refresh token presented within this window is a client race (two requests
     * refreshing at once), not theft: it is rejected without revoking the session.
     */
    static final Duration REFRESH_REUSE_GRACE = Duration.ofSeconds(30);

    private static final int USER_AGENT_MAX_LENGTH = 500;
    private static final int IP_ADDRESS_MAX_LENGTH = 45;

    @Inject
    UserSessionRepository repository;

    @Inject
    SessionTokenService tokenService;

    @ConfigProperty(name = "geopulse.auth.session.idle-timeout", defaultValue = "7d")
    Duration idleTimeout;

    @ConfigProperty(name = "geopulse.auth.session.absolute-timeout", defaultValue = "90d")
    Duration absoluteTimeout;

    @ConfigProperty(name = "geopulse.auth.session.access-token-lifespan", defaultValue = "30m")
    Duration accessTokenLifespan;

    @Transactional
    public IssuedBrowserSession createBrowserSession(UserEntity user, SessionContext context) {
        Instant now = Instant.now();
        String secret = tokenService.generate(SessionTokenService.BROWSER_SESSION_PREFIX);
        UserSessionEntity session = newSession(user, SessionClientType.BROWSER, context, now);
        session.setTokenHash(tokenService.hash(secret));
        repository.persist(session);
        return new IssuedBrowserSession(session.getId(), secret, absoluteTimeout.toSeconds());
    }

    @Transactional
    public IssuedApiSession createApiSession(UserEntity user, SessionContext context) {
        Instant now = Instant.now();
        UserSessionEntity session = newSession(user, SessionClientType.API, context, now);
        IssuedApiSession issued = issueApiCredentials(session, now);
        repository.persist(session);
        return issued;
    }

    /**
     * Validates a browser session cookie or an API access token. Returns empty when the credential
     * is unknown, of the wrong kind, expired, revoked, or belongs to a disabled user.
     */
    @Transactional
    public Optional<AuthenticatedSession> authenticate(String rawToken) {
        boolean browser = tokenService.isBrowserSession(rawToken);
        if (!browser && !tokenService.isAccessToken(rawToken)) {
            return Optional.empty();
        }

        Optional<UserSessionEntity> found = repository.findByTokenHashWithUser(tokenService.hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        UserSessionEntity session = found.get();
        Instant now = Instant.now();
        SessionClientType expectedType = browser ? SessionClientType.BROWSER : SessionClientType.API;
        if (session.getClientType() != expectedType || !session.isActiveAt(now) || !session.getUser().isActive()) {
            return Optional.empty();
        }
        if (expectedType == SessionClientType.API
                && (session.getAccessExpiresAt() == null || !now.isBefore(session.getAccessExpiresAt()))) {
            return Optional.empty();
        }

        touch(session, now);
        UserEntity user = session.getUser();
        return Optional.of(new AuthenticatedSession(session.getId(), user.getId(), user.getEmail(),
                user.getRole().name(), session.getClientType()));
    }

    /**
     * Rotates an API session's access and refresh tokens. Presenting a refresh token that was
     * already rotated (outside a short grace window) revokes the whole session.
     */
    @Transactional(dontRollbackOn = GeoPulseException.class)
    public IssuedApiSession refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || !rawRefreshToken.startsWith(SessionTokenService.REFRESH_TOKEN_PREFIX)) {
            throw invalidRefreshToken();
        }

        String hash = tokenService.hash(rawRefreshToken);
        Instant now = Instant.now();
        Optional<UserSessionEntity> current = repository.findByRefreshTokenHashForUpdate(hash);
        if (current.isPresent()) {
            UserSessionEntity session = current.get();
            if (!session.isActiveAt(now) || !session.getUser().isActive()) {
                throw invalidRefreshToken();
            }
            session.setPreviousRefreshTokenHash(hash);
            session.setRefreshRotatedAt(now);
            return issueApiCredentials(session, now);
        }

        Optional<UserSessionEntity> rotated = repository.findByPreviousRefreshTokenHash(hash);
        if (rotated.isPresent()) {
            UserSessionEntity session = rotated.get();
            boolean withinGrace = session.getRefreshRotatedAt() != null
                    && now.isBefore(session.getRefreshRotatedAt().plus(REFRESH_REUSE_GRACE));
            if (!withinGrace && session.getRevokedAt() == null) {
                log.warn("Refresh token reuse detected for session {} of user {}; revoking the session",
                        session.getId(), session.getUser().getId());
                revoke(session, SessionRevocationReason.REFRESH_TOKEN_REUSE, now);
            }
        }
        throw invalidRefreshToken();
    }

    @Transactional
    public Optional<RevokedSession> revoke(UUID sessionId, SessionRevocationReason reason) {
        return repository.findByIdOptional(sessionId)
                .filter(session -> session.getRevokedAt() == null)
                .map(session -> revoke(session, reason, Instant.now()));
    }

    /** Revokes the session a raw credential belongs to, even when it no longer authenticates. */
    @Transactional
    public Optional<RevokedSession> revokeByToken(String rawToken, SessionRevocationReason reason) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHashWithUser(tokenService.hash(rawToken))
                .filter(session -> session.getRevokedAt() == null)
                .map(session -> revoke(session, reason, Instant.now()));
    }

    /** Revokes all of a user's sessions, optionally keeping one (the session making the change). */
    @Transactional
    public int revokeAllForUser(UUID userId, UUID exceptSessionId, SessionRevocationReason reason) {
        int revoked = repository.revokeAllForUser(userId, exceptSessionId, reason, Instant.now());
        if (revoked > 0) {
            log.info("Revoked {} session(s) of user {} ({})", revoked, userId, reason);
        }
        return revoked;
    }

    @Transactional
    public int revokeByIdentityProvider(String provider, String subject, String sid, Instant issuedBefore) {
        int revoked = repository.revokeByIdentityProvider(provider, subject, sid, issuedBefore,
                SessionRevocationReason.IDP_LOGOUT, Instant.now());
        log.info("Back-channel logout from provider {} revoked {} session(s)", provider, revoked);
        return revoked;
    }

    @Transactional
    public long deleteEndedBefore(Instant cutoff) {
        return repository.deleteEndedBefore(cutoff);
    }

    private UserSessionEntity newSession(UserEntity user, SessionClientType clientType, SessionContext context,
                                         Instant now) {
        return UserSessionEntity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .clientType(clientType)
                .authMethod(context.authMethod())
                .oidcProvider(context.oidcProvider())
                .idpSubject(context.idpSubject())
                .idpSessionId(context.idpSessionId())
                .idpIdToken(context.idpIdToken())
                .userAgent(truncate(context.userAgent(), USER_AGENT_MAX_LENGTH))
                .ipAddress(truncate(context.ipAddress(), IP_ADDRESS_MAX_LENGTH))
                .createdAt(now)
                .lastSeenAt(now)
                .absoluteExpiresAt(now.plus(absoluteTimeout))
                .idleExpiresAt(earliest(now.plus(idleTimeout), now.plus(absoluteTimeout)))
                .build();
    }

    private IssuedApiSession issueApiCredentials(UserSessionEntity session, Instant now) {
        String accessToken = tokenService.generate(SessionTokenService.ACCESS_TOKEN_PREFIX);
        String refreshToken = tokenService.generate(SessionTokenService.REFRESH_TOKEN_PREFIX);
        session.setTokenHash(tokenService.hash(accessToken));
        session.setRefreshTokenHash(tokenService.hash(refreshToken));
        session.setAccessExpiresAt(earliest(now.plus(accessTokenLifespan), session.getAbsoluteExpiresAt()));
        session.setLastSeenAt(now);
        session.setIdleExpiresAt(earliest(now.plus(idleTimeout), session.getAbsoluteExpiresAt()));
        return new IssuedApiSession(session.getId(), accessToken, refreshToken, accessTokenLifespan.toSeconds());
    }

    private void touch(UserSessionEntity session, Instant now) {
        if (session.getLastSeenAt() != null && now.isBefore(session.getLastSeenAt().plus(TOUCH_INTERVAL))) {
            return;
        }
        session.setLastSeenAt(now);
        session.setIdleExpiresAt(earliest(now.plus(idleTimeout), session.getAbsoluteExpiresAt()));
    }

    private RevokedSession revoke(UserSessionEntity session, SessionRevocationReason reason, Instant now) {
        RevokedSession snapshot = new RevokedSession(session.getId(), session.getClientType(),
                session.getAuthMethod(), session.getOidcProvider(), session.getIdpIdToken());
        session.setRevokedAt(now);
        session.setRevokedReason(reason);
        session.setIdpIdToken(null);
        return snapshot;
    }

    private static Instant earliest(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static GeoPulseException invalidRefreshToken() {
        return new GeoPulseException(INVALID_REFRESH_TOKEN, "Invalid refresh token");
    }

    public long getAccessTokenLifespanSeconds() {
        return accessTokenLifespan.toSeconds();
    }
}

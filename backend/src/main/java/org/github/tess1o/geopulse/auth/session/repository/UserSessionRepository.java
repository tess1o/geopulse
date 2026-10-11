package org.github.tess1o.geopulse.auth.session.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import org.github.tess1o.geopulse.auth.session.model.SessionRevocationReason;
import org.github.tess1o.geopulse.auth.session.model.UserSessionEntity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserSessionRepository implements PanacheRepositoryBase<UserSessionEntity, UUID> {

    public Optional<UserSessionEntity> findByTokenHashWithUser(String tokenHash) {
        return find("FROM UserSessionEntity s JOIN FETCH s.user WHERE s.tokenHash = ?1", tokenHash)
                .firstResultOptional();
    }

    /** Locks the row so concurrent refreshes of the same token are serialized. */
    public Optional<UserSessionEntity> findByRefreshTokenHashForUpdate(String refreshTokenHash) {
        return find("refreshTokenHash", refreshTokenHash)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }

    public Optional<UserSessionEntity> findByPreviousRefreshTokenHash(String refreshTokenHash) {
        return find("previousRefreshTokenHash", refreshTokenHash).firstResultOptional();
    }

    public int revokeAllForUser(UUID userId, UUID exceptSessionId, SessionRevocationReason reason, Instant now) {
        String query = "revokedAt = :now, revokedReason = :reason, idpIdToken = null "
                + "WHERE user.id = :userId AND revokedAt IS NULL"
                + (exceptSessionId != null ? " AND id <> :exceptId" : "");
        Parameters parameters = Parameters.with("now", now)
                .and("reason", reason)
                .and("userId", userId);
        if (exceptSessionId != null) {
            parameters = parameters.and("exceptId", exceptSessionId);
        }
        return update(query, parameters);
    }

    /**
     * Revokes the sessions of one identity provider session, or of one subject when {@code sid} is null.
     * Sessions created after {@code issuedBefore} are kept, so a replayed logout token cannot end a
     * session that started after the logout it describes.
     */
    public int revokeByIdentityProvider(String provider, String subject, String sid, Instant issuedBefore,
                                        SessionRevocationReason reason, Instant now) {
        String match = sid != null ? "idpSessionId = :match" : "idpSubject = :match";
        return update("revokedAt = :now, revokedReason = :reason, idpIdToken = null "
                        + "WHERE oidcProvider = :provider AND " + match
                        + " AND createdAt <= :issuedBefore AND revokedAt IS NULL",
                Parameters.with("now", now)
                        .and("reason", reason)
                        .and("provider", provider)
                        .and("match", sid != null ? sid : subject)
                        .and("issuedBefore", issuedBefore));
    }

    public long deleteEndedBefore(Instant cutoff) {
        return delete("(revokedAt IS NOT NULL AND revokedAt < ?1) OR idleExpiresAt < ?1 OR absoluteExpiresAt < ?1",
                cutoff);
    }
}

package org.github.tess1o.geopulse.auth.session.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * One sign-in. Browser sessions are identified by the session cookie, API sessions by an
 * access token that is rotated together with the refresh token. Only hashes are stored.
 */
@Entity
@Table(name = "user_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionEntity extends PanacheEntityBase {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", nullable = false, length = 16)
    private SessionClientType clientType;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_method", nullable = false, length = 16)
    private SessionAuthMethod authMethod;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "access_expires_at")
    private Instant accessExpiresAt;

    @Column(name = "refresh_token_hash", unique = true, length = 64)
    private String refreshTokenHash;

    @Column(name = "previous_refresh_token_hash", length = 64)
    private String previousRefreshTokenHash;

    @Column(name = "refresh_rotated_at")
    private Instant refreshRotatedAt;

    @Column(name = "oidc_provider", length = 50)
    private String oidcProvider;

    @Column(name = "idp_subject", length = 255)
    private String idpSubject;

    @Column(name = "idp_session_id", length = 255)
    private String idpSessionId;

    @Column(name = "idp_id_token", columnDefinition = "TEXT")
    private String idpIdToken;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "idle_expires_at", nullable = false)
    private Instant idleExpiresAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 32)
    private SessionRevocationReason revokedReason;

    public boolean isActiveAt(Instant now) {
        return revokedAt == null && now.isBefore(idleExpiresAt) && now.isBefore(absoluteExpiresAt);
    }
}

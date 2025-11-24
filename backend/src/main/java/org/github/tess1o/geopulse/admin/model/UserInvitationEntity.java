package org.github.tess1o.geopulse.admin.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_invitations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInvitationEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "used_by")
    private UUID usedBy;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /**
     * Check if the invitation is still valid (not used, not revoked, not expired)
     */
    public boolean isValid() {
        return !used && !revoked && Instant.now().isBefore(expiresAt);
    }

    /**
     * Check if the invitation is expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Get the current status of the invitation
     */
    public InvitationStatus getStatus() {
        if (revoked) {
            return InvitationStatus.REVOKED;
        }
        if (used) {
            return InvitationStatus.USED;
        }
        if (isExpired()) {
            return InvitationStatus.EXPIRED;
        }
        return InvitationStatus.PENDING;
    }
}

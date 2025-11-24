package org.github.tess1o.geopulse.admin.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.admin.model.InvitationStatus;
import org.github.tess1o.geopulse.admin.model.UserInvitationEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserInvitationRepository implements PanacheRepositoryBase<UserInvitationEntity, UUID> {

    /**
     * Find an invitation by its token
     */
    public Optional<UserInvitationEntity> findByToken(String token) {
        return find("token", token).firstResultOptional();
    }

    /**
     * Find all invitations with pagination
     */
    public List<UserInvitationEntity> findAllPaginated(int page, int size) {
        return findAll(Sort.descending("createdAt"))
                .page(page, size)
                .list();
    }

    /**
     * Find invitations by status with pagination
     */
    public List<UserInvitationEntity> findByStatus(InvitationStatus status, int page, int size) {
        Instant now = Instant.now();

        return switch (status) {
            case PENDING -> find(
                    "used = false AND revoked = false AND expiresAt > ?1",
                    Sort.descending("createdAt"),
                    now
            ).page(page, size).list();

            case USED -> find(
                    "used = true",
                    Sort.descending("usedAt")
            ).page(page, size).list();

            case EXPIRED -> find(
                    "used = false AND revoked = false AND expiresAt <= ?1",
                    Sort.descending("expiresAt"),
                    now
            ).page(page, size).list();

            case REVOKED -> find(
                    "revoked = true",
                    Sort.descending("revokedAt")
            ).page(page, size).list();
        };
    }

    /**
     * Count invitations by status
     */
    public long countByStatus(InvitationStatus status) {
        Instant now = Instant.now();

        return switch (status) {
            case PENDING -> count("used = false AND revoked = false AND expiresAt > ?1", now);
            case USED -> count("used = true");
            case EXPIRED -> count("used = false AND revoked = false AND expiresAt <= ?1", now);
            case REVOKED -> count("revoked = true");
        };
    }

    /**
     * Count all invitations
     */
    public long countAll() {
        return count();
    }
}

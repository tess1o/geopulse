package org.github.tess1o.geopulse.sharing.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.sharing.model.ShareType;
import org.github.tess1o.geopulse.sharing.model.SharedLinkEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SharedLinkRepository implements PanacheRepositoryBase<SharedLinkEntity, UUID> {

    public List<SharedLinkEntity> findByUserId(UUID userId) {
        return find("user.id = ?1 ORDER BY createdAt desc", userId).list();
    }

    public long countActiveByUserId(UUID userId) {
        return count("user.id = ?1 and (expiresAt is null or expiresAt > ?2)", userId, Instant.now());
    }

    public Optional<SharedLinkEntity> findActiveById(UUID id) {
        return find("id = ?1 and (expiresAt is null or expiresAt > ?2)", id, Instant.now()).firstResultOptional();
    }

    public Optional<SharedLinkEntity> findByIdAndUserId(UUID id, UUID userId) {
        return find("id = ?1 and user.id = ?2", id, userId).firstResultOptional();
    }

    public void incrementViewCount(UUID id) {
        update("viewCount = viewCount + 1 where id = ?1", id);
    }

    /**
     * Count active shares by user and share type for separate limits
     */
    public long countActiveByUserIdAndType(UUID userId, ShareType shareType) {
        return count("user.id = ?1 and shareType = ?2 and (expiresAt is null or expiresAt > ?3)",
                userId, shareType, Instant.now());
    }

    /**
     * Find all shares by user and type
     */
    public List<SharedLinkEntity> findByUserIdAndType(UUID userId, ShareType shareType) {
        return find("user.id = ?1 and shareType = ?2", userId, shareType).list();
    }
}

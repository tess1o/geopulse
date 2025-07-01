package org.github.tess1o.geopulse.sharing.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.sharing.model.SharedLinkEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SharedLinkRepository implements PanacheRepositoryBase<SharedLinkEntity, UUID> {

    public List<SharedLinkEntity> findByUserId(UUID userId) {
        return find("user.id", userId).list();
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
}

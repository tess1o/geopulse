package org.github.tess1o.geopulse.auth.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.auth.model.MobileAuthCodeEntity;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class MobileAuthCodeRepository implements PanacheRepository<MobileAuthCodeEntity> {

    public Optional<MobileAuthCodeEntity> findByCode(String code) {
        return find("code = ?1 and deletedAt is null", code).firstResultOptional();
    }

    public void delete(MobileAuthCodeEntity entity, Instant deletedAt) {
        entity.markDeleted(deletedAt);
        persist(entity);
    }

    public long deleteExpiredBefore(Instant expiresBefore, Instant deletedAt) {
        return update("deletedAt = ?1 where deletedAt is null and expiresAt < ?2", deletedAt, expiresBefore);
    }
}

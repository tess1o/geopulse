package org.github.tess1o.geopulse.poi.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.poi.model.entity.PoiImageCacheEntity;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class PoiImageCacheRepository implements PanacheRepository<PoiImageCacheEntity> {

    public Optional<PoiImageCacheEntity> findByImageKey(String imageKey) {
        return find("imageKey = ?1", imageKey).firstResultOptional();
    }

    public long deleteExpired(Instant now) {
        return delete("expiresAt <= ?1", now);
    }
}

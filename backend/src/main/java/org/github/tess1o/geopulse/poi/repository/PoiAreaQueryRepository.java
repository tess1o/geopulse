package org.github.tess1o.geopulse.poi.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.poi.model.entity.PoiAreaQueryEntity;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class PoiAreaQueryRepository implements PanacheRepository<PoiAreaQueryEntity> {

    /** Present and unexpired means this area was fetched recently enough to trust the cache. */
    public Optional<PoiAreaQueryEntity> findFresh(String areaHash, Instant now) {
        return find("areaHash = ?1 and expiresAt > ?2", areaHash, now).firstResultOptional();
    }

    public long deleteExpired(Instant now) {
        return delete("expiresAt <= ?1", now);
    }
}

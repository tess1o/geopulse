package org.github.tess1o.geopulse.poi.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.poi.model.entity.PoiCacheEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PoiCacheRepository implements PanacheRepository<PoiCacheEntity> {

    public Optional<PoiCacheEntity> findByProviderAndExternalId(String provider, String externalId) {
        return find("provider = ?1 and externalId = ?2", provider, externalId).firstResultOptional();
    }

    /**
     * Unexpired POIs inside a bounding box. Uses the (latitude, longitude) index; the
     * box is small enough that a range scan is cheap, and the area cache in front of
     * this means the table is only consulted for areas we have already fetched.
     */
    public List<PoiCacheEntity> findUnexpiredInBox(double south, double north,
                                                   double west, double east,
                                                   Instant now, int limit) {
        return find("latitude between ?1 and ?2 and longitude between ?3 and ?4 and expiresAt > ?5",
                south, north, west, east, now)
                .page(0, limit)
                .list();
    }

    public long deleteExpired(Instant now) {
        return delete("expiresAt <= ?1", now);
    }
}

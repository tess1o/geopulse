package org.github.tess1o.geopulse.trips.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TripRepository implements PanacheRepository<TripEntity> {

    public List<TripEntity> findByUserId(UUID userId) {
        return list("user.id = ?1 ORDER BY startTime DESC", userId);
    }

    public Optional<TripEntity> findByIdAndUserId(Long id, UUID userId) {
        return find("id = ?1 and user.id = ?2", id, userId).firstResultOptional();
    }

    public Optional<TripEntity> findByPeriodTagIdAndUserId(Long periodTagId, UUID userId) {
        return find("periodTag.id = ?1 and user.id = ?2", periodTagId, userId).firstResultOptional();
    }
}

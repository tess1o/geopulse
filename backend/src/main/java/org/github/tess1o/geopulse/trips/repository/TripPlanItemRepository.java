package org.github.tess1o.geopulse.trips.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TripPlanItemRepository implements PanacheRepository<TripPlanItemEntity> {

    public List<TripPlanItemEntity> findByTripId(Long tripId) {
        return list("trip.id = ?1 ORDER BY orderIndex ASC, createdAt ASC", tripId);
    }

    public Optional<TripPlanItemEntity> findByIdAndTripId(Long itemId, Long tripId) {
        return find("id = ?1 and trip.id = ?2", itemId, tripId).firstResultOptional();
    }

    public long countByTripId(Long tripId) {
        return count("trip.id = ?1", tripId);
    }
}

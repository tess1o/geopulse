package org.github.tess1o.geopulse.trips.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.trips.model.entity.TripPlaceVisitMatchEntity;

@ApplicationScoped
public class TripPlaceVisitMatchRepository implements PanacheRepository<TripPlaceVisitMatchEntity> {

    public long deleteByTripIdAndPlanItemId(Long tripId, Long planItemId) {
        return delete("trip.id = ?1 and planItem.id = ?2", tripId, planItemId);
    }
}


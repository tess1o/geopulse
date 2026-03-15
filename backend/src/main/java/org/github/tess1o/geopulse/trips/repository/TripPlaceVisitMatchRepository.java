package org.github.tess1o.geopulse.trips.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.trips.model.entity.TripPlaceVisitMatchEntity;

import java.util.List;

@ApplicationScoped
public class TripPlaceVisitMatchRepository implements PanacheRepository<TripPlaceVisitMatchEntity> {

    public long deleteByTripIdAndPlanItemId(Long tripId, Long planItemId) {
        return delete("trip.id = ?1 and planItem.id = ?2", tripId, planItemId);
    }

    public List<TripPlaceVisitMatchEntity> findByTripId(Long tripId) {
        return find("""
                SELECT m
                FROM TripPlaceVisitMatchEntity m
                LEFT JOIN FETCH m.stay
                JOIN FETCH m.planItem
                WHERE m.trip.id = ?1
                ORDER BY m.planItem.id ASC, m.createdAt DESC
                """, tripId).list();
    }
}

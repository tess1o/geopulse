package org.github.tess1o.geopulse.trips.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemEntity;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class TripPlanItemRepository implements PanacheRepository<TripPlanItemEntity> {

    public List<TripPlanItemEntity> findByTripId(Long tripId) {
        // The one ordering rule shared with the frontend rail and the map route: planned day first
        // (unscheduled last), then the position within the day.
        return list("trip.id = ?1 ORDER BY plannedDay ASC NULLS LAST, orderIndex ASC, createdAt ASC, id ASC", tripId);
    }

    public long[] countSummary(Long tripId) {
        Object[] row = getEntityManager().createQuery("""
                select count(p), coalesce(sum(case when p.isVisited = true then 1 else 0 end), 0)
                from TripPlanItemEntity p where p.trip.id = :tripId
                """, Object[].class).setParameter("tripId", tripId).getSingleResult();
        return new long[]{((Number) row[0]).longValue(), ((Number) row[1]).longValue()};
    }

    public Optional<TripPlanItemEntity> findByIdAndTripId(Long itemId, Long tripId) {
        return find("id = ?1 and trip.id = ?2", itemId, tripId).firstResultOptional();
    }

    /** Next free position, so a new or re-dated stop lands at the end of its day. */
    public int nextOrderIndex(Long tripId) {
        Integer max = getEntityManager()
                .createQuery("SELECT MAX(p.orderIndex) FROM TripPlanItemEntity p WHERE p.trip.id = :tripId", Integer.class)
                .setParameter("tripId", tripId)
                .getSingleResult();
        return max == null ? 0 : max + 1;
    }
}

package org.github.tess1o.geopulse.insight.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.DistanceTraveled;
import org.github.tess1o.geopulse.timeline.model.TravelMode;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DistanceCalculationService {

    private final EntityManager entityManager;

    public DistanceCalculationService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public DistanceTraveled calculateDistanceTraveled(UUID userId) {
        String sql = """
                select movement_type, sum(distance_km)
                from timeline_trips
                where user_id = :userId
                group by 1
                """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userId", userId);

        List<Object[]> results = query.getResultList();

        if (results.isEmpty()) {
            return new DistanceTraveled(0, 0);
        }

        int car = 0;
        int walk = 0;

        for (Object[] result : results) {
            TravelMode movementType = TravelMode.valueOf((String) result[0]);
            Double distanceKm = (Double) result[1];
            if (distanceKm == null) {
                distanceKm = 0.0d;
            }
            if (movementType == TravelMode.CAR || movementType == TravelMode.UNKNOWN) {
                car = car + distanceKm.intValue();
            }
            if (movementType == TravelMode.WALKING) {
                walk = walk + distanceKm.intValue();
            }
        }
        return new DistanceTraveled(car, walk);
    }
}
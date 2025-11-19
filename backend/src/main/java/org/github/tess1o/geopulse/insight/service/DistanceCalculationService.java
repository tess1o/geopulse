package org.github.tess1o.geopulse.insight.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.DistanceTraveled;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;

import java.math.BigDecimal;
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
                select movement_type, sum(distance_meters)
                from timeline_trips
                where user_id = :userId
                group by 1
                """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userId", userId);

        List<Object[]> results = query.getResultList();

        if (results.isEmpty()) {
            return new DistanceTraveled(0, 0, 0, 0, 0, 0);
        }

        int car = 0;
        int walk = 0;
        int bicycle = 0;
        int train = 0;
        int flight = 0;
        int unknown = 0;

        for (Object[] result : results) {
            TripType movementType = TripType.valueOf((String) result[0]);
            Long distanceMeters = ((BigDecimal) result[1]).longValue();
            if (distanceMeters == null) {
                distanceMeters = 0L;
            }
            // Convert meters to kilometers for display
            int distanceKm = (int) (distanceMeters / 1000);
            switch (movementType) {
                case CAR -> car += distanceKm;
                case WALK -> walk += distanceKm;
                case BICYCLE -> bicycle += distanceKm;
                case TRAIN -> train += distanceKm;
                case FLIGHT -> flight += distanceKm;
                case UNKNOWN -> unknown += distanceKm;
            }
        }
        return new DistanceTraveled(car, walk, bicycle, train, flight, unknown);
    }
}
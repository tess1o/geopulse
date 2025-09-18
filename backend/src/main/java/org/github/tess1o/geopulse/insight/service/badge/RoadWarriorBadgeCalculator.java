package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

//Traveled 1,000+ km total
@ApplicationScoped
public class RoadWarriorBadgeCalculator implements BadgeCalculator {

    private static final int TOTAL_DISTANCE_THRESHOLD = 1000;

    public static final String TOTAL_DISTANCE_QUERY = """
            SELECT sum(distance_meters)
            FROM timeline_trips
            WHERE user_id = :userId
            """;
    private final EntityManager entityManager;

    public RoadWarriorBadgeCalculator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public String getBadgeId() {
        return "road_warrior";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        Query distanceQuery = entityManager.createNativeQuery(TOTAL_DISTANCE_QUERY);
        distanceQuery.setParameter("userId", userId);

        Number result = (Number) distanceQuery.getSingleResult();
        int totalDistance = result != null ? result.intValue() : 0;

        return Badge.builder()
                .id("road_warrior")
                .current(totalDistance)
                .icon("\uD83D\uDE97")
                .title("Road Warrior")
                .description("Travel 5,000+ km total")
                .progress(totalDistance >= TOTAL_DISTANCE_THRESHOLD ? 100 : (totalDistance * 100) / TOTAL_DISTANCE_THRESHOLD)
                .target(TOTAL_DISTANCE_THRESHOLD)
                .earned(totalDistance >= TOTAL_DISTANCE_THRESHOLD)
                .build();
    }
}

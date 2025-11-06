package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class BusyBeeBadgeCalculator implements BadgeCalculator {

    private static final int TRIPS_THRESHOLD = 50;
    private static final String TITLE = "Busy Bee";

    private static final String TRIP_DATES_QUERY = """
            SELECT DATE(t.timestamp AT TIME ZONE u.timezone) as trip_date
            FROM timeline_trips t
            JOIN users u ON t.user_id = u.id
            WHERE t.user_id = :userId
            ORDER BY trip_date ASC
            """;

    private final EntityManager entityManager;

    public BusyBeeBadgeCalculator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public String getBadgeId() {
        return "busy_bee";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        Query query = entityManager.createNativeQuery(TRIP_DATES_QUERY);
        query.setParameter("userId", userId);

        List<LocalDate> tripDates = (List<LocalDate>) query.getResultList();

        if (tripDates == null || tripDates.isEmpty()) {
            return Badge.builder()
                    .id(getBadgeId())
                    .icon("🐝")
                    .title(TITLE)
                    .description("Take 50 trips within one month")
                    .earned(false)
                    .current(0)
                    .target(TRIPS_THRESHOLD)
                    .progress(0)
                    .build();
        }

        // Count trips per month
        Map<YearMonth, Integer> tripsByMonth = new HashMap<>();
        for (LocalDate tripDate : tripDates) {
            YearMonth yearMonth = YearMonth.from(tripDate);
            tripsByMonth.put(yearMonth, tripsByMonth.getOrDefault(yearMonth, 0) + 1);
        }

        // Find the month with the most trips
        int maxTripsInMonth = 0;
        YearMonth maxMonth = null;
        for (Map.Entry<YearMonth, Integer> entry : tripsByMonth.entrySet()) {
            if (entry.getValue() > maxTripsInMonth) {
                maxTripsInMonth = entry.getValue();
                maxMonth = entry.getKey();
            }
        }

        boolean earned = maxTripsInMonth >= TRIPS_THRESHOLD;

        return Badge.builder()
                .id(getBadgeId())
                .icon("🐝")
                .title(TITLE)
                .description("Take 50 trips within one month")
                .earned(earned)
                .current(maxTripsInMonth)
                .target(TRIPS_THRESHOLD)
                .progress(maxTripsInMonth >= TRIPS_THRESHOLD ? 100 : (maxTripsInMonth * 100) / TRIPS_THRESHOLD)
                .earnedDate(earned && maxMonth != null ? maxMonth.atDay(1).format(DateTimeFormatter.ISO_DATE) : null)
                .build();
    }
}

package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@ApplicationScoped
public class FirstMonthBadgeCalculator implements BadgeCalculator {

    private final EntityManager entityManager;

    public FirstMonthBadgeCalculator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public String getBadgeId() {
        return "first_month";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        int[] trackingData = getTrackingDays(userId);
        int trackedDays = trackingData[0];
        boolean firstMonthComplete = trackedDays >= 30;

        return new Badge(
                getBadgeId(),
                "First Month Complete!",
                "Successfully tracked for 30 days",
                "🎉",
                firstMonthComplete,
                firstMonthComplete ? getTrackingStartDate(userId) : null,
                null, null, null
        );
    }

    private int[] getTrackingDays(UUID userId) {
        String firstDateSql = """
                SELECT MIN(DATE(timestamp))
                FROM gps_points
                WHERE user_id = :userId
                """;

        Query firstDateQuery = entityManager.createNativeQuery(firstDateSql);
        firstDateQuery.setParameter("userId", userId);

        java.sql.Date firstDate = (java.sql.Date) firstDateQuery.getSingleResult();
        if (firstDate == null) {
            return new int[]{0, 0};
        }

        LocalDate startDate = firstDate.toLocalDate();
        LocalDate today = LocalDate.now();
        int totalDays = (int) ChronoUnit.DAYS.between(startDate, today) + 1;

        String trackedDaysSql = """
                SELECT COUNT(DISTINCT DATE(timestamp))
                FROM gps_points
                WHERE user_id = :userId
                """;

        Query trackedDaysQuery = entityManager.createNativeQuery(trackedDaysSql);
        trackedDaysQuery.setParameter("userId", userId);

        Number result = (Number) trackedDaysQuery.getSingleResult();
        int trackedDays = result != null ? result.intValue() : 0;

        return new int[]{trackedDays, totalDays};
    }

    private String getTrackingStartDate(UUID userId) {
        String sql = """
                SELECT MIN(timestamp)
                FROM gps_points
                WHERE user_id = :userId
                """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userId", userId);

        java.sql.Timestamp result = (java.sql.Timestamp) query.getSingleResult();
        if (result == null) {
            return "No tracking data";
        }

        LocalDate startDate = LocalDateTime.ofInstant(result.toInstant(), ZoneOffset.UTC).toLocalDate();
        return startDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));
    }
}
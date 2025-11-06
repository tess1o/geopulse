package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Shared calculator for consecutive trip days badges with different thresholds.
 * Used by DailyHabit badges with various day requirements.
 */
@ApplicationScoped
public class ConsecutiveTripDaysBadgeCalculator {

    private static final String TRIP_DATES_QUERY = """
            SELECT DISTINCT DATE(t.timestamp AT TIME ZONE u.timezone) as trip_date
            FROM timeline_trips t
            JOIN users u ON t.user_id = u.id
            WHERE t.user_id = :userId
            ORDER BY trip_date ASC
            """;

    private final EntityManager entityManager;

    public ConsecutiveTripDaysBadgeCalculator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Badge calculateConsecutiveTripDaysBadge(UUID userId, String badgeId, String title, String icon,
                                                     int thresholdDays, String description) {
        Query query = entityManager.createNativeQuery(TRIP_DATES_QUERY);
        query.setParameter("userId", userId);

        List<LocalDate> tripDates = (List<LocalDate>) query.getResultList();

        if (tripDates == null || tripDates.isEmpty()) {
            return Badge.builder()
                    .id(badgeId)
                    .icon(icon)
                    .title(title)
                    .description(description)
                    .earned(false)
                    .current(0)
                    .target(thresholdDays)
                    .progress(0)
                    .build();
        }

        // Find the longest consecutive streak
        int maxStreak = 1;
        int currentStreak = 1;
        LocalDate streakStartDate = null;
        LocalDate maxStreakStartDate = tripDates.get(0);

        for (int i = 1; i < tripDates.size(); i++) {
            LocalDate currentDate = tripDates.get(i);
            LocalDate previousDate = tripDates.get(i - 1);

            if (currentDate.equals(previousDate.plusDays(1))) {
                currentStreak++;
                if (streakStartDate == null) {
                    streakStartDate = previousDate;
                }
                if (currentStreak > maxStreak) {
                    maxStreak = currentStreak;
                    maxStreakStartDate = streakStartDate;
                }
            } else {
                currentStreak = 1;
                streakStartDate = null;
            }
        }

        boolean earned = maxStreak >= thresholdDays;

        return Badge.builder()
                .id(badgeId)
                .icon(icon)
                .title(title)
                .description(description)
                .earned(earned)
                .current(maxStreak)
                .target(thresholdDays)
                .progress(maxStreak >= thresholdDays ? 100 : (maxStreak * 100) / thresholdDays)
                .earnedDate(earned ? maxStreakStartDate.format(DateTimeFormatter.ISO_DATE) : null)
                .build();
    }
}

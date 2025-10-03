package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class WeekStreakBadgeCalculator implements BadgeCalculator {

    private static final String TRACKING_DATES_QUERY = """
            SELECT DISTINCT DATE(timestamp AT TIME ZONE u.timezone) as tracking_date
            FROM gps_points g
            JOIN users u ON g.user_id = u.id
            WHERE g.user_id = :userId
            ORDER BY tracking_date ASC
            """;

    private final EntityManager entityManager;

    public WeekStreakBadgeCalculator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public String getBadgeId() {
        return "week_streak";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        Query query = entityManager.createNativeQuery(TRACKING_DATES_QUERY);
        query.setParameter("userId", userId);

        List<Date> trackingDates = (List<Date>) query.getResultList();

        if (trackingDates == null || trackingDates.isEmpty()) {
            return Badge.builder()
                    .id("week_streak")
                    .icon("⏰")
                    .title("Week Streak")
                    .description("Track trips for 7 consecutive days")
                    .earned(false)
                    .build();
        }

        // Find the longest consecutive streak
        int maxStreak = 1;
        int currentStreak = 1;
        LocalDate streakStartDate = null;
        LocalDate maxStreakStartDate = trackingDates.get(0).toLocalDate();

        for (int i = 1; i < trackingDates.size(); i++) {
            LocalDate currentDate = trackingDates.get(i).toLocalDate();
            LocalDate previousDate = trackingDates.get(i - 1).toLocalDate();

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

        boolean earned = maxStreak >= 7;

        return Badge.builder()
                .id("week_streak")
                .icon("⏰")
                .title("Week Streak")
                .description("Track trips for 7 consecutive days")
                .earned(earned)
                .earnedDate(earned ? maxStreakStartDate.format(DateTimeFormatter.ISO_DATE) : null)
                .build();
    }
}

package org.github.tess1o.geopulse.insight.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.TimePatterns;

import java.time.*;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class TimePatternService {

    private final EntityManager entityManager;

    public TimePatternService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public TimePatterns calculateTimePatterns(UUID userId) {
        String mostActiveMonth = getMostActiveMonth(userId);
        String monthlyComparison = getMonthlyComparison(userId);
        String busiestDayOfWeek = getBusiestDayOfWeek(userId);
        String dayInsight = getDayInsight(busiestDayOfWeek);
        String mostActiveTime = getMostActiveTime(userId);
        String timeInsight = getTimeInsight(mostActiveTime);

        return new TimePatterns(
                mostActiveMonth,
                monthlyComparison,
                busiestDayOfWeek,
                dayInsight,
                mostActiveTime,
                timeInsight
        );
    }

    private String getMostActiveMonth(UUID userId) {
        String sql = """
                SELECT 
                    EXTRACT(YEAR FROM timestamp) as year,
                    EXTRACT(MONTH FROM timestamp) as month,
                    COUNT(*) as activity_count
                FROM (
                    SELECT timestamp FROM timeline_stays WHERE user_id = :userId
                    UNION ALL
                    SELECT timestamp FROM timeline_trips WHERE user_id = :userId
                ) activities
                GROUP BY year, month
                ORDER BY activity_count DESC
                LIMIT 1
                """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userId", userId);

        List<Object[]> results = query.getResultList();
        if (results.isEmpty()) {
            return "No activity recorded";
        }

        Object[] result = results.get(0);
        int year = ((Number) result[0]).intValue();
        int month = ((Number) result[1]).intValue();

        Month monthEnum = Month.of(month);
        return monthEnum.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;
    }

    private String getMonthlyComparison(UUID userId) {
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        Instant currentMonthStart = currentMonth.atStartOfDay().toInstant(ZoneOffset.UTC);

        String currentMonthSql = """
                SELECT COUNT(*)
                FROM (
                    SELECT timestamp FROM timeline_stays WHERE user_id = :userId AND timestamp >= :currentMonth
                    UNION ALL
                    SELECT timestamp FROM timeline_trips WHERE user_id = :userId AND timestamp >= :currentMonth
                ) current_activities
                """;

        Query currentQuery = entityManager.createNativeQuery(currentMonthSql);
        currentQuery.setParameter("userId", userId);
        currentQuery.setParameter("currentMonth", currentMonthStart);

        int currentMonthActivity = ((Number) currentQuery.getSingleResult()).intValue();

        String avgSql = """
                SELECT AVG(monthly_count)
                FROM (
                    SELECT 
                        EXTRACT(YEAR FROM timestamp) as year,
                        EXTRACT(MONTH FROM timestamp) as month,
                        COUNT(*) as monthly_count
                    FROM (
                        SELECT timestamp FROM timeline_stays WHERE user_id = :userId
                        UNION ALL
                        SELECT timestamp FROM timeline_trips WHERE user_id = :userId
                    ) all_activities
                    GROUP BY year, month
                ) monthly_stats
                """;

        Query avgQuery = entityManager.createNativeQuery(avgSql);
        avgQuery.setParameter("userId", userId);

        Number avgResult = (Number) avgQuery.getSingleResult();
        double avgMonthlyActivity = avgResult != null ? avgResult.doubleValue() : 0;

        if (avgMonthlyActivity == 0) {
            return "First month of tracking";
        }

        double percentageChange = ((currentMonthActivity - avgMonthlyActivity) / avgMonthlyActivity) * 100;

        if (percentageChange > 0) {
            return String.format("%.0f%% more active than average", percentageChange);
        } else {
            return String.format("%.0f%% less active than average", Math.abs(percentageChange));
        }
    }

    private String getBusiestDayOfWeek(UUID userId) {
        String sql = """
                SELECT 
                    EXTRACT(DOW FROM timestamp) as day_of_week,
                    COUNT(*) as activity_count
                FROM (
                    SELECT timestamp FROM timeline_stays WHERE user_id = :userId
                    UNION ALL
                    SELECT timestamp FROM timeline_trips WHERE user_id = :userId
                ) activities
                GROUP BY day_of_week
                ORDER BY activity_count DESC
                LIMIT 1
                """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userId", userId);

        List<Object[]> results = query.getResultList();
        if (results.isEmpty()) {
            return "No activity recorded";
        }

        Object[] result = results.get(0);
        int dowValue = ((Number) result[0]).intValue();

        DayOfWeek dayOfWeek = DayOfWeek.of(dowValue == 0 ? 7 : dowValue);
        return dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private String getDayInsight(String dayOfWeek) {
        switch (dayOfWeek.toLowerCase()) {
            case "saturday":
            case "sunday":
                return "Perfect for weekend adventures!";
            case "friday":
                return "Ready for the weekend!";
            case "monday":
                return "Starting the week strong!";
            default:
                return "Making the most of midweek!";
        }
    }

    private String getMostActiveTime(UUID userId) {
        String sql = """
                SELECT 
                    EXTRACT(HOUR FROM timestamp) as hour,
                    COUNT(*) as activity_count
                FROM (
                    SELECT timestamp FROM timeline_stays WHERE user_id = :userId
                    UNION ALL
                    SELECT timestamp FROM timeline_trips WHERE user_id = :userId
                ) activities
                GROUP BY hour
                ORDER BY activity_count DESC
                LIMIT 1
                """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userId", userId);

        List<Object[]> results = query.getResultList();
        if (results.isEmpty()) {
            return "No activity recorded";
        }

        Object[] result = results.get(0);
        int hour = ((Number) result[0]).intValue();

        return String.format("%d:%02d %s",
                hour == 0 ? 12 : (hour > 12 ? hour - 12 : hour),
                30,
                hour < 12 ? "AM" : "PM"
        );
    }

    private String getTimeInsight(String activeTime) {
        if (activeTime.contains("AM")) {
            return "Early bird explorer";
        } else if (activeTime.contains("PM")) {
            String hourStr = activeTime.split(":")[0];
            int hour = Integer.parseInt(hourStr);
            if (hour >= 12 && hour < 6) {
                return "Afternoon explorer";
            } else {
                return "Evening adventurer";
            }
        }
        return "Active explorer";
    }
}
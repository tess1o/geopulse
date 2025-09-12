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
        LocalDate now = LocalDate.now();
        int dayOfMonth = now.getDayOfMonth();
        
        // For early days of the month, show encouraging message instead of misleading comparison
        if (dayOfMonth < 15) {
            return "Early days - keep exploring!";
        }
        
        // Get current month activity count
        int currentMonthActivity = getCurrentMonthActivityCount(userId);
        if (currentMonthActivity == 0) {
            return "No activity recorded this month yet";
        }
        
        // Get best month details for rate comparison
        BestMonthDetails bestMonth = getBestMonthDetails(userId);
        if (bestMonth == null || bestMonth.activityCount == 0) {
            return "First month of tracking";
        }
        
        // Calculate daily rates for fair comparison
        double currentRate = (double) currentMonthActivity / dayOfMonth;
        double bestRate = (double) bestMonth.activityCount / bestMonth.daysInMonth;
        
        if (bestRate == 0) {
            return "Building your activity history";
        }
        
        double rateComparison = ((currentRate - bestRate) / bestRate) * 100;
        
        if (Math.abs(rateComparison) < 5) {
            return "Similar pace to your best month";
        } else if (rateComparison > 0) {
            return String.format("%.0f%% faster pace than your best month!", rateComparison);
        } else {
            return String.format("%.0f%% slower pace than your best month", Math.abs(rateComparison));
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

    private int getCurrentMonthActivityCount(UUID userId) {
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

        return ((Number) currentQuery.getSingleResult()).intValue();
    }

    private BestMonthDetails getBestMonthDetails(UUID userId) {
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
            return null;
        }

        Object[] result = results.get(0);
        int year = ((Number) result[0]).intValue();
        int month = ((Number) result[1]).intValue();
        int activityCount = ((Number) result[2]).intValue();
        
        // Calculate days in that specific month
        YearMonth yearMonth = YearMonth.of(year, month);
        int daysInMonth = yearMonth.lengthOfMonth();
        
        return new BestMonthDetails(year, month, activityCount, daysInMonth);
    }

    private static class BestMonthDetails {
        final int year;
        final int month;
        final int activityCount;
        final int daysInMonth;
        
        BestMonthDetails(int year, int month, int activityCount, int daysInMonth) {
            this.year = year;
            this.month = month;
            this.activityCount = activityCount;
            this.daysInMonth = daysInMonth;
        }
    }
}
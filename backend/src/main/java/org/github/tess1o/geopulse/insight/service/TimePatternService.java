package org.github.tess1o.geopulse.insight.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.TimePatterns;
import org.github.tess1o.geopulse.shared.api.MessageDescriptor;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All display text here is returned either as locale-neutral raw data (year-month, ISO day-of-week,
 * hour of day) for the frontend to format per the active locale, or as a {@link MessageDescriptor}
 * (see {@code shared/api/MessageDescriptor.java}) for the frontend to translate by key -- mirroring
 * how {@code digest.service.milestone.MilestoneEvaluator} handles its own English fallback text.
 */
@ApplicationScoped
public class TimePatternService {

    private static final String KEY_PREFIX = "insights.patterns.";

    private final EntityManager entityManager;

    public TimePatternService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public TimePatterns calculateTimePatterns(UUID userId) {
        String mostActiveYearMonth = getMostActiveYearMonth(userId);
        MessageDescriptor monthlyComparison = getMonthlyComparison(userId);
        Integer busiestDayOfWeek = getBusiestDayOfWeek(userId);
        MessageDescriptor dayInsight = getDayInsight(busiestDayOfWeek);
        Integer mostActiveHour = getMostActiveHour(userId);
        MessageDescriptor timeInsight = getTimeInsight(mostActiveHour);

        return new TimePatterns(
                mostActiveYearMonth,
                monthlyComparison,
                busiestDayOfWeek,
                dayInsight,
                mostActiveHour,
                timeInsight
        );
    }

    private String getMostActiveYearMonth(UUID userId) {
        BestMonthDetails bestMonth = getBestMonthDetails(userId);
        if (bestMonth == null) {
            return null;
        }
        return YearMonth.of(bestMonth.year, bestMonth.month).toString();
    }

    private MessageDescriptor getMonthlyComparison(UUID userId) {
        LocalDate now = LocalDate.now();
        int dayOfMonth = now.getDayOfMonth();

        // For early days of the month, show encouraging message instead of misleading comparison
        if (dayOfMonth < 15) {
            return descriptor("monthlyComparison.earlyDays", Map.of(), "Early days - keep exploring!");
        }

        // Get current month activity count
        int currentMonthActivity = getCurrentMonthActivityCount(userId);
        if (currentMonthActivity == 0) {
            return descriptor("monthlyComparison.noActivity", Map.of(), "No activity recorded this month yet");
        }

        // Get best month details for rate comparison
        BestMonthDetails bestMonth = getBestMonthDetails(userId);
        if (bestMonth == null || bestMonth.activityCount == 0) {
            return descriptor("monthlyComparison.noBaseline", Map.of(), "First month of tracking");
        }

        // Calculate daily rates for fair comparison
        double currentRate = (double) currentMonthActivity / dayOfMonth;
        double bestRate = (double) bestMonth.activityCount / bestMonth.daysInMonth;

        if (bestRate == 0) {
            return descriptor("monthlyComparison.noRate", Map.of(), "Building your activity history");
        }

        double rateComparison = ((currentRate - bestRate) / bestRate) * 100;

        if (Math.abs(rateComparison) < 5) {
            return descriptor("monthlyComparison.similar", Map.of(), "Similar pace to your best month");
        } else if (rateComparison > 0) {
            long percent = Math.round(rateComparison);
            return descriptor("monthlyComparison.faster", Map.of("percent", percent),
                    String.format("%d%% faster pace than your best month!", percent));
        } else {
            long percent = Math.round(Math.abs(rateComparison));
            return descriptor("monthlyComparison.slower", Map.of("percent", percent),
                    String.format("%d%% slower pace than your best month", percent));
        }
    }

    private Integer getBusiestDayOfWeek(UUID userId) {
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

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        if (results.isEmpty()) {
            return null;
        }

        Object[] result = results.get(0);
        int dowValue = ((Number) result[0]).intValue();

        // Postgres EXTRACT(DOW ...) is 0 (Sunday) .. 6 (Saturday); DayOfWeek is ISO-8601, 1 (Monday) ..
        // 7 (Sunday).
        return DayOfWeek.of(dowValue == 0 ? 7 : dowValue).getValue();
    }

    private MessageDescriptor getDayInsight(Integer isoDayOfWeek) {
        if (isoDayOfWeek == null) {
            return descriptor("dayInsight.noActivity", Map.of(), "Keep exploring to find your pattern!");
        }
        DayOfWeek dayOfWeek = DayOfWeek.of(isoDayOfWeek);
        return switch (dayOfWeek) {
            case SATURDAY, SUNDAY -> descriptor("dayInsight.weekend", Map.of(), "Perfect for weekend adventures!");
            case FRIDAY -> descriptor("dayInsight.friday", Map.of(), "Ready for the weekend!");
            case MONDAY -> descriptor("dayInsight.monday", Map.of(), "Starting the week strong!");
            default -> descriptor("dayInsight.midweek", Map.of(), "Making the most of midweek!");
        };
    }

    private Integer getMostActiveHour(UUID userId) {
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

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        if (results.isEmpty()) {
            return null;
        }

        Object[] result = results.get(0);
        return ((Number) result[0]).intValue();
    }

    private MessageDescriptor getTimeInsight(Integer hour) {
        if (hour == null) {
            return descriptor("timeInsight.noActivity", Map.of(), "Keep exploring to find your pattern!");
        }
        // Preserves the original (12-hour-string-based) categorization exactly: any hour before noon
        // reads as "early bird", every hour from noon onward reads as "evening adventurer" -- the
        // previous code also branched on an "afternoon" case, but its condition (`hour >= 12 && hour
        // < 6` on an already-12-hour-clamped value) could never be true, so afternoon hours always
        // fell through to the evening message. Kept as-is rather than changed as a drive-by fix.
        if (hour < 12) {
            return descriptor("timeInsight.earlyBird", Map.of(), "Early bird explorer");
        }
        return descriptor("timeInsight.evening", Map.of(), "Evening adventurer");
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

        @SuppressWarnings("unchecked")
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

    private static MessageDescriptor descriptor(String keySuffix, Map<String, Object> params, String fallback) {
        return new MessageDescriptor(KEY_PREFIX + keySuffix, params, fallback);
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

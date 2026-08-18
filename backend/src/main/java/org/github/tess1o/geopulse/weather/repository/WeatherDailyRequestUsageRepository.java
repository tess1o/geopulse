package org.github.tess1o.geopulse.weather.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@ApplicationScoped
public class WeatherDailyRequestUsageRepository {

    @Inject
    EntityManager entityManager;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean tryReserve(WeatherTargetSource source, int dailyLimit, int ongoingReserve) {
        return tryReserve(source, 1, dailyLimit, ongoingReserve);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean tryReserve(WeatherTargetSource source, int calls, int dailyLimit, int ongoingReserve) {
        boolean ongoing = source == WeatherTargetSource.ONGOING;
        int requestedCalls = Math.max(1, calls);
        int allowed = ongoing
                ? Math.max(0, dailyLimit)
                : Math.max(0, dailyLimit - Math.max(0, ongoingReserve));
        if (allowed <= 0) {
            return false;
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                INSERT INTO weather_daily_request_usage (
                    usage_date, request_count, ongoing_request_count, backfill_request_count, updated_at
                )
                SELECT ?1, ?2, ?3, ?4, NOW()
                WHERE ?2 <= ?5
                ON CONFLICT (usage_date) DO UPDATE
                SET request_count = weather_daily_request_usage.request_count + EXCLUDED.request_count,
                    ongoing_request_count = weather_daily_request_usage.ongoing_request_count + EXCLUDED.ongoing_request_count,
                    backfill_request_count = weather_daily_request_usage.backfill_request_count + EXCLUDED.backfill_request_count,
                    updated_at = NOW()
                WHERE weather_daily_request_usage.request_count + ?2 <= ?5
                RETURNING request_count
                """)
                .setParameter(1, today)
                .setParameter(2, requestedCalls)
                .setParameter(3, ongoing ? requestedCalls : 0)
                .setParameter(4, ongoing ? 0 : requestedCalls)
                .setParameter(5, allowed)
                .getResultList();
        return !rows.isEmpty();
    }

    public WeatherDailyRequestUsage today() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT usage_date, request_count, ongoing_request_count, backfill_request_count
                FROM weather_daily_request_usage
                WHERE usage_date = ?1
                """)
                .setParameter(1, today)
                .getResultList();
        if (rows.isEmpty()) {
            return WeatherDailyRequestUsage.empty(today);
        }
        Object[] row = rows.getFirst();
        LocalDate date = row[0] instanceof LocalDate value
                ? value
                : ((Date) row[0]).toLocalDate();
        return new WeatherDailyRequestUsage(
                date,
                ((Number) row[1]).longValue(),
                ((Number) row[2]).longValue(),
                ((Number) row[3]).longValue()
        );
    }

    @Transactional
    public long deleteBefore(LocalDate cutoff) {
        return entityManager.createNativeQuery("DELETE FROM weather_daily_request_usage WHERE usage_date < ?1")
                .setParameter(1, cutoff)
                .executeUpdate();
    }
}

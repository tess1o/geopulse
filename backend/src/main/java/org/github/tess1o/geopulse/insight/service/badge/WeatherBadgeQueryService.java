package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.insight.service.WeatherInsightService;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class WeatherBadgeQueryService {

    private static final String COUNT_SAMPLES_QUERY = """
            SELECT COUNT(*)
            FROM weather_samples
            WHERE user_id = :userId
            """;

    private static final String FIRST_SAMPLE_QUERY = """
            SELECT observed_at
            FROM weather_samples
            WHERE user_id = :userId
            ORDER BY observed_at ASC
            LIMIT 1
            """;

    private static final String COUNT_RAINY_SAMPLES_QUERY = """
            SELECT COUNT(*)
            FROM weather_samples
            WHERE user_id = :userId
              AND precipitation > 0
            """;

    private static final String NTH_RAINY_SAMPLE_QUERY = """
            SELECT observed_at
            FROM weather_samples
            WHERE user_id = :userId
              AND precipitation > 0
            ORDER BY observed_at ASC
            LIMIT 1 OFFSET :offset
            """;

    private static final String MAX_TEMPERATURE_QUERY = """
            SELECT MAX(temperature)
            FROM weather_samples
            WHERE user_id = :userId
            """;

    private static final String FIRST_HEATWAVE_SAMPLE_QUERY = """
            SELECT observed_at
            FROM weather_samples
            WHERE user_id = :userId
              AND temperature >= :threshold
            ORDER BY observed_at ASC
            LIMIT 1
            """;

    private static final String MIN_TEMPERATURE_QUERY = """
            SELECT MIN(temperature)
            FROM weather_samples
            WHERE user_id = :userId
            """;

    private static final String FIRST_FROST_SAMPLE_QUERY = """
            SELECT observed_at
            FROM weather_samples
            WHERE user_id = :userId
              AND temperature <= :threshold
            ORDER BY observed_at ASC
            LIMIT 1
            """;

    private static final String SEASON_SAMPLE_QUERY = """
            SELECT DATE(ws.observed_at AT TIME ZONE COALESCE(u.timezone, 'UTC')) AS local_date,
                   ws.observed_at
            FROM weather_samples ws
            JOIN users u ON ws.user_id = u.id
            WHERE ws.user_id = :userId
            ORDER BY ws.observed_at ASC
            """;

    private final EntityManager entityManager;

    public WeatherBadgeQueryService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public long countSamples(UUID userId) {
        return count(COUNT_SAMPLES_QUERY, userId);
    }

    public Instant firstSampleAt(UUID userId) {
        return firstInstant(FIRST_SAMPLE_QUERY, userId);
    }

    public long countRainySamples(UUID userId) {
        return count(COUNT_RAINY_SAMPLES_QUERY, userId);
    }

    public Instant nthRainySampleAt(UUID userId, int count) {
        if (count <= 0) {
            return null;
        }
        Object result = entityManager.createNativeQuery(NTH_RAINY_SAMPLE_QUERY)
                .setParameter("userId", userId)
                .setParameter("offset", count - 1)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);
        return TimestampUtils.getInstantSafe(result);
    }

    public Double maxTemperature(UUID userId) {
        return singleDouble(MAX_TEMPERATURE_QUERY, userId);
    }

    public Instant firstSampleAtOrAboveTemperature(UUID userId, double threshold) {
        Object result = entityManager.createNativeQuery(FIRST_HEATWAVE_SAMPLE_QUERY)
                .setParameter("userId", userId)
                .setParameter("threshold", threshold)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);
        return TimestampUtils.getInstantSafe(result);
    }

    public Double minTemperature(UUID userId) {
        return singleDouble(MIN_TEMPERATURE_QUERY, userId);
    }

    public Instant firstSampleAtOrBelowTemperature(UUID userId, double threshold) {
        Object result = entityManager.createNativeQuery(FIRST_FROST_SAMPLE_QUERY)
                .setParameter("userId", userId)
                .setParameter("threshold", threshold)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);
        return TimestampUtils.getInstantSafe(result);
    }

    public WeatherSeasonCoverage seasonCoverage(UUID userId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(SEASON_SAMPLE_QUERY)
                .setParameter("userId", userId)
                .getResultList();

        Set<String> seasons = new HashSet<>();
        Instant completedAt = null;
        for (Object[] row : rows) {
            if (row == null || row.length < 2) {
                continue;
            }
            String season = WeatherInsightService.meteorologicalSeason(toLocalDate(row[0]));
            if (season == null || !seasons.add(season)) {
                continue;
            }
            if (seasons.size() == 4) {
                completedAt = TimestampUtils.getInstantSafe(row[1]);
                break;
            }
        }

        return new WeatherSeasonCoverage(seasons.size(), completedAt);
    }

    private long count(String sql, UUID userId) {
        Object result = entityManager.createNativeQuery(sql)
                .setParameter("userId", userId)
                .getSingleResult();
        return result instanceof Number number ? number.longValue() : 0L;
    }

    private Instant firstInstant(String sql, UUID userId) {
        Object result = entityManager.createNativeQuery(sql)
                .setParameter("userId", userId)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);
        return TimestampUtils.getInstantSafe(result);
    }

    private Double singleDouble(String sql, UUID userId) {
        Object result = entityManager.createNativeQuery(sql)
                .setParameter("userId", userId)
                .getSingleResult();
        return result instanceof Number number ? number.doubleValue() : null;
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return value == null ? null : LocalDate.parse(String.valueOf(value));
    }

    public record WeatherSeasonCoverage(int seasonsCount, Instant completedAt) {
    }
}

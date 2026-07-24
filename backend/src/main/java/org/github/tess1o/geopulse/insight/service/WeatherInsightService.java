package org.github.tess1o.geopulse.insight.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.insight.model.WeatherConditionInsight;
import org.github.tess1o.geopulse.insight.model.WeatherDailyPrecipitationInsight;
import org.github.tess1o.geopulse.insight.model.WeatherInsights;
import org.github.tess1o.geopulse.insight.model.WeatherSampleInsight;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;
import org.github.tess1o.geopulse.weather.service.WeatherConfigurationService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class WeatherInsightService {

    private static final String WEATHER_SAMPLE_QUERY = """
            SELECT ws.requested_latitude,
                   ws.requested_longitude,
                   ws.observed_at,
                   ws.weather_code,
                   ws.temperature,
                   ws.precipitation,
                   ws.snowfall,
                   ws.wind_speed,
                   DATE(ws.observed_at AT TIME ZONE COALESCE(u.timezone, 'UTC')) AS local_date
            FROM weather_samples ws
            JOIN users u ON ws.user_id = u.id
            WHERE ws.user_id = :userId
            ORDER BY ws.observed_at ASC
            """;

    private final EntityManager entityManager;
    private final WeatherConfigurationService weatherConfigurationService;

    public WeatherInsightService(EntityManager entityManager,
                                 WeatherConfigurationService weatherConfigurationService) {
        this.entityManager = entityManager;
        this.weatherConfigurationService = weatherConfigurationService;
    }

    public WeatherInsights calculateWeatherInsights(UUID userId) {
        if (userId == null || !weatherConfigurationService.isEnabled()) {
            return null;
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(WEATHER_SAMPLE_QUERY)
                .setParameter("userId", userId)
                .getResultList();
        if (rows == null || rows.isEmpty()) {
            return null;
        }

        List<WeatherSampleRow> samples = rows.stream()
                .map(this::toSampleRow)
                .filter(Objects::nonNull)
                .toList();
        if (samples.isEmpty()) {
            return null;
        }

        WeatherSampleRow coldest = samples.stream()
                .filter(sample -> sample.temperature() != null)
                .min(Comparator.comparing(WeatherSampleRow::temperature))
                .orElse(null);
        WeatherSampleRow hottest = samples.stream()
                .filter(sample -> sample.temperature() != null)
                .max(Comparator.comparing(WeatherSampleRow::temperature))
                .orElse(null);
        WeatherSampleRow windiest = samples.stream()
                .filter(sample -> sample.windSpeed() != null)
                .max(Comparator.comparing(WeatherSampleRow::windSpeed))
                .orElse(null);

        Double averageTemperature = samples.stream()
                .map(WeatherSampleRow::temperature)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);

        long rainySamplesCount = samples.stream()
                .filter(sample -> numericOrZero(sample.precipitation()) > 0)
                .count();
        long snowySamplesCount = samples.stream()
                .filter(sample -> numericOrZero(sample.snowfall()) > 0
                        || conditionFor(sample.weatherCode()).severity().equals("snow"))
                .count();

        return WeatherInsights.builder()
                .samplesCount(samples.size())
                .coldestTemperature(toInsight(coldest))
                .hottestTemperature(toInsight(hottest))
                .averageTemperature(averageTemperature)
                .wettestDay(wettestDay(samples))
                .rainySamplesCount(rainySamplesCount)
                .snowySamplesCount(snowySamplesCount)
                .windiestSample(toInsight(windiest))
                .dominantCondition(dominantCondition(samples))
                .weatherCoverageStart(samples.getFirst().observedAt())
                .weatherCoverageEnd(samples.getLast().observedAt())
                .build();
    }

    private WeatherDailyPrecipitationInsight wettestDay(List<WeatherSampleRow> samples) {
        Map<String, Double> precipitationByDate = new HashMap<>();
        for (WeatherSampleRow sample : samples) {
            if (sample.localDate() == null) {
                continue;
            }
            precipitationByDate.merge(sample.localDate().toString(), numericOrZero(sample.precipitation()), Double::sum);
        }

        return precipitationByDate.entrySet().stream()
                .max(Map.Entry.<String, Double>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> WeatherDailyPrecipitationInsight.builder()
                        .date(entry.getKey())
                        .precipitation(entry.getValue())
                        .build())
                .orElse(null);
    }

    private WeatherConditionInsight dominantCondition(List<WeatherSampleRow> samples) {
        Map<String, Long> counts = new HashMap<>();
        Map<String, Integer> representativeCodes = new HashMap<>();
        Map<String, Condition> conditions = new HashMap<>();
        for (WeatherSampleRow sample : samples) {
            if (sample.weatherCode() != null) {
                Condition condition = conditionFor(sample.weatherCode());
                String key = "%s:%s".formatted(condition.label(), condition.severity());
                counts.merge(key, 1L, Long::sum);
                representativeCodes.merge(key, sample.weatherCode(), Math::min);
                conditions.putIfAbsent(key, condition);
            }
        }
        if (counts.isEmpty()) {
            return null;
        }

        Map.Entry<String, Long> dominant = counts.entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()))
                .orElse(null);
        if (dominant == null) {
            return null;
        }

        Condition condition = conditions.get(dominant.getKey());
        return WeatherConditionInsight.builder()
                .weatherCode(representativeCodes.get(dominant.getKey()))
                .label(condition.label())
                .severity(condition.severity())
                .samplesCount(dominant.getValue())
                .build();
    }

    private WeatherSampleInsight toInsight(WeatherSampleRow sample) {
        if (sample == null) {
            return null;
        }
        Condition condition = conditionFor(sample.weatherCode());
        return WeatherSampleInsight.builder()
                .observedAt(sample.observedAt())
                .latitude(sample.latitude())
                .longitude(sample.longitude())
                .weatherCode(sample.weatherCode())
                .condition(condition.label())
                .temperature(sample.temperature())
                .precipitation(sample.precipitation())
                .snowfall(sample.snowfall())
                .windSpeed(sample.windSpeed())
                .build();
    }

    private WeatherSampleRow toSampleRow(Object[] row) {
        if (row == null || row.length < 9) {
            return null;
        }
        Instant observedAt = TimestampUtils.getInstantSafe(row[2]);
        if (observedAt == null) {
            return null;
        }
        return new WeatherSampleRow(
                toDouble(row[0]),
                toDouble(row[1]),
                observedAt,
                toInteger(row[3]),
                toDouble(row[4]),
                toDouble(row[5]),
                toDouble(row[6]),
                toDouble(row[7]),
                toLocalDate(row[8])
        );
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return value == null ? null : LocalDate.parse(String.valueOf(value));
    }

    private Double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private Integer toInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private double numericOrZero(Double value) {
        return value == null ? 0 : value;
    }

    public static Condition conditionFor(Integer code) {
        if (code == null) {
            return new Condition("Weather", "cloud");
        }
        if (code == 0) {
            return new Condition("Clear", "clear");
        }
        if (code == 1 || code == 2) {
            return new Condition("Partly cloudy", "cloud");
        }
        if (code == 3) {
            return new Condition("Cloudy", "cloud");
        }
        if (code == 45 || code == 48) {
            return new Condition("Fog", "fog");
        }
        if (List.of(51, 53, 55, 56, 57).contains(code)) {
            return new Condition("Drizzle", "rain");
        }
        if (List.of(61, 63, 65, 66, 67).contains(code)) {
            return new Condition("Rain", "rain");
        }
        if (List.of(71, 73, 75, 77).contains(code)) {
            return new Condition("Snow", "snow");
        }
        if (List.of(80, 81, 82).contains(code)) {
            return new Condition("Rain showers", "rain");
        }
        if (List.of(85, 86).contains(code)) {
            return new Condition("Snow showers", "snow");
        }
        if (List.of(95, 96, 99).contains(code)) {
            return new Condition("Storm", "storm");
        }
        return new Condition("Weather", "cloud");
    }

    public static String meteorologicalSeason(LocalDate date) {
        if (date == null) {
            return null;
        }
        Month month = date.getMonth();
        return switch (month) {
            case DECEMBER, JANUARY, FEBRUARY -> "WINTER";
            case MARCH, APRIL, MAY -> "SPRING";
            case JUNE, JULY, AUGUST -> "SUMMER";
            case SEPTEMBER, OCTOBER, NOVEMBER -> "AUTUMN";
        };
    }

    public record Condition(String label, String severity) {
    }

    private record WeatherSampleRow(
            Double latitude,
            Double longitude,
            Instant observedAt,
            Integer weatherCode,
            Double temperature,
            Double precipitation,
            Double snowfall,
            Double windSpeed,
            LocalDate localDate) {
    }
}

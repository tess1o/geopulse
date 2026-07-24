package org.github.tess1o.geopulse.weather.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.weather.dto.WeatherSampleDTO;
import org.github.tess1o.geopulse.weather.model.WeatherSampleEntity;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class WeatherSampleRepository implements PanacheRepository<WeatherSampleEntity> {

    public List<WeatherSampleEntity> findByUserAndRange(
            UUID userId,
            Instant startTime,
            Instant endTime,
            Double minLat,
            Double minLon,
            Double maxLat,
            Double maxLon) {

        StringBuilder jpql = new StringBuilder("""
                user.id = :userId
                and observedAt >= :startTime
                and observedAt <= :endTime
                """);
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("startTime", startTime);
        params.put("endTime", endTime);

        if (minLat != null && maxLat != null) {
            jpql.append(" and requestedLatitude >= :minLat and requestedLatitude <= :maxLat");
            params.put("minLat", Math.min(minLat, maxLat));
            params.put("maxLat", Math.max(minLat, maxLat));
        }
        if (minLon != null && maxLon != null) {
            jpql.append(" and requestedLongitude >= :minLon and requestedLongitude <= :maxLon");
            params.put("minLon", Math.min(minLon, maxLon));
            params.put("maxLon", Math.max(minLon, maxLon));
        }

        jpql.append(" order by observedAt asc");
        return find(jpql.toString(), params).list();
    }

    public Optional<WeatherSampleEntity> findExisting(
            UUID userId,
            String provider,
            double latitudeBucket,
            double longitudeBucket,
            Instant observedAt,
            WeatherTargetSource source) {
        return find("""
                user.id = ?1
                and provider = ?2
                and latitudeBucket = ?3
                and longitudeBucket = ?4
                and observedAt = ?5
                """,
                userId, provider, latitudeBucket, longitudeBucket, observedAt)
                .firstResultOptional();
    }

    public boolean existsAtBucketHour(UUID userId, String provider, double latitudeBucket, double longitudeBucket, Instant observedAt) {
        return count("""
                user.id = ?1
                and provider = ?2
                and latitudeBucket = ?3
                and longitudeBucket = ?4
                and observedAt = ?5
                """,
                userId, provider, latitudeBucket, longitudeBucket, observedAt) > 0;
    }

    public long countSamples() {
        return count();
    }

    public List<WeatherSampleDTO> toDtos(List<WeatherSampleEntity> samples) {
        return samples.stream()
                .map(this::toDto)
                .toList();
    }

    public WeatherSampleDTO toDto(WeatherSampleEntity sample) {
        return WeatherSampleDTO.builder()
                .id(sample.getId())
                .provider(sample.getProvider())
                .source(sample.getSource())
                .latitude(sample.getRequestedLatitude())
                .longitude(sample.getRequestedLongitude())
                .observedAt(sample.getObservedAt())
                .fetchedAt(sample.getFetchedAt())
                .weatherCode(sample.getWeatherCode())
                .temperature(sample.getTemperature())
                .apparentTemperature(sample.getApparentTemperature())
                .humidity(sample.getHumidity())
                .precipitation(sample.getPrecipitation())
                .rain(sample.getRain())
                .snowfall(sample.getSnowfall())
                .cloudCover(sample.getCloudCover())
                .windSpeed(sample.getWindSpeed())
                .windGust(sample.getWindGust())
                .windDirection(sample.getWindDirection())
                .pressure(sample.getPressure())
                .build();
    }
}

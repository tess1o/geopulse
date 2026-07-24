package org.github.tess1o.geopulse.weather.model;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(
        name = "weather_samples",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_weather_samples_user_provider_bucket_time",
                columnNames = {"user_id", "provider", "latitude_bucket", "longitude_bucket", "observed_at"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherSampleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 40)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WeatherTargetSource source;

    @Column(name = "requested_latitude", nullable = false)
    private double requestedLatitude;

    @Column(name = "requested_longitude", nullable = false)
    private double requestedLongitude;

    @Column(name = "provider_latitude")
    private Double providerLatitude;

    @Column(name = "provider_longitude")
    private Double providerLongitude;

    @Column(name = "latitude_bucket", nullable = false)
    private double latitudeBucket;

    @Column(name = "longitude_bucket", nullable = false)
    private double longitudeBucket;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "timezone", length = 100)
    private String timezone;

    @Column(name = "weather_code")
    private Integer weatherCode;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "apparent_temperature")
    private Double apparentTemperature;

    @Column(name = "humidity")
    private Double humidity;

    @Column(name = "precipitation")
    private Double precipitation;

    @Column(name = "rain")
    private Double rain;

    @Column(name = "snowfall")
    private Double snowfall;

    @Column(name = "cloud_cover")
    private Double cloudCover;

    @Column(name = "wind_speed")
    private Double windSpeed;

    @Column(name = "wind_gust")
    private Double windGust;

    @Column(name = "wind_direction")
    private Double windDirection;

    @Column(name = "pressure")
    private Double pressure;

    @Type(JsonType.class)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private Map<String, Object> rawData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (fetchedAt == null) {
            fetchedAt = now;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

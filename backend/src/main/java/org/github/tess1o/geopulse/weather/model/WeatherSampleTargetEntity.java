package org.github.tess1o.geopulse.weather.model;

import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;

@Entity
@Table(
        name = "weather_sample_targets",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_weather_targets_user_provider_bucket_time",
                columnNames = {"user_id", "provider", "latitude_bucket", "longitude_bucket", "target_at"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherSampleTargetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "latitude_bucket", nullable = false)
    private double latitudeBucket;

    @Column(name = "longitude_bucket", nullable = false)
    private double longitudeBucket;

    @Column(name = "target_at", nullable = false)
    private Instant targetAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WeatherTargetSource source;

    @Column(nullable = false)
    @Builder.Default
    private int priority = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private WeatherTargetStatus status = WeatherTargetStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (nextAttemptAt == null) {
            nextAttemptAt = now;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

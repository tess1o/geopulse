package org.github.tess1o.geopulse.mapmatching.model;

import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;

@Entity
@Table(
        name = "timeline_trip_path_matches",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_timeline_trip_path_matches_current",
                columnNames = {"user_id", "provider", "profile", "config_hash", "input_hash"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineTripPathMatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private TimelineTripEntity trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(nullable = false, length = 40)
    private String profile;

    @Column(name = "config_hash", nullable = false, length = 128)
    private String configHash;

    @Column(name = "input_hash", nullable = false, length = 128)
    private String inputHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private MapMatchingStatus status = MapMatchingStatus.PENDING;

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

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "matched_segments_json", columnDefinition = "TEXT")
    private String matchedSegmentsJson;

    @Column(nullable = false, length = 40)
    @Builder.Default
    private String source = MapMatchingSource.ON_DEMAND.name();

    @Column(nullable = false)
    @Builder.Default
    private int priority = 100;

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
        if (status == null) {
            status = MapMatchingStatus.PENDING;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

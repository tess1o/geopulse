package org.github.tess1o.geopulse.streaming.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;

/**
 * Stores user manual movement type overrides for timeline trips.
 *
 * These records survive trip deletion/regeneration by keeping stable source anchors
 * (timestamp, duration, distance and start/end coordinates) and an optional live trip link.
 */
@Entity
@Table(name = "timeline_trip_movement_overrides")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TimelineTripMovementOverrideEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    @ToString.Exclude
    private TimelineTripEntity trip;

    @Column(name = "movement_type", nullable = false, length = 50)
    private String movementType;

    @Column(name = "source_trip_timestamp", nullable = false)
    private Instant sourceTripTimestamp;

    @Column(name = "source_trip_duration_seconds", nullable = false)
    private long sourceTripDurationSeconds;

    @Column(name = "source_distance_meters", nullable = false)
    private long sourceDistanceMeters;

    @Column(name = "source_start_latitude", nullable = false)
    private double sourceStartLatitude;

    @Column(name = "source_start_longitude", nullable = false)
    private double sourceStartLongitude;

    @Column(name = "source_end_latitude", nullable = false)
    private double sourceEndLatitude;

    @Column(name = "source_end_longitude", nullable = false)
    private double sourceEndLongitude;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

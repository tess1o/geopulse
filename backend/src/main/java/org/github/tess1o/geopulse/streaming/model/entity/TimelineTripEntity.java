package org.github.tess1o.geopulse.streaming.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.time.Instant;

/**
 * Entity for persisting timeline trip data with path information.
 * Represents movement between stay points with spatial path data.
 */
@Entity
@Table(name = "timeline_trips")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TimelineTripEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private UserEntity user;

    /**
     * Start time of the trip
     */
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    /**
     * Duration of trip in seconds
     */
    @Column(name = "trip_duration", nullable = false)
    private long tripDuration;

    @Column(name = "start_point", nullable = false)
    private Point startPoint;

    @Column(name = "end_point", nullable = false)
    private Point endPoint;

    /**
     * Distance traveled in meters
     */
    @Column(name = "distance_meters", nullable = false)
    private long distanceMeters;

    /**
     * Movement type (e.g., WALKING, DRIVING, CYCLING)
     */
    @Column(name = "movement_type", length = 50)
    private String movementType;

    /**
     * Spatial path of the trip as a LineString geometry
     * Stores the actual GPS path taken during the trip
     */
    @Column(name = "path", columnDefinition = "geometry(LineString,4326)")
    private LineString path;

    /**
     * Average GPS speed from actual GPS readings (m/s)
     * Used for more accurate travel classification
     */
    @Column(name = "avg_gps_speed")
    private Double avgGpsSpeed;

    /**
     * Maximum GPS speed from actual GPS readings (m/s)
     * Used for more accurate travel classification
     */
    @Column(name = "max_gps_speed")
    private Double maxGpsSpeed;

    /**
     * Speed variance indicating consistency of movement
     * Lower values = steady movement (walking), higher = variable (driving)
     */
    @Column(name = "speed_variance")
    private Double speedVariance;

    /**
     * Count of GPS points with low accuracy (> threshold)
     * Used to assess data quality for classification reliability
     */
    @Column(name = "low_accuracy_points_count")
    private Integer lowAccuracyPointsCount;

    /**
     * When this trip was last updated/regenerated
     */
    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    /**
     * When this timeline entry was created
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.lastUpdated = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastUpdated = Instant.now();
    }
}
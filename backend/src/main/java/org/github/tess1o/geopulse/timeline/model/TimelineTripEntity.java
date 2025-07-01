package org.github.tess1o.geopulse.timeline.model;

import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.locationtech.jts.geom.LineString;

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
     * Duration of trip in minutes
     */
    @Column(name = "trip_duration", nullable = false)
    private long tripDuration;

    /**
     * Start latitude coordinate
     */
    @Column(name = "start_latitude", nullable = false)
    private double startLatitude;

    /**
     * Start longitude coordinate
     */
    @Column(name = "start_longitude", nullable = false)
    private double startLongitude;

    /**
     * End latitude coordinate
     */
    @Column(name = "end_latitude", nullable = false)
    private double endLatitude;

    /**
     * End longitude coordinate
     */
    @Column(name = "end_longitude", nullable = false)
    private double endLongitude;

    /**
     * Distance traveled in kilometers
     */
    @Column(name = "distance_km", nullable = false)
    private double distanceKm;

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
     * Version hash of relevant data when this trip was generated.
     * Used to detect when timeline data is stale due to configuration changes.
     */
    @Column(name = "timeline_version", length = 64)
    private String timelineVersion;

    /**
     * When this trip was last updated/regenerated
     */
    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    /**
     * Flag indicating if this timeline data is stale and needs regeneration
     */
    @Column(name = "is_stale", nullable = false)
    @Builder.Default
    private Boolean isStale = false;

    /**
     * When this timeline entry was created
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.lastUpdated = Instant.now();
        if (this.isStale == null) {
            this.isStale = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastUpdated = Instant.now();
    }
}
package org.github.tess1o.geopulse.streaming.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;

/**
 * Entity for persisting timeline stay locations with referential integrity.
 * Links to either favorite locations or geocoding results for data consistency.
 */
@Entity
@Table(name = "timeline_stays")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TimelineStayEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private UserEntity user;

    /**
     * Start time of the stay
     */
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    /**
     * Duration of stay in minutes
     */
    @Column(name = "stay_duration", nullable = false)
    private long stayDuration;

    /**
     * Latitude coordinate
     */
    @Column(name = "latitude", nullable = false)
    private double latitude;

    /**
     * Longitude coordinate
     */
    @Column(name = "longitude", nullable = false)
    private double longitude;

    /**
     * Cached location name for display (resolved at creation time)
     */
    @Column(name = "location_name", nullable = false, length = 500)
    private String locationName;

    /**
     * Reference to favorite location if this stay was resolved from user's favorites.
     * Exactly one of favoriteLocation or geocodingLocation will be set.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "favorite_id")
    @ToString.Exclude
    private FavoritesEntity favoriteLocation;

    /**
     * Reference to geocoding result if this stay was resolved via external geocoding.
     * Exactly one of favoriteLocation or geocodingLocation will be set.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "geocoding_id")
    @ToString.Exclude
    private ReverseGeocodingLocationEntity geocodingLocation;

    /**
     * When this timeline entry was created
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * When this stay was last updated/regenerated
     */
    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    /**
     * Tracks the source of the location name for debugging and user information
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "location_source", nullable = false)
    @Builder.Default
    private LocationSource locationSource = LocationSource.GEOCODING;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.lastUpdated = Instant.now();
        if (this.locationSource == null) {
            this.locationSource = LocationSource.GEOCODING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastUpdated = Instant.now();
    }
}
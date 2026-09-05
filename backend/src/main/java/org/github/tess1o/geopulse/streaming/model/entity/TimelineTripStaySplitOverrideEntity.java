package org.github.tess1o.geopulse.streaming.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;

@Entity
@Table(name = "timeline_trip_stay_split_overrides")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TimelineTripStaySplitOverrideEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stay_id")
    @ToString.Exclude
    private TimelineStayEntity stay;

    @Column(name = "stay_start_time", nullable = false)
    private Instant stayStartTime;

    @Column(name = "stay_end_time", nullable = false)
    private Instant stayEndTime;

    @Column(name = "anchor_timestamp", nullable = false)
    private Instant anchorTimestamp;

    @Column(name = "stay_latitude", nullable = false)
    private double stayLatitude;

    @Column(name = "stay_longitude", nullable = false)
    private double stayLongitude;

    @Column(name = "stay_location_name", nullable = false, length = 500)
    private String stayLocationName;

    @Enumerated(EnumType.STRING)
    @Column(name = "stay_location_source", nullable = false, length = 30)
    private LocationSource stayLocationSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "favorite_id")
    @ToString.Exclude
    private FavoritesEntity favoriteLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "geocoding_id")
    @ToString.Exclude
    private ReverseGeocodingLocationEntity geocodingLocation;

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
        if (this.stayLocationSource == null) {
            this.stayLocationSource = LocationSource.HISTORICAL;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

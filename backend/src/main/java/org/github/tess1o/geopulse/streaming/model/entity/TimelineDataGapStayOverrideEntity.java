package org.github.tess1o.geopulse.streaming.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.streaming.model.shared.DataGapStayOverrideLocationStrategy;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;

/**
 * Stores manual Data Gap -> Stay conversions so they can be re-applied after timeline rebuilds.
 */
@Entity
@Table(name = "timeline_data_gap_stay_overrides")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TimelineDataGapStayOverrideEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private UserEntity user;

    /**
     * Optional live link to the currently matched gap row.
     * This will be null after conversion because matched gaps are removed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_gap_id")
    @ToString.Exclude
    private TimelineDataGapEntity dataGap;

    /**
     * Optional link to the stay currently representing this override.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stay_id")
    @ToString.Exclude
    private TimelineStayEntity stay;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_strategy", nullable = false, length = 40)
    private DataGapStayOverrideLocationStrategy locationStrategy;

    @Column(name = "selected_favorite_id")
    private Long selectedFavoriteId;

    @Column(name = "selected_geocoding_id")
    private Long selectedGeocodingId;

    @Column(name = "selected_latitude")
    private Double selectedLatitude;

    @Column(name = "selected_longitude")
    private Double selectedLongitude;

    @Column(name = "selected_location_name", length = 500)
    private String selectedLocationName;

    @Column(name = "source_gap_start_time", nullable = false)
    private Instant sourceGapStartTime;

    @Column(name = "source_gap_end_time", nullable = false)
    private Instant sourceGapEndTime;

    @Column(name = "source_gap_duration_seconds", nullable = false)
    private long sourceGapDurationSeconds;

    @Column(name = "source_before_latitude", nullable = false)
    private double sourceBeforeLatitude;

    @Column(name = "source_before_longitude", nullable = false)
    private double sourceBeforeLongitude;

    @Column(name = "source_after_latitude", nullable = false)
    private double sourceAfterLatitude;

    @Column(name = "source_after_longitude", nullable = false)
    private double sourceAfterLongitude;

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

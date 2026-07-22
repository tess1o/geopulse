package org.github.tess1o.geopulse.notes.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.locationtech.jts.geom.Point;

import java.time.Instant;

@Entity
@Table(name = "timeline_notes")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TimelineNoteEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private UserEntity user;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "content_markdown", nullable = false, columnDefinition = "TEXT")
    private String contentMarkdown;

    @Column(name = "snippet", length = 500)
    private String snippet;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @Column(name = "location", columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_source", nullable = false, length = 40)
    @Builder.Default
    private NoteLocationSource locationSource = NoteLocationSource.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "anchor_type", nullable = false, length = 24)
    @Builder.Default
    private NoteAnchorType anchorType = NoteAnchorType.TIMESTAMP;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stay_id")
    @ToString.Exclude
    private TimelineStayEntity stay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    @ToString.Exclude
    private TimelineTripEntity trip;

    @Column(name = "source_item_start_time")
    private Instant sourceItemStartTime;

    @Column(name = "source_item_duration_seconds")
    private Long sourceItemDurationSeconds;

    @Column(name = "source_start_latitude")
    private Double sourceStartLatitude;

    @Column(name = "source_start_longitude")
    private Double sourceStartLongitude;

    @Column(name = "source_end_latitude")
    private Double sourceEndLatitude;

    @Column(name = "source_end_longitude")
    private Double sourceEndLongitude;

    @Column(name = "source_distance_meters")
    private Long sourceDistanceMeters;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (locationSource == null) {
            locationSource = NoteLocationSource.NONE;
        }
        if (anchorType == null) {
            anchorType = NoteAnchorType.TIMESTAMP;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

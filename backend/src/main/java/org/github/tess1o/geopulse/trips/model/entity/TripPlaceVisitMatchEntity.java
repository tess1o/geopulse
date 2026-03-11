package org.github.tess1o.geopulse.trips.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;

import java.time.Instant;

@Entity
@Table(name = "trip_place_visit_match")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TripPlaceVisitMatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id")
    @ToString.Exclude
    private TripEntity trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_item_id")
    @ToString.Exclude
    private TripPlanItemEntity planItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stay_id")
    @ToString.Exclude
    private TimelineStayEntity stay;

    @Column(name = "distance_meters")
    private Double distanceMeters;

    @Column(name = "dwell_seconds")
    private Long dwellSeconds;

    @Column(name = "confidence", nullable = false)
    private Double confidence;

    @Column(name = "decision", nullable = false, length = 32)
    private String decision;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}


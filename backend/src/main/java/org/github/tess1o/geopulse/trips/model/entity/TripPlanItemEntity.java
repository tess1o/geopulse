package org.github.tess1o.geopulse.trips.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "trip_plan_items")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TripPlanItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id")
    @ToString.Exclude
    private TripEntity trip;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "planned_day")
    private LocalDate plannedDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private TripPlanItemPriority priority = TripPlanItemPriority.OPTIONAL;

    @Column(name = "order_index", nullable = false)
    @Builder.Default
    private Integer orderIndex = 0;

    @Column(name = "is_visited", nullable = false)
    @Builder.Default
    private Boolean isVisited = false;

    @Column(name = "visit_confidence")
    private Double visitConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_source")
    private TripPlanItemVisitSource visitSource;

    @Column(name = "visited_at")
    private Instant visitedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "manual_override_state")
    private TripPlanItemOverrideState manualOverrideState;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replacement_item_id")
    @ToString.Exclude
    private TripPlanItemEntity replacementItem;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if (this.priority == null) {
            this.priority = TripPlanItemPriority.OPTIONAL;
        }
        if (this.orderIndex == null) {
            this.orderIndex = 0;
        }
        if (this.isVisited == null) {
            this.isVisited = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

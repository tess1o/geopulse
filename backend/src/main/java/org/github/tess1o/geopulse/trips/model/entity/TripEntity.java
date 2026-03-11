package org.github.tess1o.geopulse.trips.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.periods.model.entity.PeriodTagEntity;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;

@Entity
@Table(name = "trips")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TripEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_tag_id")
    @ToString.Exclude
    private PeriodTagEntity periodTag;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TripStatus status = TripStatus.UPCOMING;

    @Column(name = "color", length = 7)
    private String color;

    @Column(name = "notes", length = 4000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if (this.status == null) {
            this.status = TripStatus.UPCOMING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}


package org.github.tess1o.geopulse.timeline.model;

import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;

/**
 * Entity for persisting timeline data gaps - periods where GPS data is not available.
 * These gaps represent unknown user activity periods in the timeline.
 */
@Entity
@Table(name = "timeline_data_gaps")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TimelineDataGapEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private UserEntity user;

    /**
     * Start time of the data gap
     */
    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    /**
     * End time of the data gap
     */
    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    /**
     * Duration of the gap in seconds
     */
    @Column(name = "duration_seconds", nullable = false)
    private long durationSeconds;

    /**
     * When this gap record was created
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.startTime != null && this.endTime != null) {
            this.durationSeconds = this.endTime.getEpochSecond() - this.startTime.getEpochSecond();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        if (this.startTime != null && this.endTime != null) {
            this.durationSeconds = this.endTime.getEpochSecond() - this.startTime.getEpochSecond();
        }
    }
}
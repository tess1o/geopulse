package org.github.tess1o.geopulse.streaming.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * A projection row: the materialized event a correction produced or changed during the last generation pass.
 * Rebuilt on every pass; deleting it only affects history display.
 */
@Entity
@Table(name = "timeline_correction_effects")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimelineCorrectionEffectEntity {
    @Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "correction_id")
    @ToString.Exclude
    private TimelineEditEntity correction;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;
    @Column(name = "target_id", nullable = false)
    private Long targetId;
    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        appliedAt = Instant.now();
    }
}

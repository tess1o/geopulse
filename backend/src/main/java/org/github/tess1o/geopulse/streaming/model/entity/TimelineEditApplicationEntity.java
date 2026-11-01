package org.github.tess1o.geopulse.streaming.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
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

@Entity
@Table(name = "timeline_edit_applications")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimelineEditApplicationEntity {
    @Id
    @Column(name = "edit_id")
    private Long editId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "edit_id")
    @ToString.Exclude
    private TimelineEditEntity edit;

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

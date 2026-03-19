package org.github.tess1o.geopulse.geofencing.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;

import java.time.Instant;

@Entity
@Table(name = "geofence_rule_state")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeofenceRuleStateEntity {

    @Id
    @Column(name = "rule_id")
    private Long ruleId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "rule_id", nullable = false)
    @ToString.Exclude
    private GeofenceRuleEntity rule;

    @Column(name = "current_inside", nullable = false)
    private Boolean currentInside;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_point_id")
    @ToString.Exclude
    private GpsPointEntity lastPoint;

    @Column(name = "last_transition_at")
    private Instant lastTransitionAt;

    @Column(name = "last_notified_at")
    private Instant lastNotifiedAt;

    @Column(name = "last_notified_inside")
    private Boolean lastNotifiedInside;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

package org.github.tess1o.geopulse.geofencing.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;

@Entity
@Table(name = "geofence_rule_subjects")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeofenceRuleSubjectEntity {

    @EmbeddedId
    private GeofenceRuleSubjectId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("ruleId")
    @JoinColumn(name = "rule_id", nullable = false)
    @ToString.Exclude
    private GeofenceRuleEntity rule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("subjectUserId")
    @JoinColumn(name = "subject_user_id", nullable = false)
    @ToString.Exclude
    private UserEntity subjectUser;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

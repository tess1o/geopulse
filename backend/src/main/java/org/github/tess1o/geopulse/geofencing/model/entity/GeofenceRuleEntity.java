package org.github.tess1o.geopulse.geofencing.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;

@Entity
@Table(name = "geofence_rules")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeofenceRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    @ToString.Exclude
    private UserEntity ownerUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_user_id", nullable = false)
    @ToString.Exclude
    private UserEntity subjectUser;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "north_east_lat", nullable = false)
    private Double northEastLat;

    @Column(name = "north_east_lon", nullable = false)
    private Double northEastLon;

    @Column(name = "south_west_lat", nullable = false)
    private Double southWestLat;

    @Column(name = "south_west_lon", nullable = false)
    private Double southWestLon;

    @Column(name = "monitor_enter", nullable = false)
    @Builder.Default
    private Boolean monitorEnter = true;

    @Column(name = "monitor_leave", nullable = false)
    @Builder.Default
    private Boolean monitorLeave = true;

    @Column(name = "cooldown_seconds", nullable = false)
    @Builder.Default
    private Integer cooldownSeconds = 120;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enter_template_id")
    @ToString.Exclude
    private NotificationTemplateEntity enterTemplate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_template_id")
    @ToString.Exclude
    private NotificationTemplateEntity leaveTemplate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private GeofenceRuleStatus status = GeofenceRuleStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (monitorEnter == null) {
            monitorEnter = true;
        }
        if (monitorLeave == null) {
            monitorLeave = true;
        }
        if (cooldownSeconds == null) {
            cooldownSeconds = 120;
        }
        if (status == null) {
            status = GeofenceRuleStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

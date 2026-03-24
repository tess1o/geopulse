package org.github.tess1o.geopulse.geofencing.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;

@Entity
@Table(name = "geofence_events")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeofenceEventEntity {

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    @ToString.Exclude
    private GeofenceRuleEntity rule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    @ToString.Exclude
    private NotificationTemplateEntity template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_id")
    @ToString.Exclude
    private GpsPointEntity point;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 16)
    private GeofenceEventType eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "title", columnDefinition = "text")
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "subject_display_name", nullable = false, length = 255)
    private String subjectDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    @Builder.Default
    private GeofenceDeliveryStatus deliveryStatus = GeofenceDeliveryStatus.PENDING;

    @Column(name = "delivery_attempts", nullable = false)
    @Builder.Default
    private Integer deliveryAttempts = 0;

    @Column(name = "last_delivery_error", columnDefinition = "text")
    private String lastDeliveryError;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "seen_at")
    private Instant seenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (deliveryStatus == null) {
            deliveryStatus = GeofenceDeliveryStatus.PENDING;
        }
        if (deliveryAttempts == null) {
            deliveryAttempts = 0;
        }
    }
}

package org.github.tess1o.geopulse.notifications.model.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceDeliveryStatus;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.hibernate.annotations.Type;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "user_notifications")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotificationEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    @ToString.Exclude
    private UserEntity ownerUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 40)
    private NotificationSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 80)
    private NotificationType type;

    @Column(name = "title", columnDefinition = "text")
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "seen_at")
    private Instant seenAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", length = 20)
    private GeofenceDeliveryStatus deliveryStatus;

    @Column(name = "object_ref", length = 255)
    private String objectRef;

    @Type(JsonType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "dedupe_key", length = 255)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

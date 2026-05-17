package org.github.tess1o.geopulse.geofencing.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;

@Entity
@Table(name = "notification_templates")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private UserEntity user;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "destination", nullable = false, columnDefinition = "text")
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(name = "external_routing_mode", nullable = false, length = 32)
    @Builder.Default
    private AppriseExternalRoutingMode externalRoutingMode = AppriseExternalRoutingMode.URLS;

    @Column(name = "apprise_config_key", length = 255)
    private String appriseConfigKey;

    @Column(name = "apprise_tag", length = 255)
    private String appriseTag;

    @Column(name = "title_template", columnDefinition = "text")
    private String titleTemplate;

    @Column(name = "body_template", columnDefinition = "text")
    private String bodyTemplate;

    @Column(name = "default_for_enter", nullable = false)
    @Builder.Default
    private Boolean defaultForEnter = false;

    @Column(name = "default_for_leave", nullable = false)
    @Builder.Default
    private Boolean defaultForLeave = false;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "send_in_app", nullable = false)
    @Builder.Default
    private Boolean sendInApp = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (defaultForEnter == null) {
            defaultForEnter = false;
        }
        if (defaultForLeave == null) {
            defaultForLeave = false;
        }
        if (enabled == null) {
            enabled = true;
        }
        if (sendInApp == null) {
            sendInApp = true;
        }
        if (externalRoutingMode == null) {
            externalRoutingMode = AppriseExternalRoutingMode.URLS;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

package org.github.tess1o.geopulse.streaming.model.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "timeline_regeneration_campaigns")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimelineRegenerationCampaignEntity extends PanacheEntityBase implements Serializable {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "campaign_key", nullable = false, length = 120, unique = true)
    private String campaignKey;

    @Column(name = "affected_from", nullable = false)
    private Instant affectedFrom;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    @Builder.Default
    private TimelineRegenerationCampaignSource source = TimelineRegenerationCampaignSource.ADMIN;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TimelineRegenerationCampaignStatus status = TimelineRegenerationCampaignStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @ToString.Exclude
    private UserEntity createdBy;

    @Column(name = "total_users", nullable = false)
    @Builder.Default
    private int totalUsers = 0;

    @Column(name = "completed_users", nullable = false)
    @Builder.Default
    private int completedUsers = 0;

    @Column(name = "failed_users", nullable = false)
    @Builder.Default
    private int failedUsers = 0;

    @Column(name = "skipped_users", nullable = false)
    @Builder.Default
    private int skippedUsers = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

package org.github.tess1o.geopulse.insight.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@Entity
@Table(name = "user_badges")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserBadgeEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private UserEntity user;

    @Column(name = "badge_id", nullable = false)
    private String badgeId;

    @Column(nullable = false)
    private String title;

    private String description;

    private String icon;

    @Column(nullable = false)
    @Builder.Default
    private boolean earned = false;

    @Column(name = "earned_date")
    private Instant earnedDate;

    @Builder.Default
    private Integer progress = 0;

    @Column(name = "current_value")
    @Builder.Default
    private Integer currentValue = 0;

    @Column(name = "target_value")
    private Integer targetValue;

    @Column(name = "last_calculated", nullable = false)
    @Builder.Default
    private Instant lastCalculated = Instant.now();

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if (this.lastCalculated == null) {
            this.lastCalculated = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
        this.lastCalculated = Instant.now();
    }

    /**
     * Convert this entity to a Badge model object
     */
    public Badge toBadge() {
        return Badge.builder()
                .id(this.badgeId)
                .title(this.title)
                .description(this.description)
                .icon(this.icon)
                .earned(this.earned)
                .earnedDate(this.earnedDate != null ? this.earnedDate.toString() : null)
                .progress(this.progress)
                .current(this.currentValue)
                .target(this.targetValue)
                .build();
    }

    /**
     * Update this entity from a Badge model object
     */
    public void updateFromBadge(Badge badge) {
        this.title = badge.getTitle();
        this.description = badge.getDescription();
        this.icon = badge.getIcon();
        this.earned = badge.isEarned();
        this.earnedDate = parseBadgeDate(badge.getEarnedDate());
        this.progress = badge.getProgress();
        this.currentValue = badge.getCurrent();
        this.targetValue = badge.getTarget();
    }

    /**
     * Parse badge date string which could be ISO-8601 or human-readable format
     */
    public static Instant parseBadgeDate(String dateString) {
        if (dateString == null) {
            return null;
        }
        
        try {
            // Try ISO-8601 format first (e.g., "2025-09-18T00:00:00Z")
            return Instant.parse(dateString);
        } catch (DateTimeParseException e1) {
            try {
                // Try human-readable format (e.g., "September 18, 2025")
                LocalDate localDate = LocalDate.parse(dateString, DateTimeFormatter.ofPattern("MMMM d, yyyy"));
                return localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException e2) {
                try {
                    // Try ISO date format (e.g., "2025-09-18")
                    LocalDate localDate = LocalDate.parse(dateString, DateTimeFormatter.ISO_DATE);
                    return localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
                } catch (DateTimeParseException e3) {
                    // Log the error and return null
                    System.err.println("Failed to parse badge date: " + dateString);
                    return null;
                }
            }
        }
    }

    /**
     * Create a new UserBadgeEntity from a Badge model object
     */
    public static UserBadgeEntity fromBadge(Badge badge, UUID userId) {
        return UserBadgeEntity.builder()
                .user(UserEntity.<UserEntity>findById(userId))
                .badgeId(badge.getId())
                .title(badge.getTitle())
                .description(badge.getDescription())
                .icon(badge.getIcon())
                .earned(badge.isEarned())
                .earnedDate(parseBadgeDate(badge.getEarnedDate()))
                .progress(badge.getProgress())
                .currentValue(badge.getCurrent())
                .targetValue(badge.getTarget())
                .lastCalculated(Instant.now())
                .build();
    }
}
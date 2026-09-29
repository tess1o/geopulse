package org.github.tess1o.geopulse.user.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.friends.invitation.model.FriendInvitationEntity;
import org.github.tess1o.geopulse.friends.model.UserFriendEntity;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.immich.model.ImmichPreferences;
import org.github.tess1o.geopulse.notes.model.MemosPreferences;
import org.github.tess1o.geopulse.notifications.model.NotificationPreferences;
import org.github.tess1o.geopulse.shared.persistence.JacksonJsonMutabilityPlan;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Mutability;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserEntity extends PanacheEntityBase implements Serializable {

    @Id
    @GeneratedValue
    private UUID id;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    @Size(max = 254, message = "Email cannot exceed 254 characters")
    @Column(unique = true, nullable = false)
    private String email;

    private boolean emailVerified;

    @Column(name = "password_hash", nullable = true)
    private String passwordHash;

    @Size(min = 1, max = 100, message = "Full name must be between 1 and 100 characters")
    @Column(name = "full_name")
    private String fullName;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "is_active")
    private boolean isActive;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    @Builder.Default
    private Role role = Role.USER;

    @Size(max = 500, message = "Avatar URL cannot exceed 500 characters")
    private String avatar;

    @NotBlank(message = "Timezone is required")
    @Size(max = 255, message = "Timezone cannot exceed 255 characters")
    @Column(name = "timezone", nullable = false)
    @Builder.Default
    private String timezone = "UTC";

    @JdbcTypeCode(SqlTypes.JSON)
    @Mutability(JacksonJsonMutabilityPlan.class)
    @Column(columnDefinition = "jsonb", name = "timeline_preferences")
    public TimelinePreferences timelinePreferences;

    @JdbcTypeCode(SqlTypes.JSON)
    @Mutability(JacksonJsonMutabilityPlan.class)
    @Column(columnDefinition = "jsonb", name = "immich_preferences")
    public ImmichPreferences immichPreferences;

    @JdbcTypeCode(SqlTypes.JSON)
    @Mutability(JacksonJsonMutabilityPlan.class)
    @Column(columnDefinition = "jsonb", name = "memos_preferences")
    public MemosPreferences memosPreferences;

    @JdbcTypeCode(SqlTypes.JSON)
    @Mutability(JacksonJsonMutabilityPlan.class)
    @Column(columnDefinition = "jsonb", name = "notification_preferences")
    public NotificationPreferences notificationPreferences;

    @Enumerated(EnumType.STRING)
    @Column(name = "timeline_status", nullable = false)
    @Builder.Default
    private TimelineStatus timelineStatus = TimelineStatus.IDLE;

    @Column(name = "ai_settings_encrypted", columnDefinition = "TEXT")
    private String aiSettingsEncrypted;

    @Column(name = "ai_settings_key_id")
    private String aiSettingsKeyId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Mutability(JacksonJsonMutabilityPlan.class)
    @Column(columnDefinition = "jsonb", name = "ui_preferences", nullable = false)
    @Builder.Default
    private UserUiPreferences uiPreferences = new UserUiPreferences();

    /** Affect ONLY UI rendering, not timeline generation. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Mutability(JacksonJsonMutabilityPlan.class)
    @Column(columnDefinition = "jsonb", name = "timeline_display_preferences", nullable = false)
    @Builder.Default
    private TimelineDisplayPreferences timelineDisplayPreferences = new TimelineDisplayPreferences();

    @Column(name = "coverage_enabled", nullable = false)
    @Builder.Default
    private boolean coverageEnabled = false;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @ToString.Exclude
    private List<GpsPointEntity> gpsPoints;

    @OneToMany(mappedBy = "sender", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<FriendInvitationEntity> sentInvitations = new ArrayList<>();

    @OneToMany(mappedBy = "receiver", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<FriendInvitationEntity> receivedInvitations = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<UserFriendEntity> friends = new ArrayList<>();

    /**
     * Stored UI preferences, never null (initialized on first access for entities not built via the builder).
     * Unset values are null; see {@link UserUiPreferences#withDefaults()}.
     */
    public UserUiPreferences getUiPreferences() {
        if (uiPreferences == null) {
            uiPreferences = new UserUiPreferences();
        }
        return uiPreferences;
    }

    /**
     * Stored display preferences, never null (initialized on first access for entities not built via the builder).
     * Unset values are null; see {@link TimelineDisplayPreferences#withDefaults()}.
     */
    public TimelineDisplayPreferences getTimelineDisplayPreferences() {
        if (timelineDisplayPreferences == null) {
            timelineDisplayPreferences = new TimelineDisplayPreferences();
        }
        return timelineDisplayPreferences;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

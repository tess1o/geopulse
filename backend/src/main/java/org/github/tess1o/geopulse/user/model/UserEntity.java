package org.github.tess1o.geopulse.user.model;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.github.tess1o.geopulse.friends.invitation.model.FriendInvitationEntity;
import org.github.tess1o.geopulse.friends.model.UserFriendEntity;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.immich.model.ImmichPreferences;
import org.hibernate.annotations.Type;

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
public class UserEntity extends PanacheEntityBase {

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

    private String role;

    @Size(max = 500, message = "Avatar URL cannot exceed 500 characters")
    private String avatar;

    @NotBlank(message = "Timezone is required")
    @Size(max = 255, message = "Timezone cannot exceed 255 characters")
    @Column(name = "timezone", nullable = false)
    @Builder.Default
    private String timezone = "UTC";

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb", name = "timeline_preferences")
    public TimelinePreferences timelinePreferences;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb", name = "immich_preferences")
    public ImmichPreferences immichPreferences;

    @Enumerated(EnumType.STRING)
    @Column(name = "timeline_status", nullable = false)
    @Builder.Default
    private TimelineStatus timelineStatus = TimelineStatus.IDLE;

    @Column(name = "ai_settings_encrypted", columnDefinition = "TEXT")
    private String aiSettingsEncrypted;

    @Column(name = "ai_settings_key_id")
    private String aiSettingsKeyId;

    @Size(max = 1000, message = "Custom map tile URL cannot exceed 1000 characters")
    @Column(name = "custom_map_tile_url", length = 1000)
    private String customMapTileUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "measure_unit", length = 1000)
    private MeasureUnit measureUnit;

    @Size(max = 1000, message = "Default redirect URL after login or when we navigate to / page")
    @Column(name = "default_redirect_url", length = 1000)
    private String defaultRedirectUrl;

    @Column(name = "share_location_with_friends", nullable = false)
    @Builder.Default
    private boolean shareLocationWithFriends = true;

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

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

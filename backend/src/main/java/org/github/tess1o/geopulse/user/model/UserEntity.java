package org.github.tess1o.geopulse.user.model;

import com.vladmihalcea.hibernate.type.json.JsonType;
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

    @NotBlank(message = "Password hash is required")
    @Column(name = "password_hash", nullable = false)
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

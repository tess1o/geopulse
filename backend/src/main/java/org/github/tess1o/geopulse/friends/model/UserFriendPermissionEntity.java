package org.github.tess1o.geopulse.friends.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;

/**
 * Entity representing granular permissions for friend timeline sharing.
 * Allows users to control which friends can view their historical timeline data.
 */
@Entity
@Table(name = "user_friend_permissions")
@Getter
@Setter
@RequiredArgsConstructor
public class UserFriendPermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;      // The user granting the permission

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "friend_id", nullable = false)
    private UserEntity friend;    // The friend receiving the permission

    @Column(name = "share_timeline", nullable = false)
    private Boolean shareTimeline = false;  // Permission to view full timeline

    @Column(name = "share_live_location", nullable = false)
    private Boolean shareLiveLocation = false;  // Permission to view live/current location

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (shareTimeline == null) {
            shareTimeline = false;
        }
        if (shareLiveLocation == null) {
            shareLiveLocation = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        UserFriendPermissionEntity that = (UserFriendPermissionEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}

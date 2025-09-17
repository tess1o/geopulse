package org.github.tess1o.geopulse.auth.oidc.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_oidc_connections")
@IdClass(UserOidcConnectionId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserOidcConnectionEntity extends PanacheEntityBase {
    
    @Id
    @Column(name = "user_id")
    private UUID userId;
    
    @Id
    @Column(name = "provider_name")
    private String providerName;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private UserEntity user;
    
    @Column(name = "external_user_id", nullable = false)
    private String externalUserId;
    
    @Column(name = "display_name")
    private String displayName;
    
    @Column(name = "avatar_url")
    private String avatarUrl;
    
    @Column(name = "linked_at")
    private Instant linkedAt;
    
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
    
    @PrePersist
    protected void onCreate() {
        this.linkedAt = Instant.now();
    }
}
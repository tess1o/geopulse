package org.github.tess1o.geopulse.auth.oidc.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oidc_session_states")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidcSessionStateEntity extends PanacheEntityBase {
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "state_token", unique = true, nullable = false)
    private String stateToken;
    
    private String nonce;
    
    @Column(name = "provider_name", nullable = false)
    private String providerName;
    
    @Column(name = "redirect_uri")
    private String redirectUri;
    
    @Column(name = "linking_user_id")
    private UUID linkingUserId;
    
    @Column(name = "linking_token")
    private String linkingToken;
    
    @Column(name = "created_at")
    private Instant createdAt;
    
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
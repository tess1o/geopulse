package org.github.tess1o.geopulse.admin.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oidc_providers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidcProviderEntity extends PanacheEntityBase {

    @Id
    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "client_id", nullable = false, length = 255)
    private String clientId;

    @Column(name = "client_secret_encrypted", nullable = false, columnDefinition = "TEXT")
    private String clientSecretEncrypted;

    @Column(name = "client_secret_key_id", length = 50)
    private String clientSecretKeyId;

    @Column(name = "discovery_url", nullable = false, length = 500)
    private String discoveryUrl;

    @Column(name = "icon", length = 500)
    private String icon;

    @Column(name = "scopes", length = 255)
    @Builder.Default
    private String scopes = "openid profile email";

    // Cached metadata from discovery document
    @Column(name = "authorization_endpoint", length = 500)
    private String authorizationEndpoint;

    @Column(name = "token_endpoint", length = 500)
    private String tokenEndpoint;

    @Column(name = "userinfo_endpoint", length = 500)
    private String userinfoEndpoint;

    @Column(name = "jwks_uri", length = 500)
    private String jwksUri;

    @Column(name = "issuer", length = 500)
    private String issuer;

    @Column(name = "metadata_cached_at")
    private Instant metadataCachedAt;

    @Column(name = "metadata_valid")
    @Builder.Default
    private Boolean metadataValid = false;

    // Audit fields
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;
}

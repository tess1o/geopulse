package org.github.tess1o.geopulse.auth.oidc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidcProviderConfiguration {
    private String name;               // 'google', 'microsoft', etc.
    private String displayName;        // 'Google', 'Microsoft', etc.
    private String clientId;
    private String clientSecret;       // Not hashed - kept in memory only
    private String discoveryUrl;       // For generic OIDC providers
    
    // Cached metadata (populated from discovery)
    private String authorizationEndpoint;
    private String tokenEndpoint;
    private String userinfoEndpoint;
    private String jwksUri;
    private String issuer;
    
    @Builder.Default
    private String scopes = "openid profile email";
    private String icon;                   // Optional icon class for UI
    private boolean enabled;
    
    // Cache metadata
    private Instant metadataCachedAt;
    private boolean metadataValid;
}
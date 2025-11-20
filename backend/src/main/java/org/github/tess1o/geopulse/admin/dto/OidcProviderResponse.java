package org.github.tess1o.geopulse.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidcProviderResponse {

    private String name;
    private String displayName;
    private boolean enabled;
    private String clientId;
    private boolean hasClientSecret; // Never expose actual secret
    private String discoveryUrl;
    private String icon;
    private String scopes;

    // Source indicator
    private ProviderSource source; // ENVIRONMENT or DATABASE

    // Additional environment info
    private boolean hasEnvironmentConfig; // True if provider also exists in environment variables

    // Metadata status
    private boolean metadataValid;
    private Instant metadataCachedAt;

    // Audit information (only for DB providers)
    private Instant createdAt;
    private Instant updatedAt;

    public enum ProviderSource {
        ENVIRONMENT,
        DATABASE
    }
}

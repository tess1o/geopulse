package org.github.tess1o.geopulse.auth.oidc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserOidcConnectionResponse {
    private String providerName;
    private String providerDisplayName;
    private String providerIcon;
    private String displayName;
    private String email;
    private String avatarUrl;
    private Instant linkedAt;

    // Constructor for JPA query
    public UserOidcConnectionResponse(String providerName, String displayName, String email, String avatarUrl, Instant linkedAt) {
        this.providerName = providerName;
        this.displayName = displayName;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.linkedAt = linkedAt;
    }
}
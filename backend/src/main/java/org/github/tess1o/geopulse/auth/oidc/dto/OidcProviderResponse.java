package org.github.tess1o.geopulse.auth.oidc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidcProviderResponse {
    private String name;        // 'google', 'microsoft', etc.
    private String displayName; // 'Google', 'Microsoft', etc.
    private String icon;        // Optional icon class for UI
}
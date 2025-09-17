package org.github.tess1o.geopulse.auth.oidc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidcLoginInitResponse {
    private String authorizationUrl; // URL to redirect user to for authentication
    private String state;           // State token for CSRF protection  
    private String redirectUri;     // Where to redirect after authentication
}
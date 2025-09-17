package org.github.tess1o.geopulse.auth.oidc.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidcCallbackRequest {
    @NotBlank(message = "Authorization code is required")
    private String code;  // Authorization code from OIDC provider
    
    @NotBlank(message = "State token is required")
    private String state; // State token for CSRF protection
}
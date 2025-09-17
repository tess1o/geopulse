package org.github.tess1o.geopulse.auth.oidc.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for initiating OIDC-to-OIDC account linking verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateOidcLinkingRequest {
    
    @NotBlank(message = "Verification provider is required")
    private String verificationProvider;
    
    @NotBlank(message = "New provider is required") 
    private String newProvider;
    
    @NotBlank(message = "Linking token is required")
    private String linkingToken;
}
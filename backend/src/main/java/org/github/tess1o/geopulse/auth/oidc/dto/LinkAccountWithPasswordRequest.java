package org.github.tess1o.geopulse.auth.oidc.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for linking OIDC account using password verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkAccountWithPasswordRequest {
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
    
    @NotBlank(message = "Password is required")
    private String password;
    
    @NotBlank(message = "Provider name is required")
    private String provider;
    
    @NotBlank(message = "Linking token is required")
    private String linkingToken;
}
package org.github.tess1o.geopulse.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOidcProviderRequest {

    @NotBlank(message = "Display name is required")
    @Size(min = 2, max = 100, message = "Display name must be between 2 and 100 characters")
    @Schema(examples = "Google")
    private String displayName;

    @Schema(examples = "true")
    private boolean enabled;

    @NotBlank(message = "Client ID is required")
    @Size(max = 255, message = "Client ID must not exceed 255 characters")
    @Schema(examples = "geopulse-client-id")
    private String clientId;

    // Optional: only update if provided (not empty)
    @Schema(examples = "geopulse-client-secret")
    private String clientSecret;

    @NotBlank(message = "Discovery URL is required")
    @Size(max = 500, message = "Discovery URL must not exceed 500 characters")
    @Schema(examples = "https://accounts.google.com/.well-known/openid-configuration")
    private String discoveryUrl;

    @Size(max = 100, message = "Icon must not exceed 100 characters")
    @Schema(examples = "pi pi-google")
    private String icon;

    @Size(max = 255, message = "Scopes must not exceed 255 characters")
    @Schema(examples = "openid profile email")
    private String scopes;
}

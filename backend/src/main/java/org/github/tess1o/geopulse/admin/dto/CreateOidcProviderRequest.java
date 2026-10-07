package org.github.tess1o.geopulse.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class CreateOidcProviderRequest {

    @NotBlank(message = "Provider name is required")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Provider name must be lowercase alphanumeric with hyphens")
    @Size(min = 2, max = 50, message = "Provider name must be between 2 and 50 characters")
    @Schema(examples = "google")
    private String name;

    @NotBlank(message = "Display name is required")
    @Size(min = 2, max = 100, message = "Display name must be between 2 and 100 characters")
    @Schema(examples = "Google")
    private String displayName;

    @Builder.Default
    @Schema(examples = "true")
    private boolean enabled = true;

    @NotBlank(message = "Client ID is required")
    @Size(max = 255, message = "Client ID must not exceed 255 characters")
    @Schema(examples = "geopulse-client-id")
    private String clientId;

    @NotBlank(message = "Client secret is required")
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
    @Builder.Default
    @Schema(examples = "openid profile email")
    private String scopes = "openid profile email";
}

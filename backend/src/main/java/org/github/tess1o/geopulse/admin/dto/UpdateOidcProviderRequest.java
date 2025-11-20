package org.github.tess1o.geopulse.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOidcProviderRequest {

    @NotBlank(message = "Display name is required")
    @Size(min = 2, max = 100, message = "Display name must be between 2 and 100 characters")
    private String displayName;

    private boolean enabled;

    @NotBlank(message = "Client ID is required")
    @Size(max = 255, message = "Client ID must not exceed 255 characters")
    private String clientId;

    // Optional: only update if provided (not empty)
    private String clientSecret;

    @NotBlank(message = "Discovery URL is required")
    @Size(max = 500, message = "Discovery URL must not exceed 500 characters")
    private String discoveryUrl;

    @Size(max = 100, message = "Icon must not exceed 100 characters")
    private String icon;

    @Size(max = 255, message = "Scopes must not exceed 255 characters")
    private String scopes;
}

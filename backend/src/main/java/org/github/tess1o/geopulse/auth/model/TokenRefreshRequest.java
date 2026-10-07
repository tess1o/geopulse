package org.github.tess1o.geopulse.auth.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request DTO for refreshing an access token using a refresh token.
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class TokenRefreshRequest {
    @NotBlank(message = "Refresh token is required")
    @Schema(examples = "eyJhbGciOiJSUzI1NiJ9.refresh-token")
    private String refreshToken;
}
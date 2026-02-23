package org.github.tess1o.geopulse.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.user.model.UserResponse;

/**
 * Browser authentication response for cookie-based login flows.
 * Excludes tokens from the response body and returns only user data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrowserAuthResponse {
    private UserResponse user;
    private String redirectUri;
}

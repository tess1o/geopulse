package org.github.tess1o.geopulse.auth.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Result of signing out. When the session came from an OIDC provider that supports RP-initiated
 * logout, {@code idpLogoutUrl} is where the browser should go next to end the provider session too.
 */
public record LogoutResponse(
        @Schema(description = "URL that also signs the user out of the OIDC provider, or null when there is "
                + "nothing to do at the provider (password session, provider without an end-session endpoint, "
                + "or the feature is disabled).", nullable = true)
        String idpLogoutUrl) {
}

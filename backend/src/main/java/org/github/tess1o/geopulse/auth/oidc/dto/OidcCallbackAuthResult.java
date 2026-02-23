package org.github.tess1o.geopulse.auth.oidc.dto;

import org.github.tess1o.geopulse.auth.model.AuthResponse;

/**
 * Internal result for OIDC callback handling.
 * Carries token-bearing auth response (for cookies) plus redirect target.
 */
public record OidcCallbackAuthResult(AuthResponse authResponse, String redirectUri) {
}

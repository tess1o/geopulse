package org.github.tess1o.geopulse.auth.oidc.dto;

import org.github.tess1o.geopulse.user.model.UserEntity;

/**
 * Internal result for OIDC callback handling: the signed-in user, where to send the browser,
 * and the provider session the new GeoPulse session belongs to.
 */
public record OidcCallbackAuthResult(UserEntity user,
                                     String redirectUri,
                                     String providerName,
                                     String idpSubject,
                                     String idpSessionId,
                                     String idToken) {
}

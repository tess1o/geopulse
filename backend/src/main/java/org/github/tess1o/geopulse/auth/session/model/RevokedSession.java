package org.github.tess1o.geopulse.auth.session.model;

import java.util.UUID;

/**
 * A session as it was just before it was revoked. Revocation clears the stored ID token,
 * so this is the only place the logout flow can still read it.
 */
public record RevokedSession(UUID sessionId, SessionClientType clientType, SessionAuthMethod authMethod,
                             String oidcProvider, String idpIdToken) {
}

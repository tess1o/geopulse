package org.github.tess1o.geopulse.auth.session.model;

import java.util.UUID;

/** A validated session and the user it belongs to, as read on the current request. */
public record AuthenticatedSession(UUID sessionId, UUID userId, String email, String role,
                                   SessionClientType clientType) {
}

package org.github.tess1o.geopulse.auth.session.model;

import java.util.UUID;

/** A new browser session: the raw cookie value is only available here, at creation time. */
public record IssuedBrowserSession(UUID sessionId, String secret, long cookieMaxAgeSeconds) {
}

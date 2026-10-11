package org.github.tess1o.geopulse.auth.session.model;

import java.util.UUID;

/** Raw API session credentials, only available when they are issued or rotated. */
public record IssuedApiSession(UUID sessionId, String accessToken, String refreshToken, long accessTokenExpiresIn) {
}

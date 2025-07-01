package org.github.tess1o.geopulse.user.model;

public record RefreshTokenResponse(String accessToken, String refreshToken, String csrfToken, Long expiresIn) {
}

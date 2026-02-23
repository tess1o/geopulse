package org.github.tess1o.geopulse.auth.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.model.BrowserAuthResponse;
import org.github.tess1o.geopulse.user.model.UserResponse;

import java.util.UUID;

@ApplicationScoped
public class BrowserAuthResponseMapper {

    public BrowserAuthResponse toBrowserAuthResponse(AuthResponse authResponse, String redirectUri) {
        UserResponse user = UserResponse.builder()
                .userId(UUID.fromString(authResponse.getId()))
                .email(authResponse.getEmail())
                .role(authResponse.getRole())
                .fullName(authResponse.getFullName())
                .avatar(authResponse.getAvatar())
                .timezone(authResponse.getTimezone())
                .hasPassword(authResponse.isHasPassword())
                .customMapTileUrl(authResponse.getCustomMapTileUrl())
                .measureUnit(authResponse.getMeasureUnit())
                .defaultRedirectUrl(authResponse.getDefaultRedirectUrl())
                .build();

        return BrowserAuthResponse.builder()
                .user(user)
                .redirectUri(redirectUri)
                .build();
    }
}

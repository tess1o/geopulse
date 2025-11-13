package org.github.tess1o.geopulse.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for user responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private UUID userId;
    private String fullName;
    private String role;
    private String email;
    private String timezone;
    private String avatar;
    private boolean hasPassword;
    private String customMapTileUrl;
    private MeasureUnit measureUnit;
    private String defaultRedirectUrl;
    private boolean shareLocationWithFriends;
    // Don't include passwordHash in responses
}
package org.github.tess1o.geopulse.auth.model;

import lombok.*;

import java.time.Instant;

/**
 * Response DTO for authentication containing JWT tokens.
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthResponse {
    private String id;
    private String email;
    private String fullName;
    private String avatar;
    private String timezone;
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private Instant createdAt;
    private boolean hasPassword;
}
package org.github.tess1o.geopulse.auth.model;

import lombok.*;
import org.github.tess1o.geopulse.user.model.TimelineDisplaySettings;
import org.github.tess1o.geopulse.user.model.UserUiPreferences;

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
    private String role;
    private boolean demoMode;
    private boolean canViewAdmin;
    private boolean adminReadOnly;
    private String fullName;
    private String avatar;
    private String timezone;
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private Instant createdAt;
    private boolean hasPassword;
    /** UI preferences with defaults applied. */
    private UserUiPreferences uiPreferences;
    /** Timeline display preferences with defaults applied, plus server capabilities. */
    private TimelineDisplaySettings timelineDisplay;
}

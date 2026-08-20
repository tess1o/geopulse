package org.github.tess1o.geopulse.auth.model;

import lombok.*;
import org.github.tess1o.geopulse.shared.map.MapRenderMode;
import org.github.tess1o.geopulse.user.model.DistanceUnit;
import org.github.tess1o.geopulse.user.model.TemperatureUnit;

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
    private String customMapTileUrl;
    private String customMapStyleUrl;
    private MapRenderMode mapRenderMode;
    private DistanceUnit distanceUnit;
    private TemperatureUnit temperatureUnit;
    private String defaultRedirectUrl;
    private String dateFormat;
    private String timeFormat;
    private String defaultDateRangePreset;
    private Boolean autoShowTripReplayControls;
}

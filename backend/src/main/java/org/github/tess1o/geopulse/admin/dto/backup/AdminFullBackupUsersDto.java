package org.github.tess1o.geopulse.admin.dto.backup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.ai.model.UserAISettings;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.immich.model.ImmichPreferences;
import org.github.tess1o.geopulse.notes.model.MemosPreferences;
import org.github.tess1o.geopulse.shared.map.MapRenderMode;
import org.github.tess1o.geopulse.user.model.DistanceUnit;
import org.github.tess1o.geopulse.user.model.TemperatureUnit;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminFullBackupUsersDto {
    private String dataType;
    private Instant exportDate;
    private List<UserBackupDto> users;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserBackupDto {
        private UUID id;
        private String email;
        private boolean emailVerified;
        private String passwordHash;
        private String fullName;
        private Instant createdAt;
        private Instant updatedAt;
        private boolean active;
        private Role role;
        private String avatar;
        private String timezone;
        private TimelinePreferences timelinePreferences;
        private ImmichPreferences immichPreferences;
        private MemosPreferences memosPreferences;
        private UserAISettings aiSettings;
        private String customMapTileUrl;
        private String customMapStyleUrl;
        private MapRenderMode mapRenderMode;
        private DistanceUnit distanceUnit;
        private TemperatureUnit temperatureUnit;
        private String defaultRedirectUrl;
        private String dateFormat;
        private String timeFormat;
        private String defaultDateRangePreset;
        private boolean coverageEnabled;
        private Boolean timelineDisplayPathSimplificationEnabled;
        private Double timelineDisplayPathSimplificationTolerance;
        private Integer timelineDisplayPathMaxPoints;
        private Boolean timelineDisplayPathAdaptiveSimplification;
        private Boolean timelineDisplayShowCurrentLocationTelemetry;
        private Boolean timelineDisplayAutoShowTripReplayControls;
        private Boolean timelineDisplayMapMatchingEnabled;
        private List<ApiTokenBackupDto> apiTokens;
        private List<OidcConnectionBackupDto> oidcConnections;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiTokenBackupDto {
        private UUID id;
        private String name;
        private String tokenHash;
        private String tokenPrefix;
        private String tokenSuffix;
        private Instant createdAt;
        private Instant expiresAt;
        private Instant revokedAt;
        private UUID revokedBy;
        private Instant lastUsedAt;
        private String lastUsedIp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OidcConnectionBackupDto {
        private String providerName;
        private String externalUserId;
        private String displayName;
        private String avatarUrl;
        private Instant linkedAt;
        private Instant lastLoginAt;
    }
}

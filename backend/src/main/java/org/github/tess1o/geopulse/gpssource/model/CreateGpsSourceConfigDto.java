package org.github.tess1o.geopulse.gpssource.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateGpsSourceConfigDto {
    @Schema(examples = "OWNTRACKS")
    private GpsSourceType type;
    @Schema(examples = "jane-phone")
    private String username;
    @Schema(examples = "tracker-password")
    private String password;
    @Schema(examples = "f3b1c9e2a7d84c56")
    private String token;
    @Schema(examples = "phone")
    private String deviceId;
    @Schema(examples = "owntracks-secret")
    private String payloadEncryptionSecret;
    @JsonIgnore
    @Schema(hidden = true)
    private UUID userId;
    @Schema(examples = "HTTP")
    private GpsSourceConfigEntity.ConnectionType connectionType;
    // Use Boolean wrapper to allow null from frontend, will be converted to false in mapper if null
    @Schema(examples = "true")
    private Boolean filterInaccurateData;
    @Schema(examples = "100")
    private Integer maxAllowedAccuracy;
    @Schema(examples = "250")
    private Integer maxAllowedSpeed;
    @Schema(examples = "true")
    private Boolean enableDuplicateDetection;
    @Schema(examples = "2")
    private Integer duplicateDetectionThresholdMinutes;

    /** Source with its own credentials (all fields except the API token ones). */
    public CreateGpsSourceConfigDto(GpsSourceType type, String username, String password, String token, String deviceId,
                                    String payloadEncryptionSecret, UUID userId,
                                    GpsSourceConfigEntity.ConnectionType connectionType, Boolean filterInaccurateData,
                                    Integer maxAllowedAccuracy, Integer maxAllowedSpeed,
                                    Boolean enableDuplicateDetection, Integer duplicateDetectionThresholdMinutes) {
        this(type, username, password, token, deviceId, payloadEncryptionSecret, userId, connectionType,
                filterInaccurateData, maxAllowedAccuracy, maxAllowedSpeed, enableDuplicateDetection,
                duplicateDetectionThresholdMinutes, null, null);
    }

    @Schema(description = "Home Assistant only: connect the source to this existing personal API "
            + "token (it needs the `gps:ingest` scope) instead of a source token.")
    private UUID apiTokenId;

    @Schema(description = "Home Assistant only: create a new personal API token for sending locations "
            + "(scopes `read` and `gps:ingest`) and connect the source to it. The response contains the token secret "
            + "once, in `apiTokenSecret`. Needs a signed-in session or a full-access token.")
    private NewApiToken newApiToken;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NewApiToken {
        @Schema(examples = "Home Assistant")
        private String name;
        @Schema(examples = "2027-12-31T23:59:59Z")
        private Instant expiresAt;
    }
}

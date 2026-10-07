package org.github.tess1o.geopulse.gpssource.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@AllArgsConstructor
public class UpdateGpsSourceConfigDto {
    @Schema(hidden = true)
    private String id;
    @Schema(examples = "OWNTRACKS")
    private String type;
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
    @Schema(examples = "false")
    private boolean clearPayloadEncryptionSecret;
    @Schema(hidden = true)
    private String userId;
    @Schema(examples = "HTTP")
    private GpsSourceConfigEntity.ConnectionType connectionType;
    @Schema(examples = "true")
    private boolean filterInaccurateData;
    @Schema(examples = "100")
    private Integer maxAllowedAccuracy;
    @Schema(examples = "250")
    private Integer maxAllowedSpeed;
    @Schema(examples = "true")
    private boolean enableDuplicateDetection;
    @Schema(examples = "2")
    private Integer duplicateDetectionThresholdMinutes;
}

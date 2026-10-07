package org.github.tess1o.geopulse.gpssource.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

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
}

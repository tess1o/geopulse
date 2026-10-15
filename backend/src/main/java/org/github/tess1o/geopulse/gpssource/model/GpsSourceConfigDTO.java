package org.github.tess1o.geopulse.gpssource.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GpsSourceConfigDTO {
    private UUID id;
    private String type;
    private String username;
    private String token;
    private String deviceId;
    private boolean hasPayloadEncryptionSecret;
    private UUID userId;
    private boolean active;
    private GpsSourceConfigEntity.ConnectionType connectionType;
    private boolean filterInaccurateData;
    private Integer maxAllowedAccuracy;
    private Integer maxAllowedSpeed;
    private boolean enableDuplicateDetection;
    private Integer duplicateDetectionThresholdMinutes;
    private GpsSourceIngestStatusDTO ingestStatus; // null until the source receives its first request
    private UUID apiTokenId;     // set when points arrive with a personal API token instead of source credentials
    private String apiTokenName;
    @Schema(description = "Only in the response that created a new API token for this source: its secret, shown once.")
    private String apiTokenSecret;
}

package org.github.tess1o.geopulse.admin.dto.backup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.geofencing.model.entity.AppriseExternalRoutingMode;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBackupConfigDto {
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    @lombok.ToString.Exclude
    @Schema(examples = "a-long-backup-passphrase")
    private String password;
    @Schema(examples = "true")
    private boolean passwordConfigured;
    @Schema(examples = "true")
    private boolean scheduledEnabled;
    @Schema(examples = "0 0 3 * * ?")
    private String scheduledCron;
    @Schema(examples = "/data/geopulse-backups")
    private String localPath;
    @Schema(examples = "7")
    private int retentionCount;
    @Schema(examples = "120")
    private int operationTimeoutMinutes;
    @Schema(examples = "2")
    private int healthMaxAgeDays;
    @Schema(examples = "false")
    private boolean healthAppriseEnabled;
    @Schema(examples = "URLS")
    private AppriseExternalRoutingMode healthAppriseRoutingMode;
    @Schema(examples = "tgram://bottoken/ChatID")
    private String healthAppriseDestination;
    @Schema(examples = "geopulse")
    private String healthAppriseConfigKey;
    @Schema(examples = "admins")
    private String healthAppriseTag;
}

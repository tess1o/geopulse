package org.github.tess1o.geopulse.admin.dto.backup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.geofencing.model.entity.AppriseExternalRoutingMode;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBackupConfigDto {
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    @lombok.ToString.Exclude
    private String password;
    private boolean passwordConfigured;
    private boolean scheduledEnabled;
    private String scheduledCron;
    private String localPath;
    private int retentionCount;
    private int operationTimeoutMinutes;
    private int healthMaxAgeDays;
    private boolean healthAppriseEnabled;
    private AppriseExternalRoutingMode healthAppriseRoutingMode;
    private String healthAppriseDestination;
    private String healthAppriseConfigKey;
    private String healthAppriseTag;
}

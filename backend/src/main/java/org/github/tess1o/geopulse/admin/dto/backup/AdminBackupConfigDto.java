package org.github.tess1o.geopulse.admin.dto.backup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBackupConfigDto {
    private boolean scheduledEnabled;
    private String scheduledCron;
    private String localPath;
    private int retentionCount;
    private int operationTimeoutMinutes;
}

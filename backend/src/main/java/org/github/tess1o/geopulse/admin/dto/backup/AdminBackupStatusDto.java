package org.github.tess1o.geopulse.admin.dto.backup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.api.MessageDescriptor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBackupStatusDto {
    private String operationId;
    private String stagingDatabase;
    private String previousDatabase;
    private String state;
    private boolean restartRequired;
    private boolean backupRunning;
    private boolean restoreRunning;
    private boolean restoreRequired;
    private boolean environmentBlocked;
    private String status;
    private String operation;
    private String fileName;
    private Long sizeBytes;
    private String phase;
    private MessageDescriptor message;
    private Integer progressPercent;
    private Instant startedAt;
    private Instant completedAt;
    private String error;
}

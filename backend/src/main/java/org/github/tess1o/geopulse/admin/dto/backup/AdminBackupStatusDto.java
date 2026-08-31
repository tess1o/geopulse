package org.github.tess1o.geopulse.admin.dto.backup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBackupStatusDto {
    private boolean backupRunning;
    private boolean restoreRunning;
    private boolean restoreRequired;
    private boolean environmentBlocked;
    private String status;
    private String operation;
    private String fileName;
    private Long sizeBytes;
    private String phase;
    private String message;
    private Integer progressPercent;
    private Integer processedUsers;
    private Integer totalUsers;
    private UUID currentUserId;
    private String currentUserEmail;
    private Instant startedAt;
    private Instant completedAt;
    private String error;
}

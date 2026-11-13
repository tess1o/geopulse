package org.github.tess1o.geopulse.admin.dto.backup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
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
    @Schema(description = "Bytes written to the archive while a backup is running.")
    private Long bytesWritten;
    @Schema(description = "Browser download transfer state after the archive is built: running, completed, or failed.")
    private String downloadTransfer;
    @Schema(description = "Bytes streamed to the browser for the current download transfer.")
    private Long downloadTransferredBytes;
    private String phase;
    private MessageDescriptor message;
    private Integer progressPercent;
    private Instant startedAt;
    private Instant completedAt;
    private String error;
}

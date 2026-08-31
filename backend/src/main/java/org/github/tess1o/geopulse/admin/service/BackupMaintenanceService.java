package org.github.tess1o.geopulse.admin.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupStatusDto;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class BackupMaintenanceService {
    private final AtomicBoolean backupRunning = new AtomicBoolean(false);
    private final AtomicBoolean restoreRunning = new AtomicBoolean(false);
    private volatile AdminBackupStatusDto lastStatus = AdminBackupStatusDto.builder()
            .status("idle")
            .build();

    public boolean tryStartBackup(String operation) {
        if (restoreRunning.get()) {
            return false;
        }
        boolean started = backupRunning.compareAndSet(false, true);
        if (started) {
            lastStatus = AdminBackupStatusDto.builder()
                    .backupRunning(true)
                    .restoreRunning(false)
                    .status("running")
                    .operation(operation)
                    .phase("starting")
                    .message("Starting backup")
                    .progressPercent(0)
                    .processedUsers(0)
                    .startedAt(Instant.now())
                    .build();
        }
        return started;
    }

    public boolean tryStartRestore(String operation, String fileName) {
        if (backupRunning.get()) {
            return false;
        }
        boolean started = restoreRunning.compareAndSet(false, true);
        if (started) {
            lastStatus = AdminBackupStatusDto.builder()
                    .backupRunning(false)
                    .restoreRunning(true)
                    .status("running")
                    .operation(operation)
                    .fileName(fileName)
                    .phase("starting")
                    .message("Starting restore")
                    .progressPercent(0)
                    .startedAt(Instant.now())
                    .build();
        }
        return started;
    }

    public void updateBackupFile(String fileName) {
        AdminBackupStatusDto current = lastStatus;
        lastStatus = copyBuilder(current)
                .fileName(fileName)
                .build();
    }

    public void updateProgress(String phase,
                               String message,
                               Integer processedUsers,
                               Integer totalUsers,
                               UUID currentUserId,
                               String currentUserEmail,
                               Integer progressPercent) {
        AdminBackupStatusDto current = lastStatus;
        lastStatus = copyBuilder(current)
                .phase(phase)
                .message(message)
                .processedUsers(processedUsers)
                .totalUsers(totalUsers)
                .currentUserId(currentUserId)
                .currentUserEmail(currentUserEmail)
                .progressPercent(progressPercent)
                .build();
    }

    public void finishSuccess(String fileName, Long sizeBytes) {
        backupRunning.set(false);
        restoreRunning.set(false);
        lastStatus = AdminBackupStatusDto.builder()
                .backupRunning(false)
                .restoreRunning(false)
                .status("completed")
                .operation(lastStatus.getOperation())
                .fileName(fileName)
                .sizeBytes(sizeBytes)
                .phase("completed")
                .message("Backup/restore operation completed")
                .progressPercent(100)
                .processedUsers(lastStatus.getProcessedUsers())
                .totalUsers(lastStatus.getTotalUsers())
                .startedAt(lastStatus.getStartedAt())
                .completedAt(Instant.now())
                .build();
    }

    public void finishFailure(String error) {
        backupRunning.set(false);
        restoreRunning.set(false);
        lastStatus = AdminBackupStatusDto.builder()
                .backupRunning(false)
                .restoreRunning(false)
                .status("failed")
                .operation(lastStatus.getOperation())
                .fileName(lastStatus.getFileName())
                .phase("failed")
                .message(lastStatus.getMessage())
                .progressPercent(lastStatus.getProgressPercent())
                .processedUsers(lastStatus.getProcessedUsers())
                .totalUsers(lastStatus.getTotalUsers())
                .currentUserId(lastStatus.getCurrentUserId())
                .currentUserEmail(lastStatus.getCurrentUserEmail())
                .startedAt(lastStatus.getStartedAt())
                .completedAt(Instant.now())
                .error(error)
                .build();
    }

    public boolean isRestoreRunning() {
        return restoreRunning.get();
    }

    public AdminBackupStatusDto getStatus() {
        AdminBackupStatusDto status = lastStatus;
        status.setBackupRunning(backupRunning.get());
        status.setRestoreRunning(restoreRunning.get());
        return status;
    }

    private AdminBackupStatusDto.AdminBackupStatusDtoBuilder copyBuilder(AdminBackupStatusDto status) {
        return AdminBackupStatusDto.builder()
                .backupRunning(backupRunning.get())
                .restoreRunning(restoreRunning.get())
                .status(status.getStatus())
                .operation(status.getOperation())
                .fileName(status.getFileName())
                .sizeBytes(status.getSizeBytes())
                .phase(status.getPhase())
                .message(status.getMessage())
                .progressPercent(status.getProgressPercent())
                .processedUsers(status.getProcessedUsers())
                .totalUsers(status.getTotalUsers())
                .currentUserId(status.getCurrentUserId())
                .currentUserEmail(status.getCurrentUserEmail())
                .startedAt(status.getStartedAt())
                .completedAt(status.getCompletedAt())
                .error(status.getError());
    }
}

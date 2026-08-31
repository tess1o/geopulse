package org.github.tess1o.geopulse.admin.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupStatusDto;
import org.github.tess1o.geopulse.admin.model.AdminRestoreOperationEntity;
import org.github.tess1o.geopulse.admin.repository.AdminRestoreOperationRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class BackupMaintenanceService {
    public static final String STATUS_VALIDATING = "VALIDATING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_VALIDATION_FAILED = "VALIDATION_FAILED";

    @Inject
    AdminRestoreOperationRepository restoreOperationRepository;

    private final AtomicBoolean backupRunning = new AtomicBoolean(false);
    private final AtomicBoolean restoreRunning = new AtomicBoolean(false);
    private volatile UUID activeRestoreOperationId;
    private volatile AdminBackupStatusDto lastStatus = AdminBackupStatusDto.builder()
            .status("idle")
            .build();

    @Transactional
    void onStart(@Observes StartupEvent ignored) {
        restoreOperationRepository.failActiveRestoresFromPreviousProcess();
    }

    @Transactional
    public boolean tryStartBackup(String operation) {
        if (restoreRunning.get() || restoreOperationRepository.hasBlockingRestore()) {
            return false;
        }
        boolean started = backupRunning.compareAndSet(false, true);
        if (started) {
            lastStatus = AdminBackupStatusDto.builder()
                    .backupRunning(true)
                    .restoreRunning(false)
                    .restoreRequired(restoreOperationRepository.hasFailedRestore())
                    .environmentBlocked(false)
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

    @Transactional
    public boolean tryStartRestore(String operation, String fileName) {
        if (backupRunning.get() || restoreOperationRepository.hasActiveRestore()) {
            return false;
        }
        boolean started = restoreRunning.compareAndSet(false, true);
        if (started) {
            Instant now = Instant.now();
            AdminRestoreOperationEntity restoreOperation = AdminRestoreOperationEntity.builder()
                    .id(UUID.randomUUID())
                    .operation(operation)
                    .status(STATUS_VALIDATING)
                    .fileName(fileName)
                    .phase("starting")
                    .message("Starting restore")
                    .progressPercent(0)
                    .startedAt(now)
                    .updatedAt(now)
                    .build();
            restoreOperationRepository.persist(restoreOperation);
            activeRestoreOperationId = restoreOperation.getId();
            lastStatus = AdminBackupStatusDto.builder()
                    .backupRunning(false)
                    .restoreRunning(true)
                    .restoreRequired(false)
                    .environmentBlocked(true)
                    .status(statusForApi(STATUS_VALIDATING))
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

    @Transactional
    public void markRestoreDataMutationStarted() {
        if (!restoreRunning.get() || activeRestoreOperationId == null) {
            return;
        }
        AdminRestoreOperationEntity operation = restoreOperationRepository.findById(activeRestoreOperationId);
        if (operation == null) {
            return;
        }
        Instant now = Instant.now();
        operation.setStatus(STATUS_RUNNING);
        operation.setPhase("clearing");
        operation.setMessage("Clearing existing application data");
        operation.setUpdatedAt(now);
        lastStatus = copyBuilder(lastStatus)
                .status(statusForApi(STATUS_RUNNING))
                .phase(operation.getPhase())
                .message(operation.getMessage())
                .environmentBlocked(true)
                .restoreRequired(false)
                .build();
    }

    @Transactional
    public void updateProgress(String phase,
                               String message,
                               Integer processedUsers,
                               Integer totalUsers,
                               UUID currentUserId,
                               String currentUserEmail,
                               Integer progressPercent) {
        updateActiveRestoreOperation(phase, message, processedUsers, totalUsers, currentUserId, currentUserEmail, progressPercent);
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

    @Transactional
    public void finishSuccess(String fileName, Long sizeBytes) {
        finishActiveRestoreOperation(STATUS_COMPLETED, fileName, sizeBytes, null);
        backupRunning.set(false);
        restoreRunning.set(false);
        activeRestoreOperationId = null;
        lastStatus = AdminBackupStatusDto.builder()
                .backupRunning(false)
                .restoreRunning(false)
                .restoreRequired(false)
                .environmentBlocked(false)
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

    @Transactional
    public void finishFailure(String error) {
        String persistentRestoreStatus = finishActiveRestoreFailure(error);
        backupRunning.set(false);
        restoreRunning.set(false);
        activeRestoreOperationId = null;
        boolean restoreRequired = STATUS_FAILED.equals(persistentRestoreStatus)
                || restoreOperationRepository.hasFailedRestore();
        String apiStatus = STATUS_VALIDATION_FAILED.equals(persistentRestoreStatus)
                ? "validation_failed"
                : "failed";
        lastStatus = AdminBackupStatusDto.builder()
                .backupRunning(false)
                .restoreRunning(false)
                .restoreRequired(restoreRequired)
                .environmentBlocked(restoreRequired)
                .status(apiStatus)
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

    @Transactional
    public boolean isRestoreRunning() {
        return restoreRunning.get() || restoreOperationRepository.hasActiveRestore();
    }

    @Transactional
    public boolean isRestoreBlocked() {
        return restoreRunning.get() || restoreOperationRepository.hasBlockingRestore();
    }

    @Transactional
    public AdminBackupStatusDto getStatus() {
        AdminBackupStatusDto status = currentDisplayStatus();
        status.setBackupRunning(backupRunning.get());
        status.setRestoreRunning(restoreRunning.get() || restoreOperationRepository.hasActiveRestore());
        status.setRestoreRequired(restoreOperationRepository.hasFailedRestore());
        status.setEnvironmentBlocked(isRestoreBlocked());
        return status;
    }

    private AdminBackupStatusDto currentDisplayStatus() {
        if (backupRunning.get() || restoreRunning.get()) {
            return lastStatus;
        }
        Optional<AdminRestoreOperationEntity> latestRestore = restoreOperationRepository.findLatest();
        if (latestRestore.isEmpty()) {
            return lastStatus;
        }
        Instant lastStartedAt = lastStatus.getStartedAt();
        if (lastStartedAt != null && lastStartedAt.isAfter(latestRestore.get().getStartedAt())) {
            return lastStatus;
        }
        return toStatusDto(latestRestore.get());
    }

    private AdminBackupStatusDto.AdminBackupStatusDtoBuilder copyBuilder(AdminBackupStatusDto status) {
        return AdminBackupStatusDto.builder()
                .backupRunning(backupRunning.get())
                .restoreRunning(restoreRunning.get())
                .restoreRequired(status.isRestoreRequired())
                .environmentBlocked(status.isEnvironmentBlocked())
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

    private void updateActiveRestoreOperation(String phase,
                                              String message,
                                              Integer processedUsers,
                                              Integer totalUsers,
                                              UUID currentUserId,
                                              String currentUserEmail,
                                              Integer progressPercent) {
        if (!restoreRunning.get() || activeRestoreOperationId == null) {
            return;
        }
        AdminRestoreOperationEntity operation = restoreOperationRepository.findById(activeRestoreOperationId);
        if (operation == null) {
            return;
        }
        operation.setPhase(phase);
        operation.setMessage(message);
        operation.setProcessedUsers(processedUsers);
        operation.setTotalUsers(totalUsers);
        operation.setCurrentUserId(currentUserId);
        operation.setCurrentUserEmail(currentUserEmail);
        operation.setProgressPercent(progressPercent);
        operation.setUpdatedAt(Instant.now());
    }

    private void finishActiveRestoreOperation(String status, String fileName, Long sizeBytes, String error) {
        if (activeRestoreOperationId == null) {
            return;
        }
        AdminRestoreOperationEntity operation = restoreOperationRepository.findById(activeRestoreOperationId);
        if (operation == null) {
            return;
        }
        Instant now = Instant.now();
        operation.setStatus(status);
        operation.setFileName(fileName);
        operation.setSizeBytes(sizeBytes);
        operation.setPhase("completed");
        operation.setMessage("Backup/restore operation completed");
        operation.setProgressPercent(100);
        operation.setCompletedAt(now);
        operation.setUpdatedAt(now);
        operation.setError(error);
    }

    private String finishActiveRestoreFailure(String error) {
        if (activeRestoreOperationId == null) {
            return null;
        }
        AdminRestoreOperationEntity operation = restoreOperationRepository.findById(activeRestoreOperationId);
        if (operation == null) {
            return null;
        }
        String failedStatus = STATUS_VALIDATING.equals(operation.getStatus())
                ? STATUS_VALIDATION_FAILED
                : STATUS_FAILED;
        Instant now = Instant.now();
        operation.setStatus(failedStatus);
        operation.setPhase("failed");
        operation.setMessage(lastStatus.getMessage());
        operation.setCompletedAt(now);
        operation.setUpdatedAt(now);
        operation.setError(error);
        return failedStatus;
    }

    private AdminBackupStatusDto toStatusDto(AdminRestoreOperationEntity operation) {
        return AdminBackupStatusDto.builder()
                .backupRunning(false)
                .restoreRunning(STATUS_VALIDATING.equals(operation.getStatus()) || STATUS_RUNNING.equals(operation.getStatus()))
                .restoreRequired(STATUS_FAILED.equals(operation.getStatus()))
                .environmentBlocked(STATUS_FAILED.equals(operation.getStatus()) || STATUS_RUNNING.equals(operation.getStatus()))
                .status(statusForApi(operation.getStatus()))
                .operation(operation.getOperation())
                .fileName(operation.getFileName())
                .sizeBytes(operation.getSizeBytes())
                .phase(operation.getPhase())
                .message(operation.getMessage())
                .progressPercent(operation.getProgressPercent())
                .processedUsers(operation.getProcessedUsers())
                .totalUsers(operation.getTotalUsers())
                .currentUserId(operation.getCurrentUserId())
                .currentUserEmail(operation.getCurrentUserEmail())
                .startedAt(operation.getStartedAt())
                .completedAt(operation.getCompletedAt())
                .error(operation.getError())
                .build();
    }

    private String statusForApi(String status) {
        if (STATUS_VALIDATING.equals(status)) {
            return "validating";
        }
        if (STATUS_RUNNING.equals(status)) {
            return "running";
        }
        if (STATUS_COMPLETED.equals(status)) {
            return "completed";
        }
        if (STATUS_VALIDATION_FAILED.equals(status)) {
            return "validation_failed";
        }
        if (STATUS_FAILED.equals(status)) {
            return "failed";
        }
        return status == null ? "idle" : status.toLowerCase();
    }
}

package org.github.tess1o.geopulse.admin.service;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.Config;
import org.github.tess1o.geopulse.admin.backup.*;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupStatusDto;
import org.github.tess1o.geopulse.admin.dto.backup.MaintenanceStatusDto;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.UUID;

@Startup
@ApplicationScoped
@Slf4j
public class BackupMaintenanceService {
    private static final EnumSet<RestoreOperationState> RUNNING_STATES = EnumSet.of(
            RestoreOperationState.PREPARING, RestoreOperationState.ACTIVATING,
            RestoreOperationState.SWAPPED_PENDING_RESTART);
    private static final EnumSet<RestoreOperationState> ACTIVE_STATES = EnumSet.of(
            RestoreOperationState.PREPARING, RestoreOperationState.ACTIVATING,
            RestoreOperationState.SWAPPED_PENDING_RESTART, RestoreOperationState.ACTIVATION_RETRYABLE,
            RestoreOperationState.ACTIVATION_FAILED);

    @Inject Config config;
    private NativeBackupContext context;
    private Connection coordination;
    private boolean exclusive;
    private boolean operationLocked;
    private boolean backupRunning;
    private RestoreState restore;
    private AdminBackupStatusDto backupStatus = AdminBackupStatusDto.builder().status("idle").build();

    @PostConstruct
    void initialize() throws Exception {
        String instanceId = "GeoPulse-" + UUID.randomUUID();
        PostgresTarget postgres = new PostgresTarget(value("quarkus.datasource.jdbc.url", ""),
                value("quarkus.datasource.username", ""), value("quarkus.datasource.password", ""),
                value("geopulse.backup.restore.username", ""), value("geopulse.backup.restore.password", ""),
                value("geopulse.backup.maintenance-database", "postgres"), instanceId);
        context = new NativeBackupContext(postgres,
                Path.of(value("geopulse.backup.work-path", "/data/geopulse-backups/.work")),
                value("geopulse.backup.binary-directory", ""),
                value("geopulse.ai.encryption.key.location", "file:/app/keys/ai-encryption-key.txt"),
                value("quarkus.application.version", "unknown"));
        restore = context.journal().read();
        reconcileStartup();
        coordination = postgres.connect(postgres.maintenanceDatabase(), true);
        if (!instanceLock("pg_try_advisory_lock_shared")) {
            throw new IllegalStateException("Another GeoPulse backend is activating a restore");
        }
        log.info("Backup coordination initialized for database {}; restoreState={}",
                postgres.database(), restore == null ? "IDLE" : restore.state);
    }

    private String value(String key, String fallback) {
        return config.getOptionalValue(key, String.class).orElse(fallback);
    }

    private void reconcileStartup() throws Exception {
        if (restore == null) return;
        log.info("Reconciling restore operation {} from state {}", restore.operationId, restore.state);
        if (EnumSet.of(RestoreOperationState.ACTIVATING,
                RestoreOperationState.SWAPPED_PENDING_RESTART).contains(restore.state)) {
            restore.validateDatabaseIdentity(context.postgres().database(), true);
            DatabaseCutover.CurrentIdentity identity = new DatabaseCutover(context.postgres()).currentIdentity(restore);
            if (identity == DatabaseCutover.CurrentIdentity.STAGED) {
                transition(RestoreOperationState.COMPLETED, RestorePhase.COMPLETED, null);
                log.info("Restore operation {} completed; staged database is active", restore.operationId);
            } else if (identity == DatabaseCutover.CurrentIdentity.ORIGINAL) {
                transition(RestoreOperationState.ACTIVATION_RETRYABLE, RestorePhase.ACTIVATION_ROLLED_BACK,
                        "Activation did not commit. The original database is active and the prepared restore can be retried.");
                log.warn("Restore operation {} rolled back; activation can be retried", restore.operationId);
            } else {
                transition(RestoreOperationState.ACTIVATION_FAILED, RestorePhase.IDENTITY_MISMATCH,
                        "The configured database identity does not match the recorded original or staged database.");
                log.error("Restore operation {} has an unexpected database identity", restore.operationId);
            }
        } else if (restore.state == RestoreOperationState.PREPARING) {
            String cleanupSuffix = "";
            try {
                new NativeDatabaseBackup(context).discard(restore);
            } catch (Exception cleanupFailure) {
                cleanupSuffix = " Staging cleanup requires administrator attention.";
                log.warn("Interrupted restore {} staging cleanup failed", restore.operationId, cleanupFailure);
            }
            try {
                RestoreJournal.removeTree(context.workPath().resolve(restore.operationId + ".extract"));
            } catch (IOException cleanupFailure) {
                cleanupSuffix = " Temporary-file cleanup requires administrator attention.";
                log.warn("Interrupted restore {} temporary-file cleanup failed", restore.operationId, cleanupFailure);
            }
            transition(RestoreOperationState.PREPARATION_FAILED, RestorePhase.INTERRUPTED,
                    "Restore preparation was interrupted. The original database remains active." + cleanupSuffix);
            log.warn("Restore operation {} was interrupted during preparation", restore.operationId);
        }
    }

    public NativeBackupContext context() { return context; }

    public synchronized boolean tryStartBackup(String operation) {
        if (backupRunning || restoreActive()) return false;
        try {
            if (!acquireOperation()) {
                log.warn("Backup operation {} skipped because another instance holds the maintenance lock", operation);
                return false;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot coordinate backup with other instances", e);
        }
        String operationId = UUID.randomUUID().toString();
        backupRunning = true;
        backupStatus = AdminBackupStatusDto.builder().operationId(operationId).backupRunning(true)
                .status("running").operation(operation).phase("snapshot")
                .message("Creating encrypted PostgreSQL backup").startedAt(Instant.now()).build();
        log.info("Backup operation {} started; type={}", operationId, operation);
        return true;
    }

    public synchronized boolean tryStartFileMutation(String operation) {
        if (backupRunning || restoreActive()) return false;
        try {
            boolean acquired = acquireOperation();
            if (!acquired) log.warn("Backup file operation {} skipped because another instance holds the maintenance lock", operation);
            return acquired;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot coordinate backup file operation", e);
        }
    }

    public synchronized void finishFileMutation() { releaseOperation(); }

    public synchronized boolean tryStartRestore(String operation, String fileName) {
        if (backupRunning || restoreActive()) return false;
        try {
            if (!acquireOperation()) {
                log.warn("Restore request skipped because another instance holds the maintenance lock");
                return false;
            }
            context.journal().archive(restore);
            RestoreState next = new RestoreState();
            next.fileName = fileName;
            context.journal().write(next);
            restore = next;
            log.info("Restore operation {} started; source={}", next.operationId, fileName);
            return true;
        } catch (Exception e) {
            releaseOperation();
            throw new IllegalStateException("Cannot start restore", e);
        }
    }

    public synchronized boolean beginActivation() {
        if (restore == null || !EnumSet.of(RestoreOperationState.PREPARING,
                RestoreOperationState.ACTIVATION_RETRYABLE).contains(restore.state)) return false;
        boolean sharedReleased = false;
        try {
            if (!instanceLock("pg_advisory_unlock_shared")) {
                throw new SQLException("This instance did not own the shared restore coordination lock");
            }
            sharedReleased = true;
            if (!instanceLock("pg_try_advisory_lock")) {
                if (!instanceLock("pg_try_advisory_lock_shared")) {
                    throw new SQLException("Could not restore the shared coordination lock after upgrade contention");
                }
                sharedReleased = false;
                transition(RestoreOperationState.ACTIVATION_RETRYABLE, RestorePhase.ACTIVATION_ROLLED_BACK,
                        "Stop all other backend instances before retrying activation.");
                releaseOperation();
                log.warn("Restore operation {} could not acquire the exclusive instance lock", restore.operationId);
                return false;
            }
            exclusive = true;
            sharedReleased = false;
            transition(RestoreOperationState.ACTIVATING, RestorePhase.CUTOVER, null);
            log.info("Restore operation {} acquired exclusive activation lock", restore.operationId);
            return true;
        } catch (Exception e) {
            boolean sharedRestored = !sharedReleased || exclusive;
            if (sharedReleased && !exclusive) {
                try {
                    sharedRestored = instanceLock("pg_try_advisory_lock_shared");
                    if (!sharedRestored) {
                        e.addSuppressed(new SQLException("Could not restore shared coordination lock"));
                    }
                }
                catch (SQLException lockFailure) { e.addSuppressed(lockFailure); }
            }
            if (sharedRestored) {
                releaseOperation();
            } else {
                transition(RestoreOperationState.ACTIVATION_FAILED, RestorePhase.ACTIVATION_FAILED,
                        "Restore coordination lock could not be recovered. The backend must restart before recovery.");
            }
            log.error("Restore operation {} could not enter activation", restore.operationId, e);
            throw new IllegalStateException("Cannot acquire exclusive restore coordination", e);
        }
    }

    public synchronized void progress(RestorePhase phase) {
        if (restore == null) return;
        restore.updatePhase(phase);
        persist();
        log.info("Restore operation {} entered phase {}", restore.operationId, phase.wireName());
    }

    public synchronized void swapped() {
        transition(RestoreOperationState.SWAPPED_PENDING_RESTART, RestorePhase.RESTARTING, null);
        log.info("Restore operation {} database cutover committed; backend restart required", restore.operationId);
    }

    public synchronized void activationRetryable(String error) {
        transition(RestoreOperationState.ACTIVATION_RETRYABLE, RestorePhase.ACTIVATION_ROLLED_BACK, error);
        log.warn("Restore operation {} activation rolled back: {}", restore.operationId, error);
    }

    public synchronized void abortActivation(String error) { activationRetryable(error); releaseLocks(); }

    public synchronized boolean acquireRetry() {
        if (restore == null || restore.state != RestoreOperationState.ACTIVATION_RETRYABLE) return false;
        try {
            boolean acquired = acquireOperation();
            if (acquired) log.info("Restore operation {} activation retry accepted", restore.operationId);
            return acquired;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot coordinate activation retry", e);
        }
    }

    public synchronized void activationFailed(String error) {
        transition(RestoreOperationState.ACTIVATION_FAILED, RestorePhase.ACTIVATION_FAILED, error);
        log.error("Restore operation {} activation outcome requires administrator recovery: {}", restore.operationId, error);
    }

    public synchronized void discarded() {
        transition(RestoreOperationState.DISCARDED, RestorePhase.DISCARDED, null);
        releaseLocks();
        log.info("Restore operation {} discarded", restore.operationId);
    }

    public synchronized RestoreState restoreState() { return restore; }
    public synchronized String currentOperationId() {
        if (backupRunning) return backupStatus.getOperationId();
        return restore == null ? null : restore.operationId;
    }
    public synchronized void updateBackupFile(String name) { backupStatus.setFileName(name); }

    public synchronized void finishSuccess(String name, Long size) {
        String operationId = backupStatus.getOperationId();
        releaseOperation();
        backupRunning = false;
        backupStatus.setBackupRunning(false);
        backupStatus.setStatus("completed");
        backupStatus.setFileName(name);
        backupStatus.setSizeBytes(size);
        backupStatus.setProgressPercent(100);
        backupStatus.setCompletedAt(Instant.now());
        backupStatus.setError(null);
        log.info("Backup operation {} completed; file={}; sizeBytes={}", operationId, name, size);
    }

    public synchronized void finishFailure(String error) {
        if (restore != null && restore.state == RestoreOperationState.PREPARING) {
            transition(RestoreOperationState.PREPARATION_FAILED, RestorePhase.FAILED, error);
            releaseLocks();
            log.warn("Restore operation {} preparation failed: {}", restore.operationId, error);
        } else {
            String operationId = backupStatus.getOperationId();
            releaseOperation();
            backupRunning = false;
            backupStatus.setBackupRunning(false);
            backupStatus.setStatus("failed");
            backupStatus.setError(error);
            backupStatus.setCompletedAt(Instant.now());
            log.warn("Backup operation {} failed: {}", operationId, error);
        }
    }

    public synchronized boolean isRestoreRunning() { return restore != null && RUNNING_STATES.contains(restore.state); }
    public synchronized boolean isRestoreBlocked() { return restore != null && restore.blocked(); }
    private boolean restoreActive() { return restore != null && ACTIVE_STATES.contains(restore.state); }

    public synchronized MaintenanceStatusDto publicStatus() {
        String state = restore == null ? "IDLE" : restore.state.name();
        return MaintenanceStatusDto.builder().state(state).blocked(isRestoreBlocked())
                .warning(restore != null && restore.state == RestoreOperationState.PREPARING)
                .restarting(restore != null && restore.state == RestoreOperationState.SWAPPED_PENDING_RESTART)
                .message(restore == null ? "GeoPulse is available." : restore.message())
                .backupCreatedAt(restore == null || restore.backupCreatedAt == null ? "" : restore.backupCreatedAt)
                .completedAt(restore != null && restore.state == RestoreOperationState.COMPLETED ? restore.updatedAt : "")
                .build();
    }

    public synchronized AdminBackupStatusDto getStatus() {
        if (restore == null || backupRunning) return backupStatus;
        return AdminBackupStatusDto.builder().operationId(restore.operationId).state(restore.state.name())
                .status(restore.state.name().toLowerCase(Locale.ROOT)).stagingDatabase(restore.stagingDatabase)
                .previousDatabase(restore.previousDatabase).operation("restore").restoreRunning(isRestoreRunning())
                .restartRequired(restore.state == RestoreOperationState.SWAPPED_PENDING_RESTART)
                .environmentBlocked(isRestoreBlocked()).restoreRequired(restore.state == RestoreOperationState.ACTIVATION_FAILED)
                .fileName(restore.fileName).phase(restore.phase.wireName()).progressPercent(restore.progress)
                .message(restore.message()).error(restore.error).startedAt(Instant.parse(restore.startedAt))
                .completedAt(restore.state == RestoreOperationState.COMPLETED ? Instant.parse(restore.updatedAt) : null).build();
    }

    private void transition(RestoreOperationState state, RestorePhase phase, String error) {
        restore.transition(state, phase, error);
        persist();
    }

    private void persist() {
        try { context.journal().write(restore); }
        catch (IOException e) { throw new IllegalStateException("Cannot durably persist restore state", e); }
    }

    private boolean acquireOperation() throws SQLException {
        if (operationLocked) return false;
        operationLocked = operationLock("pg_try_advisory_lock");
        return operationLocked;
    }

    private void releaseOperation() {
        if (!operationLocked) return;
        try { operationLock("pg_advisory_unlock"); }
        catch (SQLException e) { log.warn("Could not explicitly release backup maintenance lock", e); }
        finally { operationLocked = false; }
    }

    private boolean operationLock(String function) throws SQLException {
        return advisory(function, context.postgres().lockKey() ^ 0x0100000000000000L);
    }
    private boolean instanceLock(String function) throws SQLException { return advisory(function, context.postgres().lockKey()); }
    private boolean advisory(String function, long key) throws SQLException {
        try (PreparedStatement statement = coordination.prepareStatement("SELECT " + function + "(?)")) {
            statement.setLong(1, key);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                if (function.startsWith("pg_advisory_lock") && !function.startsWith("pg_advisory_unlock")
                        && !function.startsWith("pg_try_")) {
                    return true;
                }
                return result.getBoolean(1);
            }
        }
    }

    private void releaseLocks() {
        releaseOperation();
        if (!exclusive) return;
        try {
            if (!instanceLock("pg_advisory_unlock")) {
                log.warn("Restore activation lock was already absent during downgrade");
            }
            if (!instanceLock("pg_try_advisory_lock_shared")) {
                throw new SQLException("Could not reacquire shared restore coordination lock");
            }
        } catch (SQLException e) {
            log.error("Could not downgrade the restore activation lock", e);
        } finally {
            exclusive = false;
        }
    }

    @PreDestroy void close() throws SQLException { if (coordination != null) coordination.close(); }
}

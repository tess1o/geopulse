package org.github.tess1o.geopulse.admin.service;

import io.agroal.api.AgroalDataSource;
import io.quarkus.scheduler.Scheduler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.backup.DatabaseCutover;
import org.github.tess1o.geopulse.admin.backup.KeyCipher;
import org.github.tess1o.geopulse.admin.backup.NativeDatabaseBackup;
import org.github.tess1o.geopulse.admin.backup.RestoreJournal;
import org.github.tess1o.geopulse.admin.backup.RestoreOperationState;
import org.github.tess1o.geopulse.admin.backup.RestoreState;
import org.github.tess1o.geopulse.gps.integrations.owntracks.mqtt.OwnTracksMqttService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

@ApplicationScoped
@Slf4j
public class FullRestoreCoordinator {
    static final int MAX_RESTORE_PASSWORD_LENGTH = 1024;

    @Inject BackupMaintenanceService maintenanceService;
    @Inject SystemSettingsService settingsService;
    @Inject OwnTracksMqttService mqtt;
    @Inject AgroalDataSource dataSource;
    @Inject BackendExitCoordinator exitCoordinator;
    @Inject Scheduler scheduler;

    public String start(Path source, String fileName, String password) throws Exception {
        validateRestorePassword(password);
        String safeFileName = safeDisplayName(fileName);
        if (!maintenanceService.tryStartRestore("restore", safeFileName)) {
            throw new IllegalStateException("Another backup or restore is already running");
        }
        RestoreState state = maintenanceService.restoreState();
        char[] passwordChars = password.toCharArray();
        Path owned = null;
        try {
            RestoreJournal.secureDirectory(maintenanceService.context().workPath());
            owned = maintenanceService.context().workPath().resolve(state.operationId + ".gpb");
            RestoreJournal.createPrivateFile(owned);
            try (var output = Files.newOutputStream(owned)) {
                Files.copy(source, output);
            }
            Instant deadline = deadline();
            Path archive = owned;
            Thread.ofVirtual().name("full-restore-" + state.operationId)
                    .start(() -> prepare(archive, passwordChars, state, deadline));
            log.info("Restore operation {} archive staged; preparation worker started", state.operationId);
            return state.operationId;
        } catch (Exception e) {
            Arrays.fill(passwordChars, '\0');
            if (owned != null) Files.deleteIfExists(owned);
            maintenanceService.finishFailure("Could not stage the uploaded backup. Check persistent working storage.");
            log.error("Restore operation {} could not stage its archive", state.operationId, e);
            throw e;
        }
    }

    private void prepare(Path archive, char[] password, RestoreState state, Instant deadline) {
        NativeDatabaseBackup engine = new NativeDatabaseBackup(maintenanceService.context());
        try {
            engine.prepare(archive, password, state, deadline, maintenanceService::progress);
            activatePrepared(engine, state, deadline);
        } catch (Exception e) {
            if (state.state != RestoreOperationState.ACTIVATING
                    && state.state != RestoreOperationState.SWAPPED_PENDING_RESTART) {
                String cleanup = "";
                try {
                    engine.discard(state);
                } catch (Exception cleanupFailure) {
                    cleanup = " Staging cleanup requires administrator attention.";
                    log.warn("Restore operation {} staging cleanup failed", state.operationId, cleanupFailure);
                }
                String reason = e instanceof IOException && e.getMessage() != null
                        ? e.getMessage()
                        : "Database restoration or secret validation failed. Verify backup compatibility and restore permissions.";
                log.warn("Restore operation {} preparation failed", state.operationId, e);
                maintenanceService.finishFailure(reason + cleanup);
            }
        } finally {
            Arrays.fill(password, '\0');
            try {
                Files.deleteIfExists(archive);
            } catch (IOException e) {
                log.warn("Restore archive cleanup requires attention for operation {}", state.operationId, e);
            }
        }
    }

    private void activatePrepared(NativeDatabaseBackup engine, RestoreState state, Instant deadline) throws Exception {
        state.validateDatabaseIdentity(maintenanceService.context().postgres().database(), true);
        engine.refreshDestinationBackupSettings(state, deadline);
        if (!KeyCipher.load(maintenanceService.context().keyLocation()).fingerprint().equals(state.keyFingerprint)) {
            throw new IOException("The destination encryption key changed after restore preparation");
        }
        DatabaseCutover cutover = new DatabaseCutover(maintenanceService.context().postgres());
        cutover.validateReady(state);
        try {
            if (!maintenanceService.beginActivation()) return;
        } catch (RuntimeException coordinationFailure) {
            log.error("Restore operation {} activation coordination failed; requesting backend restart",
                    state.operationId);
            exitCoordinator.restartRequested();
            throw coordinationFailure;
        }

        boolean schedulerPaused = false;
        try {
            if (scheduler.isStarted()) {
                scheduler.pause();
                schedulerPaused = true;
            }
            mqtt.pauseForRestore();
            Thread.sleep(Duration.ofSeconds(3));
        } catch (Exception failure) {
            maintenanceService.abortActivation(
                    "Activation was interrupted before the database pool closed. The prepared restore can be retried.");
            mqtt.resumeAfterRestore();
            if (schedulerPaused) scheduler.resume();
            log.warn("Restore operation {} activation stopped before pool shutdown", state.operationId, failure);
            return;
        }

        try {
            log.info("Restore operation {} closing the application database pool", state.operationId);
            dataSource.close();
            cutover.activate(state);
            maintenanceService.swapped();
        } catch (Exception failure) {
            log.error("Restore operation {} activation raised an error; reconciling database identity",
                    state.operationId, failure);
            try {
                if (cutover.isCommitted(state)) {
                    maintenanceService.swapped();
                } else {
                    maintenanceService.activationRetryable(
                            "Database activation rolled back. The original database remains active after backend restart.");
                }
            } catch (Exception uncertain) {
                log.error("Restore operation {} activation outcome is uncertain", state.operationId, uncertain);
                maintenanceService.activationFailed(
                        "Activation outcome could not be verified. Inspect the recorded database OIDs before recovery.");
            }
        } finally {
            log.info("Restore operation {} requesting backend exit for database-pool restart", state.operationId);
            exitCoordinator.restartRequested();
        }
    }

    public synchronized String retryActivation() {
        RestoreState state = maintenanceService.restoreState();
        if (!maintenanceService.acquireRetry()) {
            throw new IllegalArgumentException("No prepared restore is available for activation retry");
        }
        Thread.ofVirtual().name("restore-activation-retry-" + state.operationId).start(() -> {
            try {
                activatePrepared(new NativeDatabaseBackup(maintenanceService.context()), state, deadline());
            } catch (Exception e) {
                log.warn("Restore operation {} activation retry failed before cutover", state.operationId, e);
                maintenanceService.abortActivation("Activation retry failed before database cutover.");
                mqtt.resumeAfterRestore();
            }
        });
        return state.operationId;
    }

    public synchronized String discardPrepared() throws Exception {
        RestoreState state = maintenanceService.restoreState();
        if (state == null || state.state != RestoreOperationState.ACTIVATION_RETRYABLE) {
            throw new IllegalArgumentException("No retryable prepared restore is available");
        }
        new NativeDatabaseBackup(maintenanceService.context()).discard(state);
        maintenanceService.discarded();
        mqtt.resumeAfterRestore();
        return state.operationId;
    }

    private Instant deadline() {
        return Instant.now().plusSeconds(settingsService.getInteger("backup.operation.timeout-minutes") * 60L);
    }

    private static void validateRestorePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("The source backup password is required");
        }
        if (password.length() > MAX_RESTORE_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("The source backup password must be at most 1024 characters");
        }
    }

    static String safeDisplayName(String fileName) {
        if (fileName == null) return "uploaded-backup.gpb";
        try {
            String base = Path.of(fileName.replace('\\', '/')).getFileName().toString();
            if (base.length() > 255 || !base.matches("[A-Za-z0-9._-]+")) return "uploaded-backup.gpb";
            return base;
        } catch (RuntimeException invalidPath) {
            return "uploaded-backup.gpb";
        }
    }
}

package org.github.tess1o.geopulse.admin.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.agroal.api.AgroalDataSource;
import io.quarkus.scheduler.Scheduler;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.backup.*;
import org.github.tess1o.geopulse.admin.dto.backup.*;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.*;
import java.util.*;

@ApplicationScoped
@Slf4j
public class AdminFullBackupService {
    @Inject
    SystemSettingsService settingsService;
    @Inject
    BackupMaintenanceService maintenanceService;
    @Inject
    org.github.tess1o.geopulse.gps.integrations.owntracks.mqtt.OwnTracksMqttService mqtt;
    @Inject
    AgroalDataSource dataSource;
    @Inject
    BackendExitCoordinator exitCoordinator;
    @Inject
    Scheduler scheduler;

    public AdminBackupConfigDto getConfig() {
        return AdminBackupConfigDto.builder().scheduledEnabled(settingsService.getBoolean("backup.scheduled.enabled"))
                .scheduledCron(settingsService.getString("backup.scheduled.cron")).localPath(settingsService.getString("backup.local.path"))
                .retentionCount(settingsService.getInteger("backup.retention.count")).operationTimeoutMinutes(settingsService.getInteger("backup.operation.timeout-minutes"))
                .passwordConfigured(!settingsService.getString("backup.password").isBlank()).build();
    }

    @Transactional
    public void updateConfig(AdminBackupConfigDto config, UUID adminId) {
        validateConfig(config);
        if (config.getPassword() != null && !config.getPassword().isEmpty())
            settingsService.setValue("backup.password", config.getPassword(), adminId);
        settingsService.setValue("backup.scheduled.enabled", Boolean.toString(config.isScheduledEnabled()), adminId);
        settingsService.setValue("backup.scheduled.cron", config.getScheduledCron(), adminId);
        settingsService.setValue("backup.local.path", config.getLocalPath(), adminId);
        settingsService.setValue("backup.retention.count", Integer.toString(config.getRetentionCount()), adminId);
        settingsService.setValue("backup.operation.timeout-minutes", Integer.toString(config.getOperationTimeoutMinutes()), adminId);
    }

    public void validateConfig(AdminBackupConfigDto config) {
        if (config == null || config.getScheduledCron() == null || config.getScheduledCron().isBlank())
            throw new IllegalArgumentException("A valid cron expression is required");
        if (config.getLocalPath() == null || config.getLocalPath().isBlank())
            throw new IllegalArgumentException("Backup folder path is required");
        if (config.getRetentionCount() < 1 || config.getRetentionCount() > 365)
            throw new IllegalArgumentException("Retention count must be between 1 and 365");
        if (config.getOperationTimeoutMinutes() < 1 || config.getOperationTimeoutMinutes() > 1440)
            throw new IllegalArgumentException("Operation timeout must be between 1 and 1440 minutes");
        if (config.isScheduledEnabled() && (config.getPassword() == null || config.getPassword().isEmpty()) && !getConfig().isPasswordConfigured())
            throw new IllegalArgumentException("Configure a backup password before enabling scheduled backups");
        try {
            Files.createDirectories(Path.of(config.getLocalPath()));
            if (!Files.isWritable(Path.of(config.getLocalPath()))) throw new IOException();
        } catch (IOException e) {
            throw new IllegalArgumentException("Backup folder is not writable", e);
        }
    }

    public String writeLocalBackup() throws Exception {
        char[] password = settingsService.getString("backup.password").toCharArray();
        if (password.length == 0)
            throw new IllegalArgumentException("Configure a backup password first and save a recovery copy outside GeoPulse");
        Path dir = directory();
        Files.createDirectories(dir);
        String name = "geopulse-full-backup-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + ".gpb";
        Path temp = dir.resolve(name + ".tmp");
        maintenanceService.updateBackupFile(name);
        try {
            new NativeDatabaseBackup(maintenanceService.context()).write(temp, password, deadline());
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temp, dir.resolve(name), StandardCopyOption.ATOMIC_MOVE);
            RestoreJournal.forceDirectory(dir);
            List<AdminBackupFileDto> backups = listLocalBackups();
            for (int i = settingsService.getInteger("backup.retention.count"); i < backups.size(); i++)
                Files.deleteIfExists(resolveLocalBackup(backups.get(i).getFileName()));
            return name;
        } finally {
            Arrays.fill(password, '\0');
            Files.deleteIfExists(temp);
        }
    }

    public List<AdminBackupFileDto> listLocalBackups() throws IOException {
        Path dir = directory();
        if (!Files.exists(dir)) return List.of();
        try (var files = Files.list(dir)) {
            return files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && isBackupName(p.getFileName().toString()))
                    .map(p -> {
                        try {
                            return AdminBackupFileDto.builder().fileName(p.getFileName().toString()).sizeBytes(Files.size(p)).lastModifiedAt(Files.getLastModifiedTime(p).toInstant()).build();
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .sorted(Comparator.comparing(AdminBackupFileDto::getLastModifiedAt).reversed()).toList();
        }
    }

    public Path resolveLocalBackup(String name) {
        if (!isBackupName(name) || Path.of(name).getNameCount() != 1)
            throw new IllegalArgumentException("Invalid backup file name");
        Path file = directory().resolve(name).normalize();
        if (!file.startsWith(directory()) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("Backup file not found");
        return file;
    }

    public void deleteLocalBackup(String name) throws IOException {
        Files.delete(resolveLocalBackup(name));
    }

    public String startRestore(Path source, String fileName, String password) throws Exception {
        if (password == null || password.isEmpty())
            throw new IllegalArgumentException("The source backup password is required");
        if (!maintenanceService.tryStartRestore("restore", fileName))
            throw new IllegalStateException("Another backup or restore is already running");
        RestoreState state = maintenanceService.restoreState();
        char[] chars = password.toCharArray();
        Path owned = null;
        try {
            RestoreJournal.secureDirectory(maintenanceService.context().workPath());
            owned = maintenanceService.context().workPath().resolve(state.operationId + ".gpb");
            RestoreJournal.createPrivateFile(owned);
            try (var out = Files.newOutputStream(owned)) {
                Files.copy(source, out);
            }
            Instant deadline = deadline();
            Path archive = owned;
            // This worker must not inherit the request's JTA/CDI context or its maintenance permit.
            Thread.ofVirtual().name("full-restore-" + state.operationId).start(() -> prepare(archive, chars, state, deadline));
            return state.operationId;
        } catch (Exception e) {
            Arrays.fill(chars, '\0');
            if (owned != null) Files.deleteIfExists(owned);
            maintenanceService.finishFailure("Could not stage the uploaded backup. Check persistent working storage.");
            throw e;
        }
    }

    private void prepare(Path archive,char[] password,RestoreState state,Instant deadline) {
        NativeDatabaseBackup engine=new NativeDatabaseBackup(maintenanceService.context());
        try {
            engine.prepare(archive,password,state,deadline,maintenanceService::progress);
            activatePrepared(engine,state,deadline);
        } catch(Exception e) {
            if (!"ACTIVATING".equals(state.state) && !"SWAPPED_PENDING_RESTART".equals(state.state)) {
                String cleanup="";
                try { engine.discard(state); } catch(Exception failure) { cleanup=" Staging cleanup requires administrator attention."; }
                String reason=e instanceof IOException?e.getMessage():"Database restoration or secret validation failed. Verify backup compatibility and restore permissions.";
                log.warn("Full restore preparation failed for operation {} ({})",state.operationId,e.getClass().getSimpleName());
                maintenanceService.finishFailure(reason+cleanup);
            }
        } finally {
            Arrays.fill(password,'\0');
            try { Files.deleteIfExists(archive); } catch(IOException e) { log.warn("Restore archive cleanup requires attention for operation {}",state.operationId); }
        }
    }

    private void activatePrepared(NativeDatabaseBackup engine,RestoreState state,Instant deadline) throws Exception {
        engine.refreshDestinationBackupSettings(state,deadline);
        if (!KeyCipher.load(maintenanceService.context().keyLocation()).fingerprint().equals(state.keyFingerprint))
            throw new IOException("The destination encryption key changed after restore preparation");
        DatabaseCutover cutover=new DatabaseCutover(maintenanceService.context().postgres());
        cutover.validateReady(state);
        if(!maintenanceService.beginActivation()) return;
        boolean schedulerPaused = false;
        try {
            if (scheduler.isStarted()) {
                scheduler.pause();
                schedulerPaused = true;
            }
            mqtt.pauseForRestore();
            Thread.sleep(Duration.ofSeconds(3));
        } catch (Exception failure) {
            maintenanceService.abortActivation("Activation was interrupted before the database pool closed. The prepared restore can be retried.");
            mqtt.resumeAfterRestore();
            if (schedulerPaused) scheduler.resume();
            return;
        }
        try {
            dataSource.close();
            cutover.activate(state);
            maintenanceService.swapped();
        } catch(Exception failure) {
            try {
                if(cutover.isCommitted(state)) maintenanceService.swapped();
                else maintenanceService.activationRetryable("Database activation rolled back. The original database remains active after backend restart.");
            } catch(Exception uncertain) {
                maintenanceService.activationFailed("Activation outcome could not be verified. Inspect the recorded database OIDs before recovery.");
            }
        } finally {
            exitCoordinator.restartRequested();
        }
    }

    public synchronized void retryActivation() {
        RestoreState state=maintenanceService.restoreState();
        if(!maintenanceService.acquireRetry()) throw new IllegalArgumentException("No prepared restore is available for activation retry");
        Thread.ofVirtual().name("restore-activation-retry-"+state.operationId).start(()->{
            try { activatePrepared(new NativeDatabaseBackup(maintenanceService.context()),state,deadline()); }
            catch(Exception e) { maintenanceService.abortActivation("Activation retry failed before database cutover."); mqtt.resumeAfterRestore(); }
        });
    }

    public synchronized void discardPrepared() throws Exception {
        RestoreState state=maintenanceService.restoreState();
        if(state==null||!"ACTIVATION_RETRYABLE".equals(state.state)) throw new IllegalArgumentException("No retryable prepared restore is available");
        new NativeDatabaseBackup(maintenanceService.context()).discard(state);
        maintenanceService.discarded(); mqtt.resumeAfterRestore();
    }

    private Instant deadline() {
        return Instant.now().plusSeconds(settingsService.getInteger("backup.operation.timeout-minutes") * 60L);
    }

    private Path directory() {
        return Path.of(settingsService.getString("backup.local.path")).toAbsolutePath().normalize();
    }

    private boolean isBackupName(String name) {
        return name != null && name.startsWith("geopulse-full-backup-") && name.endsWith(".gpb") && !name.contains("/") && !name.contains("\\");
    }
}

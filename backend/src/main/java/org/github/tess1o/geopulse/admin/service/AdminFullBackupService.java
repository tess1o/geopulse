package org.github.tess1o.geopulse.admin.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.backup.*;
import org.github.tess1o.geopulse.admin.dto.backup.*;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
@Slf4j
public class AdminFullBackupService {
    static final int MIN_NEW_BACKUP_PASSWORD_LENGTH = 12;
    static final int MAX_BACKUP_PASSWORD_LENGTH = 1024;

    @Inject
    SystemSettingsService settingsService;
    @Inject
    BackupMaintenanceService maintenanceService;
    @Inject
    AdminBackupStorageService storageService;
    @Inject
    FullRestoreCoordinator restoreCoordinator;

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
        if (config.getPassword() != null && !config.getPassword().isEmpty()
                && (config.getPassword().length() < MIN_NEW_BACKUP_PASSWORD_LENGTH
                || config.getPassword().length() > MAX_BACKUP_PASSWORD_LENGTH))
            throw new IllegalArgumentException("Backup password must be between 12 and 1024 characters");
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
        Path dir = storageService.directory();
        Files.createDirectories(dir);
        String name = "geopulse-full-backup-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + ".gpb";
        Path temp = storageService.newTemporaryFile(name);
        maintenanceService.updateBackupFile(name);
        String operationId = maintenanceService.currentOperationId();
        try {
            new NativeDatabaseBackup(maintenanceService.context()).write(temp, password, deadline(), operationId);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            storageService.publish(temp, name);
            storageService.prune(settingsService.getInteger("backup.retention.count"), operationId);
            return name;
        } finally {
            Arrays.fill(password, '\0');
            Files.deleteIfExists(temp);
        }
    }

    public List<AdminBackupFileDto> listLocalBackups() throws IOException {
        return storageService.list();
    }

    public Path resolveLocalBackup(String name) {
        return storageService.resolve(name);
    }

    public void deleteLocalBackup(String name) throws IOException {
        storageService.delete(name);
    }

    public String startRestore(Path source, String fileName, String password) throws Exception {
        return restoreCoordinator.start(source, fileName, password);
    }

    public synchronized String retryActivation() { return restoreCoordinator.retryActivation(); }

    public synchronized String discardPrepared() throws Exception { return restoreCoordinator.discardPrepared(); }

    private Instant deadline() {
        return Instant.now().plusSeconds(settingsService.getInteger("backup.operation.timeout-minutes") * 60L);
    }

}

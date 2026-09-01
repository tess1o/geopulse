package org.github.tess1o.geopulse.admin.service;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import io.quarkus.scheduler.Scheduler;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupConfigDto;

@ApplicationScoped
@Slf4j
public class AdminFullBackupScheduler {
    private static final String JOB_ID = "admin-full-backup";
    private static final String TIME_ZONE = "UTC";

    @Inject
    SystemSettingsService settingsService;

    @Inject
    AdminFullBackupService backupService;

    @Inject
    BackupMaintenanceService maintenanceService;

    @Inject
    Scheduler scheduler;

    void onStartup(@Observes StartupEvent ignored) {
        rescheduleFromConfig();
    }

    public synchronized void rescheduleFromConfig() {
        if (!scheduler.isStarted()) {
            log.info("Skipping scheduled full backup registration because Quarkus scheduler is not started");
            return;
        }

        unschedule();

        if (!settingsService.getBoolean("backup.scheduled.enabled")) {
            log.info("Scheduled full backup is disabled");
            return;
        }

        String cron = settingsService.getString("backup.scheduled.cron");
        scheduler.newJob(JOB_ID)
                .setCron(cron)
                .setTimeZone(TIME_ZONE)
                .setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP)
                .setTask(this::runScheduledBackup)
                .schedule();

        log.info("Scheduled full backup registered with cron '{}' in {} timezone", cron, TIME_ZONE);
    }

    public synchronized void validateSchedule(AdminBackupConfigDto config) {
        if (!scheduler.isStarted()) {
            return;
        }
        if (!config.isScheduledEnabled()) {
            return;
        }
        String validationJobId = JOB_ID + "-validation";
        scheduler.unscheduleJob(validationJobId);
        try {
            scheduler.newJob(validationJobId)
                    .setCron(config.getScheduledCron())
                    .setTimeZone(TIME_ZONE)
                    .setTask(execution -> {
                    })
                    .schedule();
        } finally {
            scheduler.unscheduleJob(validationJobId);
        }
    }

    void runScheduledBackup(ScheduledExecution ignored) {
        if (!maintenanceService.tryStartBackup("scheduled")) {
            log.warn("Skipping scheduled full backup because another backup or restore is running");
            return;
        }
        try {
            String fileName = backupService.writeLocalBackup();
            long size = java.nio.file.Files.size(backupService.resolveLocalBackup(fileName));
            maintenanceService.finishSuccess(fileName, size);
            log.info("Scheduled full backup completed: {}", fileName);
        } catch (Exception e) {
            log.error("Scheduled full backup failed; failureType={}", e.getClass().getSimpleName());
            maintenanceService.finishFailure("Scheduled encrypted backup failed. Check client tools, database permissions, backup storage, and free disk space.");
        }
    }

    private void unschedule() {
        if (scheduler.getScheduledJob(JOB_ID) != null) {
            scheduler.unscheduleJob(JOB_ID);
            log.info("Scheduled full backup unregistered");
        }
    }
}

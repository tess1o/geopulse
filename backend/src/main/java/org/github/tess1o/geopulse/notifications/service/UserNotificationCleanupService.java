package org.github.tess1o.geopulse.notifications.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.notifications.repository.UserNotificationRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
@Slf4j
public class UserNotificationCleanupService {

    public static final String KEY_ENABLED = "system.notifications.user-notifications.cleanup.enabled";
    public static final String KEY_RETENTION_DAYS = "system.notifications.user-notifications.retention-days";

    private final UserNotificationRepository notificationRepository;
    private final SystemSettingsService settingsService;
    private final AtomicBoolean cleanupInProgress = new AtomicBoolean(false);

    @Inject
    public UserNotificationCleanupService(UserNotificationRepository notificationRepository,
                                          SystemSettingsService settingsService) {
        this.notificationRepository = notificationRepository;
        this.settingsService = settingsService;
    }

    @Scheduled(every = "${geopulse.notifications.user-notifications.cleanup.scheduler-cadence:12h}",
            identity = "user-notification-cleanup")
    @Transactional
    public void cleanupOldNotifications() {
        if (!cleanupInProgress.compareAndSet(false, true)) {
            return;
        }

        try {
            if (!settingsService.getBoolean(KEY_ENABLED)) {
                return;
            }

            int retentionDays = Math.max(1, settingsService.getInteger(KEY_RETENTION_DAYS));
            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            long deleted = notificationRepository.deleteOlderThan(cutoff);

            if (deleted > 0) {
                log.info("User notification cleanup removed {} record(s) older than {} days", deleted, retentionDays);
            } else {
                log.debug("User notification cleanup found no records older than {} days", retentionDays);
            }
        } catch (Exception e) {
            log.error("Failed to cleanup old user notifications", e);
        } finally {
            cleanupInProgress.set(false);
        }
    }
}

package org.github.tess1o.geopulse.notifications.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupConfigDto;
import org.github.tess1o.geopulse.admin.service.AdminFullBackupService;
import org.github.tess1o.geopulse.geofencing.client.AppriseClientResult;
import org.github.tess1o.geopulse.geofencing.model.entity.AppriseExternalRoutingMode;
import org.github.tess1o.geopulse.geofencing.service.AppriseNotificationService;
import org.github.tess1o.geopulse.notifications.model.NotificationPreferences;
import org.github.tess1o.geopulse.notifications.model.entity.IncidentEntity;
import org.github.tess1o.geopulse.notifications.model.entity.IncidentType;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;
import org.github.tess1o.geopulse.notifications.repository.IncidentRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
@Slf4j
public class BackupHealthMonitoringService {
    private static final String INCIDENT_ID = "backup-health:global";

    private final AdminFullBackupService backupService;
    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final NotificationPublisherService publisher;
    private final AppriseNotificationService apprise;

    public BackupHealthMonitoringService(AdminFullBackupService backupService,
                                         IncidentRepository incidentRepository,
                                         UserRepository userRepository,
                                         NotificationPublisherService publisher,
                                         AppriseNotificationService apprise) {
        this.backupService = backupService;
        this.incidentRepository = incidentRepository;
        this.userRepository = userRepository;
        this.publisher = publisher;
        this.apprise = apprise;
    }

    @Scheduled(every = "1h", delayed = "1m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP, identity = "backup-health-monitor")
    @Transactional
    public void checkHealth() {
        checkHealthAt(Instant.now());
    }

    void checkHealthAt(Instant now) {
        AdminBackupConfigDto config = backupService.getConfig();
        if (!config.isScheduledEnabled() || config.getHealthMaxAgeDays() == 0) return;

        Instant latestBackupAt;
        try {
            latestBackupAt = backupService.getLatestLocalBackupAt();
        } catch (IOException exception) {
            log.warn("Backup health check could not list local backups: {}", exception.getMessage());
            return;
        }

        boolean stale = latestBackupAt == null || latestBackupAt.isBefore(now.minus(Duration.ofDays(config.getHealthMaxAgeDays())));
        IncidentEntity incident = incidentRepository.findById(INCIDENT_ID);
        if (stale && (incident == null || incident.getOpenedAt() == null)) {
            if (incident == null) {
                incident = new IncidentEntity();
                incident.setId(INCIDENT_ID);
                incident.setType(IncidentType.BACKUP_HEALTH);
                incidentRepository.persist(incident);
            }
            incident.setOpenedAt(now);
            notifyAdmins(config, NotificationType.BACKUP_HEALTH_INCIDENT_OPENED,
                    "Backup needs attention", staleMessage(latestBackupAt, config.getHealthMaxAgeDays()), latestBackupAt, now);
        } else if (!stale && incident != null && incident.getOpenedAt() != null) {
            Instant openedAt = incident.getOpenedAt();
            incident.setOpenedAt(null);
            incident.setLastRecoveredAt(now);
            notifyAdmins(config, NotificationType.BACKUP_HEALTH_INCIDENT_RESOLVED,
                    "Backup health restored", "A current local backup is available again.", latestBackupAt, openedAt);
        }
    }

    private void notifyAdmins(AdminBackupConfigDto config, NotificationType type, String title, String message,
                              Instant latestBackupAt, Instant incidentAt) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("latestBackupAt", latestBackupAt == null ? null : latestBackupAt.toString());
        metadata.put("targetRoute", "/app/admin/backups");
        metadata.put("state", type == NotificationType.BACKUP_HEALTH_INCIDENT_OPENED ? "opened" : "resolved");
        for (UserEntity admin : userRepository.findActiveAdmins()) {
            publisher.publish(admin, NotificationSource.BACKUP_HEALTH, type, title, message, metadata,
                    "backup-health:" + admin.getId() + ":" + type + ":" + incidentAt.toEpochMilli(),
                    NotificationPreferences.Channel.builder().inAppEnabled(true).build());
        }
        sendApprise(config, title, message);
    }

    private void sendApprise(AdminBackupConfigDto config, String title, String message) {
        if (!config.isHealthAppriseEnabled()) return;
        AppriseClientResult result = config.getHealthAppriseRoutingMode() == AppriseExternalRoutingMode.KEY_TAG
                ? apprise.sendToConfigKey(config.getHealthAppriseConfigKey(), config.getHealthAppriseTag(), title, message)
                : apprise.sendToDestination(config.getHealthAppriseDestination(), title, message);
        if (!result.isSuccess()) log.warn("Backup health Apprise delivery failed: {}", result.getMessage());
    }

    private String staleMessage(Instant latestBackupAt, int maxAgeDays) {
        if (latestBackupAt == null) return "No local full backup has been found. Configure or run a backup.";
        long ageDays = Math.max(1, Duration.between(latestBackupAt, Instant.now()).toDays());
        return "The latest local backup is " + ageDays + " day(s) old; the configured maximum is " + maxAgeDays + " day(s).";
    }
}

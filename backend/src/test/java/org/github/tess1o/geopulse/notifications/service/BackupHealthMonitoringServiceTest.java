package org.github.tess1o.geopulse.notifications.service;

import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupConfigDto;
import org.github.tess1o.geopulse.admin.service.AdminFullBackupService;
import org.github.tess1o.geopulse.geofencing.service.AppriseNotificationService;
import org.github.tess1o.geopulse.notifications.model.entity.IncidentEntity;
import org.github.tess1o.geopulse.notifications.model.entity.IncidentType;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;
import org.github.tess1o.geopulse.notifications.repository.IncidentRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class BackupHealthMonitoringServiceTest {
    @Mock AdminFullBackupService backups;
    @Mock IncidentRepository incidents;
    @Mock UserRepository users;
    @Mock NotificationPublisherService publisher;
    @Mock AppriseNotificationService apprise;

    @Test
    void opensOneSystemIncidentForAStaleBackup() throws IOException {
        Instant now = Instant.parse("2026-09-08T10:00:00Z");
        UserEntity admin = new UserEntity(); admin.setId(UUID.randomUUID());
        when(backups.getConfig()).thenReturn(config());
        when(backups.getLatestLocalBackupAt()).thenReturn(now.minusSeconds(3 * 86_400));
        when(incidents.findById("backup-health:global")).thenReturn(null);
        when(users.findActiveAdmins()).thenReturn(List.of(admin));

        service().checkHealthAt(now);

        ArgumentCaptor<IncidentEntity> incident = ArgumentCaptor.forClass(IncidentEntity.class);
        verify(incidents).persist(incident.capture());
        assertThat(incident.getValue().getId()).isEqualTo("backup-health:global");
        assertThat(incident.getValue().getType()).isEqualTo(IncidentType.BACKUP_HEALTH);
        verify(publisher).publish(eq(admin), any(), eq(NotificationType.BACKUP_HEALTH_INCIDENT_OPENED), any(), any(), any(), any(), any());
    }

    @Test
    void resolvesAnOpenIncidentWhenABackupIsCurrent() throws IOException {
        Instant now = Instant.parse("2026-09-08T10:00:00Z");
        UserEntity admin = new UserEntity(); admin.setId(UUID.randomUUID());
        IncidentEntity incident = new IncidentEntity(); incident.setOpenedAt(now.minusSeconds(86_400));
        when(backups.getConfig()).thenReturn(config());
        when(backups.getLatestLocalBackupAt()).thenReturn(now.minusSeconds(60));
        when(incidents.findById("backup-health:global")).thenReturn(incident);
        when(users.findActiveAdmins()).thenReturn(List.of(admin));

        service().checkHealthAt(now);

        assertThat(incident.getOpenedAt()).isNull();
        assertThat(incident.getLastRecoveredAt()).isEqualTo(now);
        verify(publisher).publish(eq(admin), any(), eq(NotificationType.BACKUP_HEALTH_INCIDENT_RESOLVED), any(), any(), any(), any(), any());
    }

    @Test
    void doesNotNotifyAgainWhileTheBackupIncidentIsOpen() throws IOException {
        Instant now = Instant.parse("2026-09-08T10:00:00Z");
        IncidentEntity incident = new IncidentEntity(); incident.setOpenedAt(now.minusSeconds(86_400));
        when(backups.getConfig()).thenReturn(config());
        when(backups.getLatestLocalBackupAt()).thenReturn(null);
        when(incidents.findById("backup-health:global")).thenReturn(incident);

        service().checkHealthAt(now);

        verify(incidents, never()).persist(any(IncidentEntity.class));
        verifyNoInteractions(users, publisher, apprise);
    }

    @Test
    void doesNotCheckBackupHealthWhenScheduledBackupsAreDisabled() throws IOException {
        when(backups.getConfig()).thenReturn(AdminBackupConfigDto.builder().healthMaxAgeDays(2).scheduledEnabled(false).build());

        service().checkHealthAt(Instant.parse("2026-09-08T10:00:00Z"));

        verifyNoInteractions(incidents, users, publisher, apprise);
    }

    private BackupHealthMonitoringService service() {
        return new BackupHealthMonitoringService(backups, incidents, users, publisher, apprise);
    }

    private AdminBackupConfigDto config() {
        return AdminBackupConfigDto.builder().scheduledEnabled(true).healthMaxAgeDays(2).build();
    }
}

package org.github.tess1o.geopulse.notifications.service;

import io.quarkus.scheduler.Scheduled;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.notifications.repository.UserNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class UserNotificationCleanupServiceTest {

    @Mock
    UserNotificationRepository notificationRepository;

    @Mock
    SystemSettingsService settingsService;

    private UserNotificationCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        cleanupService = new UserNotificationCleanupService(notificationRepository, settingsService);
    }

    @Test
    void shouldSkipCleanupWhenDisabled() {
        when(settingsService.getBoolean(UserNotificationCleanupService.KEY_ENABLED)).thenReturn(false);

        cleanupService.cleanupOldNotifications();

        verifyNoInteractions(notificationRepository);
        verify(settingsService, never()).getInteger(UserNotificationCleanupService.KEY_RETENTION_DAYS);
    }

    @Test
    void shouldDeleteNotificationsOlderThanRetentionWindow() {
        when(settingsService.getBoolean(UserNotificationCleanupService.KEY_ENABLED)).thenReturn(true);
        when(settingsService.getInteger(UserNotificationCleanupService.KEY_RETENTION_DAYS)).thenReturn(90);
        when(notificationRepository.deleteOlderThan(any())).thenReturn(7L);

        Instant before = Instant.now();
        cleanupService.cleanupOldNotifications();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(notificationRepository).deleteOlderThan(cutoffCaptor.capture());
        Instant cutoff = cutoffCaptor.getValue();

        assertThat(cutoff).isBetween(
                before.minus(90, ChronoUnit.DAYS).minusSeconds(1),
                after.minus(90, ChronoUnit.DAYS).plusSeconds(1)
        );
    }

    @Test
    void shouldClampRetentionToAtLeastOneDay() {
        when(settingsService.getBoolean(UserNotificationCleanupService.KEY_ENABLED)).thenReturn(true);
        when(settingsService.getInteger(UserNotificationCleanupService.KEY_RETENTION_DAYS)).thenReturn(0);
        when(notificationRepository.deleteOlderThan(any())).thenReturn(0L);

        Instant before = Instant.now();
        cleanupService.cleanupOldNotifications();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(notificationRepository).deleteOlderThan(cutoffCaptor.capture());

        Instant cutoff = cutoffCaptor.getValue();
        assertThat(cutoff).isBetween(
                before.minus(1, ChronoUnit.DAYS).minusSeconds(1),
                after.minus(1, ChronoUnit.DAYS).plusSeconds(1)
        );
    }

    @Test
    void shouldResetInProgressFlagAfterFailure() {
        when(settingsService.getBoolean(UserNotificationCleanupService.KEY_ENABLED)).thenReturn(true);
        when(settingsService.getInteger(UserNotificationCleanupService.KEY_RETENTION_DAYS)).thenReturn(90);
        when(notificationRepository.deleteOlderThan(any()))
                .thenThrow(new RuntimeException("db issue"))
                .thenReturn(0L);

        cleanupService.cleanupOldNotifications();
        cleanupService.cleanupOldNotifications();

        verify(notificationRepository, times(2)).deleteOlderThan(any());
    }

    @Test
    void shouldUseEnvConfigurableSchedulerCadence() throws Exception {
        Scheduled scheduled = UserNotificationCleanupService.class
                .getMethod("cleanupOldNotifications")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.every())
                .isEqualTo("${geopulse.notifications.user-notifications.cleanup.scheduler-cadence:12h}");
    }
}

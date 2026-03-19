package org.github.tess1o.geopulse.geofencing.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceEventRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
@Slf4j
public class GeofenceEventCleanupService {

    public static final String KEY_ENABLED = "system.notifications.geofence-events.cleanup.enabled";
    public static final String KEY_INTERVAL_DAYS = "system.notifications.geofence-events.cleanup.interval-days";
    public static final String KEY_RETENTION_DAYS = "system.notifications.geofence-events.retention-days";

    private final GeofenceEventRepository eventRepository;
    private final SystemSettingsService settingsService;
    private final AtomicBoolean cleanupInProgress = new AtomicBoolean(false);

    private volatile Instant lastCleanupRun = Instant.EPOCH;

    @Inject
    public GeofenceEventCleanupService(GeofenceEventRepository eventRepository,
                                       SystemSettingsService settingsService) {
        this.eventRepository = eventRepository;
        this.settingsService = settingsService;
    }

    @Scheduled(every = "12h", identity = "geofence-event-cleanup")
    @Transactional
    public void cleanupOldEvents() {
        if (!cleanupInProgress.compareAndSet(false, true)) {
            return;
        }

        try {
            if (!settingsService.getBoolean(KEY_ENABLED)) {
                return;
            }

            int intervalDays = Math.max(1, settingsService.getInteger(KEY_INTERVAL_DAYS));
            if (!isCleanupDue(intervalDays)) {
                return;
            }

            int retentionDays = Math.max(1, settingsService.getInteger(KEY_RETENTION_DAYS));
            Instant now = Instant.now();
            Instant cutoff = now.minus(retentionDays, ChronoUnit.DAYS);
            long deleted = eventRepository.deleteOlderThan(cutoff);
            lastCleanupRun = now;

            if (deleted > 0) {
                log.info("Geofence event cleanup removed {} event(s) older than {} days", deleted, retentionDays);
            } else {
                log.debug("Geofence event cleanup found no events older than {} days", retentionDays);
            }
        } catch (Exception e) {
            log.error("Failed to cleanup old geofence events", e);
        } finally {
            cleanupInProgress.set(false);
        }
    }

    private boolean isCleanupDue(int intervalDays) {
        Instant lastRun = lastCleanupRun;
        return lastRun.equals(Instant.EPOCH) || Duration.between(lastRun, Instant.now()).toDays() >= intervalDays;
    }
}

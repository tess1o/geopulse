package org.github.tess1o.geopulse.notifications.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.notifications.model.NotificationPreferences;
import org.github.tess1o.geopulse.notifications.model.entity.IncidentEntity;
import org.github.tess1o.geopulse.notifications.model.entity.IncidentType;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;
import org.github.tess1o.geopulse.notifications.repository.IncidentRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
@Slf4j
public class GpsHealthMonitoringService {
    private final UserRepository userRepository;
    private final GpsPointRepository pointRepository;
    private final IncidentRepository incidentRepository;
    private final NotificationPreferencesService preferencesService;
    private final NotificationPublisherService publisher;

    public GpsHealthMonitoringService(UserRepository userRepository, GpsPointRepository pointRepository,
                                      IncidentRepository incidentRepository,
                                      NotificationPreferencesService preferencesService, NotificationPublisherService publisher) {
        this.userRepository = userRepository;
        this.pointRepository = pointRepository;
        this.incidentRepository = incidentRepository;
        this.preferencesService = preferencesService;
        this.publisher = publisher;
    }

    @Scheduled(every = "1m", delayed = "10s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP, identity = "gps-health-monitor")
    @Transactional
    public void checkHealth() {
        checkHealthAt(Instant.now());
    }

    void checkHealthAt(Instant now) {
        for (UserEntity user : userRepository.findActiveUsers()) {
            try {
                checkUser(user, now);
            } catch (Exception exception) {
                log.warn("GPS health check failed for user {}: {}", user.getId(), exception.getMessage());
            }
        }
    }

    private void checkUser(UserEntity user, Instant now) {
        NotificationPreferences preferences = preferencesService.getEntityPreferences(user);
        if (!preferences.isGpsHealthEnabled()) return;

        Instant monitoringStartedAt = preferences.getGpsMonitoringStartedAt();
        if (monitoringStartedAt == null) {
            preferences.setGpsMonitoringStartedAt(now);
            log.info("GPS health monitoring armed for user {}: threshold={}m", user.getId(), preferences.getGpsSilenceMinutes());
            return;
        }
        Duration threshold = Duration.ofMinutes(preferences.getGpsSilenceMinutes());
        Instant eligibleAt = monitoringStartedAt.plus(threshold);
        String incidentId = "gps-health:" + user.getId();
        IncidentEntity incident = incidentRepository.findById(incidentId);
        if (now.isBefore(eligibleAt) && (incident == null || incident.getOpenedAt() == null)) {
            log.debug("GPS health monitor grace period active: user={}, eligibleAt={}, remainingSeconds={}",
                    user.getId(), eligibleAt, Duration.between(now, eligibleAt).toSeconds());
            return;
        }

        Instant latestReceivedAt = pointRepository.findLatestReceivedByUserId(user.getId());
        Instant cutoff = now.minus(threshold);
        boolean quiet = latestReceivedAt == null || latestReceivedAt.isBefore(cutoff);
        if (quiet && (incident == null || incident.getOpenedAt() == null)) {
            if (incident == null) {
                incident = new IncidentEntity();
                incident.setId(incidentId);
                incident.setUserId(user.getId());
                incident.setType(IncidentType.GPS_HEALTH);
                incidentRepository.persist(incident);
            }
            incident.setOpenedAt(now);
            log.warn("GPS health incident opened for user {}: threshold={}m, latestReceivedAt={}",
                    user.getId(), preferences.getGpsSilenceMinutes(), latestReceivedAt);
            String body = "GeoPulse has not received a GPS point for " + preferences.getGpsSilenceMinutes() + " minutes.";
            publisher.publish(user, NotificationSource.GPS_HEALTH, NotificationType.GPS_HEALTH_INCIDENT_OPENED,
                    "GPS tracking is quiet", body,
                    metadata(latestReceivedAt, "opened", "/app/notifications"),
                    "gps-health:" + user.getId() + ":opened:" + now.toEpochMilli(), preferences.getGpsHealth());
        } else if (!quiet && incident != null && incident.getOpenedAt() != null) {
            Instant openedAt = incident.getOpenedAt();
            incident.setOpenedAt(null);
            incident.setLastRecoveredAt(now);
            log.info("GPS health incident resolved for user {} after {}", user.getId(),
                    humanDuration(Duration.between(openedAt, now)));
            publisher.publish(user, NotificationSource.GPS_HEALTH, NotificationType.GPS_HEALTH_INCIDENT_RESOLVED,
                    "GPS tracking resumed", "GeoPulse is receiving GPS points again.",
                    metadata(latestReceivedAt, "resolved", "/app/notifications"),
                    "gps-health:" + user.getId() + ":resolved:" + openedAt.toEpochMilli(), preferences.getGpsHealth());
        }
    }

    private Map<String, Object> metadata(Instant latestReceivedAt, String state, String targetRoute) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("state", state);
        metadata.put("latestReceivedAt", latestReceivedAt == null ? null : latestReceivedAt.toString());
        metadata.put("targetRoute", targetRoute);
        return metadata;
    }

    private String humanDuration(Duration duration) {
        long minutes = Math.max(1, duration.toMinutes());
        return minutes >= 60 ? (minutes / 60) + " hour(s)" : minutes + " minute(s)";
    }
}

package org.github.tess1o.geopulse.geofencing.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geofencing.client.AppriseClientResult;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceDeliveryStatus;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.NotificationTemplateEntity;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceEventRepository;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
@Slf4j
public class GeofenceDeliveryService {

    private static final int DELIVERY_BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 5;

    private final GeofenceEventRepository eventRepository;
    private final AppriseNotificationService appriseNotificationService;

    @Inject
    public GeofenceDeliveryService(GeofenceEventRepository eventRepository,
                                   AppriseNotificationService appriseNotificationService) {
        this.eventRepository = eventRepository;
        this.appriseNotificationService = appriseNotificationService;
    }

    @Scheduled(every = "${geopulse.geofence.delivery.interval:30s}")
    @Transactional
    public void processPendingDeliveries() {
        List<GeofenceEventEntity> pending = eventRepository.findPendingForDelivery(DELIVERY_BATCH_SIZE, MAX_ATTEMPTS);
        if (pending.isEmpty()) {
            return;
        }

        if (!appriseNotificationService.isEnabledAndConfigured()) {
            pending.forEach(event -> markSkipped(event, "Apprise delivery disabled or not configured"));
            return;
        }

        for (GeofenceEventEntity event : pending) {
            processEvent(event);
        }
    }

    private void processEvent(GeofenceEventEntity event) {
        NotificationTemplateEntity template = event.getTemplate();
        if (template == null || !Boolean.TRUE.equals(template.getEnabled())
                || template.getDestination() == null || template.getDestination().isBlank()) {
            markSkipped(event, "No enabled template destination configured");
            return;
        }

        AppriseClientResult result = appriseNotificationService.sendToDestination(
                template.getDestination(),
                event.getTitle(),
                event.getMessage()
        );

        int attempts = event.getDeliveryAttempts() == null ? 0 : event.getDeliveryAttempts();
        event.setDeliveryAttempts(attempts + 1);

        if (result.isSuccess()) {
            event.setDeliveryStatus(GeofenceDeliveryStatus.SENT);
            event.setDeliveredAt(Instant.now());
            event.setLastDeliveryError(null);
            return;
        }

        event.setLastDeliveryError(result.getMessage());
        if (event.getDeliveryAttempts() >= MAX_ATTEMPTS) {
            event.setDeliveryStatus(GeofenceDeliveryStatus.FAILED);
        } else {
            event.setDeliveryStatus(GeofenceDeliveryStatus.PENDING);
        }
    }

    private void markSkipped(GeofenceEventEntity event, String reason) {
        event.setDeliveryStatus(GeofenceDeliveryStatus.SKIPPED);
        event.setDeliveredAt(Instant.now());
        event.setLastDeliveryError(reason);
    }
}

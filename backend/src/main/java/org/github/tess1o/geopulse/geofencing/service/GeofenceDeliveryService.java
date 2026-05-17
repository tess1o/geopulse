package org.github.tess1o.geopulse.geofencing.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geofencing.client.AppriseClientResult;
import org.github.tess1o.geopulse.geofencing.model.entity.AppriseExternalRoutingMode;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceDeliveryStatus;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.NotificationTemplateEntity;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceEventRepository;
import org.github.tess1o.geopulse.notifications.service.GeofenceNotificationProjectionService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class GeofenceDeliveryService {

    private static final int DELIVERY_BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 5;

    private final GeofenceEventRepository eventRepository;
    private final AppriseNotificationService appriseNotificationService;
    private final GeofenceNotificationProjectionService notificationProjectionService;

    @Inject
    public GeofenceDeliveryService(GeofenceEventRepository eventRepository,
                                   AppriseNotificationService appriseNotificationService,
                                   GeofenceNotificationProjectionService notificationProjectionService) {
        this.eventRepository = eventRepository;
        this.appriseNotificationService = appriseNotificationService;
        this.notificationProjectionService = notificationProjectionService;
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
        if (!isExternallyDeliverable(template)) {
            markSkipped(event, "No enabled template external routing configured");
            return;
        }

        String consistencyError = validateEventOwnershipConsistency(event, template);
        if (consistencyError != null) {
            log.warn("Skipping geofence delivery for event {} due to ownership mismatch: {}",
                    event != null ? event.getId() : null,
                    consistencyError);
            markSkipped(event, consistencyError);
            return;
        }

        log.info("Processing geofence delivery eventId={} ownerUserId={} subjectUserId={} pointUserId={} templateId={} templateUserId={} ruleId={}",
                event.getId(),
                event.getOwnerUser() != null ? event.getOwnerUser().getId() : null,
                event.getSubjectUser() != null ? event.getSubjectUser().getId() : null,
                event.getPoint() != null && event.getPoint().getUser() != null ? event.getPoint().getUser().getId() : null,
                template != null ? template.getId() : null,
                template != null && template.getUser() != null ? template.getUser().getId() : null,
                event.getRule() != null ? event.getRule().getId() : null
        );

        AppriseClientResult result = appriseNotificationService.sendToTemplate(template, event.getTitle(), event.getMessage());

        int attempts = event.getDeliveryAttempts() == null ? 0 : event.getDeliveryAttempts();
        event.setDeliveryAttempts(attempts + 1);

        if (result.isSuccess()) {
            event.setDeliveryStatus(GeofenceDeliveryStatus.SENT);
            event.setDeliveredAt(Instant.now());
            event.setLastDeliveryError(null);
            syncNotificationDeliveryStatus(event);
            return;
        }

        event.setLastDeliveryError(result.getMessage());
        if (event.getDeliveryAttempts() >= MAX_ATTEMPTS) {
            event.setDeliveryStatus(GeofenceDeliveryStatus.FAILED);
        } else {
            event.setDeliveryStatus(GeofenceDeliveryStatus.PENDING);
        }
        syncNotificationDeliveryStatus(event);
    }

    private boolean isExternallyDeliverable(NotificationTemplateEntity template) {
        if (template == null || !Boolean.TRUE.equals(template.getEnabled())) {
            return false;
        }
        if (template.getExternalRoutingMode() == AppriseExternalRoutingMode.KEY_TAG) {
            return template.getAppriseConfigKey() != null && !template.getAppriseConfigKey().isBlank();
        }
        return template.getDestination() != null && !template.getDestination().isBlank();
    }

    private String validateEventOwnershipConsistency(GeofenceEventEntity event, NotificationTemplateEntity template) {
        if (event == null || event.getOwnerUser() == null || event.getOwnerUser().getId() == null
                || event.getSubjectUser() == null || event.getSubjectUser().getId() == null) {
            return "Event owner/subject metadata is missing";
        }

        UUID ownerUserId = event.getOwnerUser().getId();
        UUID subjectUserId = event.getSubjectUser().getId();

        if (template == null || template.getUser() == null || template.getUser().getId() == null) {
            return "Template owner metadata is missing";
        }
        if (!ownerUserId.equals(template.getUser().getId())) {
            return "Template owner does not match event owner";
        }

        if (event.getRule() != null && event.getRule().getOwnerUser() != null && event.getRule().getOwnerUser().getId() != null
                && !ownerUserId.equals(event.getRule().getOwnerUser().getId())) {
            return "Rule owner does not match event owner";
        }

        if (event.getPoint() != null && event.getPoint().getUser() != null && event.getPoint().getUser().getId() != null
                && !subjectUserId.equals(event.getPoint().getUser().getId())) {
            return "Point user does not match event subject";
        }

        return null;
    }

    private void markSkipped(GeofenceEventEntity event, String reason) {
        event.setDeliveryStatus(GeofenceDeliveryStatus.SKIPPED);
        event.setDeliveredAt(Instant.now());
        event.setLastDeliveryError(reason);
        syncNotificationDeliveryStatus(event);
    }

    private void syncNotificationDeliveryStatus(GeofenceEventEntity event) {
        if (event.getOwnerUser() == null || event.getOwnerUser().getId() == null || event.getId() == null) {
            return;
        }
        notificationProjectionService.syncDeliveryStatus(
                event.getOwnerUser().getId(),
                event.getId(),
                event.getDeliveryStatus()
        );
    }
}

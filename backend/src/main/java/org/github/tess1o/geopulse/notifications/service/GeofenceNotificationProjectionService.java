package org.github.tess1o.geopulse.notifications.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceDeliveryStatus;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventType;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;
import org.github.tess1o.geopulse.notifications.model.entity.UserNotificationEntity;
import org.github.tess1o.geopulse.notifications.repository.UserNotificationRepository;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class GeofenceNotificationProjectionService {

    private final UserNotificationRepository notificationRepository;

    @Inject
    public GeofenceNotificationProjectionService(UserNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void publishSnapshot(GeofenceEventEntity geofenceEvent, Map<String, Object> metadataSnapshot) {
        if (geofenceEvent == null || geofenceEvent.getId() == null || geofenceEvent.getOwnerUser() == null) {
            return;
        }

        String dedupeKey = dedupeKeyForGeofenceEvent(geofenceEvent.getId());
        NotificationType type = geofenceEvent.getEventType() == GeofenceEventType.ENTER
                ? NotificationType.GEOFENCE_ENTER
                : NotificationType.GEOFENCE_LEAVE;

        UserNotificationEntity entity = notificationRepository.findByDedupeKey(dedupeKey)
                .orElseGet(UserNotificationEntity::new);

        entity.setOwnerUser(geofenceEvent.getOwnerUser());
        entity.setSource(NotificationSource.GEOFENCE);
        entity.setType(type);
        entity.setTitle(geofenceEvent.getTitle());
        entity.setMessage(geofenceEvent.getMessage());
        entity.setOccurredAt(geofenceEvent.getOccurredAt());
        entity.setSeenAt(geofenceEvent.getSeenAt());
        entity.setDeliveryStatus(geofenceEvent.getDeliveryStatus());
        entity.setObjectRef(String.valueOf(geofenceEvent.getId()));
        entity.setMetadata(metadataSnapshot);
        entity.setDedupeKey(dedupeKey);

        if (entity.getId() == null) {
            notificationRepository.persist(entity);
        }
    }

    @Transactional
    public void syncDeliveryStatus(UUID ownerUserId, Long geofenceEventId, GeofenceDeliveryStatus deliveryStatus) {
        if (ownerUserId == null || geofenceEventId == null) {
            return;
        }

        String objectRef = String.valueOf(geofenceEventId);
        notificationRepository.findBySourceAndObjectRefAndOwner(NotificationSource.GEOFENCE, objectRef, ownerUserId)
                .ifPresentOrElse(
                        entity -> entity.setDeliveryStatus(deliveryStatus),
                        () -> notificationRepository.findByDedupeKey(dedupeKeyForGeofenceEvent(geofenceEventId))
                                .ifPresent(entity -> entity.setDeliveryStatus(deliveryStatus))
                );
    }

    @Transactional
    public void syncSeen(UUID ownerUserId, Long geofenceEventId, Instant seenAt) {
        if (ownerUserId == null || geofenceEventId == null || seenAt == null) {
            return;
        }
        notificationRepository.markSeenBySourceAndObjectRefAndOwner(
                NotificationSource.GEOFENCE,
                String.valueOf(geofenceEventId),
                ownerUserId,
                seenAt
        );
    }

    @Transactional
    public void syncAllSeen(UUID ownerUserId, Instant seenAt) {
        if (ownerUserId == null || seenAt == null) {
            return;
        }
        notificationRepository.markAllSeenBySourceAndOwner(NotificationSource.GEOFENCE, ownerUserId, seenAt);
    }

    private String dedupeKeyForGeofenceEvent(Long geofenceEventId) {
        return "geofence-event:" + geofenceEventId;
    }
}

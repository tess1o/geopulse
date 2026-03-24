package org.github.tess1o.geopulse.notifications.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceEventRepository;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.UserNotificationEntity;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class GeofenceNotificationSeenSyncAdapter implements NotificationSeenSyncAdapter {

    private final GeofenceEventRepository geofenceEventRepository;

    @Inject
    public GeofenceNotificationSeenSyncAdapter(GeofenceEventRepository geofenceEventRepository) {
        this.geofenceEventRepository = geofenceEventRepository;
    }

    @Override
    public NotificationSource source() {
        return NotificationSource.GEOFENCE;
    }

    @Override
    public void markSeen(UUID ownerUserId, UserNotificationEntity notification, Instant seenAt) {
        Long geofenceEventId = parseGeofenceEventId(notification);
        geofenceEventRepository.markSeenByOwnerAndId(ownerUserId, geofenceEventId, seenAt);
    }

    @Override
    public void markAllSeen(UUID ownerUserId, Instant seenAt) {
        geofenceEventRepository.markAllSeenByOwner(ownerUserId, seenAt);
    }

    private Long parseGeofenceEventId(UserNotificationEntity notification) {
        if (notification == null || notification.getObjectRef() == null || notification.getObjectRef().isBlank()) {
            throw new IllegalArgumentException("Geofence notification is missing objectRef");
        }
        try {
            return Long.parseLong(notification.getObjectRef().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid geofence objectRef: " + notification.getObjectRef(), e);
        }
    }
}

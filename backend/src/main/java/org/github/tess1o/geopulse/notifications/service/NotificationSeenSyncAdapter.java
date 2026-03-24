package org.github.tess1o.geopulse.notifications.service;

import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.UserNotificationEntity;

import java.time.Instant;
import java.util.UUID;

public interface NotificationSeenSyncAdapter {

    NotificationSource source();

    void markSeen(UUID ownerUserId, UserNotificationEntity notification, Instant seenAt);

    void markAllSeen(UUID ownerUserId, Instant seenAt);
}

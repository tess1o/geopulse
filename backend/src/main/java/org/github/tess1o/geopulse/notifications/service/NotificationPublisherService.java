package org.github.tess1o.geopulse.notifications.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geofencing.client.AppriseClientResult;
import org.github.tess1o.geopulse.geofencing.model.entity.AppriseExternalRoutingMode;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceDeliveryStatus;
import org.github.tess1o.geopulse.geofencing.service.AppriseNotificationService;
import org.github.tess1o.geopulse.notifications.model.NotificationPreferences;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;
import org.github.tess1o.geopulse.notifications.model.entity.UserNotificationEntity;
import org.github.tess1o.geopulse.notifications.repository.UserNotificationRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;
import java.util.Map;

@ApplicationScoped
@Slf4j
public class NotificationPublisherService {
    private final UserNotificationRepository notificationRepository;
    private final AppriseNotificationService apprise;

    public NotificationPublisherService(UserNotificationRepository notificationRepository, AppriseNotificationService apprise) {
        this.notificationRepository = notificationRepository;
        this.apprise = apprise;
    }

    @Transactional
    public UserNotificationEntity publish(UserEntity user, NotificationSource source, NotificationType type,
                                          String title, String message, Map<String, Object> metadata, String dedupeKey,
                                          NotificationPreferences.Channel channel) {
        return notificationRepository.findByDedupeKey(dedupeKey).orElseGet(() -> {
            GeofenceDeliveryStatus status = null;
            if (channel != null && channel.isAppriseEnabled()) {
                log.info("Sending external notification: user={}, source={}, type={}, routingMode={}",
                        user.getId(), source, type, channel.getRoutingMode());
                AppriseClientResult result = channel.getRoutingMode() == AppriseExternalRoutingMode.KEY_TAG
                        ? apprise.sendToConfigKey(channel.getAppriseConfigKey(), channel.getAppriseTag(), title, message)
                        : apprise.sendToDestination(channel.getDestination(), title, message);
                status = result.isSuccess() ? GeofenceDeliveryStatus.SENT : GeofenceDeliveryStatus.FAILED;
                if (result.isSuccess()) {
                    log.info("External notification sent: user={}, source={}, type={}, statusCode={}",
                            user.getId(), source, type, result.getStatusCode());
                } else {
                    log.warn("External notification failed: user={}, source={}, type={}, statusCode={}, reason={}",
                            user.getId(), source, type, result.getStatusCode(), result.getMessage());
                }
            }
            UserNotificationEntity notification = UserNotificationEntity.builder()
                    .ownerUser(user).source(source).type(type).title(title).message(message)
                    .occurredAt(Instant.now()).metadata(metadata).dedupeKey(dedupeKey)
                    .inAppEnabled(channel == null || channel.isInAppEnabled())
                    .deliveryStatus(status).build();
            notificationRepository.persist(notification);
            log.info("Notification recorded: user={}, source={}, type={}, inApp={}, externalStatus={}",
                    user.getId(), source, type, notification.isInAppEnabled(), status);
            return notification;
        });
    }
}

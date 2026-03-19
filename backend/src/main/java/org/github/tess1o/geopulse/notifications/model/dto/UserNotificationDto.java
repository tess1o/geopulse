package org.github.tess1o.geopulse.notifications.model.dto;

import lombok.Builder;
import lombok.Data;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceDeliveryStatus;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class UserNotificationDto {
    private Long id;
    private NotificationSource source;
    private NotificationType type;
    private String title;
    private String message;
    private Instant occurredAt;
    private Instant seenAt;
    private Boolean seen;
    private GeofenceDeliveryStatus deliveryStatus;
    private String objectRef;
    private Map<String, Object> metadata;
}

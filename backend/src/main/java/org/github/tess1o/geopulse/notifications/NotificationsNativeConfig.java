package org.github.tess1o.geopulse.notifications;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.notifications.model.dto.UnreadCountDto;
import org.github.tess1o.geopulse.notifications.model.dto.UserNotificationDto;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;
import org.github.tess1o.geopulse.notifications.model.entity.UserNotificationEntity;

@RegisterForReflection(targets = {
        UserNotificationEntity.class,
        NotificationSource.class,
        NotificationType.class,
        UserNotificationDto.class,
        UserNotificationDto.UserNotificationDtoBuilder.class,
        UnreadCountDto.class
})
public class NotificationsNativeConfig {
}

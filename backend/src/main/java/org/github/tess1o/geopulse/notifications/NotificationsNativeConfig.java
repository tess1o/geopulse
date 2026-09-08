package org.github.tess1o.geopulse.notifications;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.notifications.model.dto.UnreadCountDto;
import org.github.tess1o.geopulse.notifications.model.dto.UserNotificationDto;
import org.github.tess1o.geopulse.notifications.model.dto.UserNotificationPageDto;
import org.github.tess1o.geopulse.notifications.model.dto.NotificationPreferencesDto;
import org.github.tess1o.geopulse.notifications.model.dto.UpdateNotificationPreferencesRequest;
import org.github.tess1o.geopulse.notifications.model.dto.ReleaseAnnouncementResponse;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;
import org.github.tess1o.geopulse.notifications.model.entity.UserNotificationEntity;
import org.github.tess1o.geopulse.notifications.model.NotificationPreferences;
import org.github.tess1o.geopulse.notifications.model.entity.GpsHealthIncidentEntity;

@RegisterForReflection(targets = {
        UserNotificationEntity.class,
        NotificationSource.class,
        NotificationType.class,
        UserNotificationDto.class,
        UserNotificationDto.UserNotificationDtoBuilder.class,
        UserNotificationPageDto.class,
        UserNotificationPageDto.UserNotificationPageDtoBuilder.class,
        UnreadCountDto.class,
        NotificationPreferences.class,
        NotificationPreferences.Channel.class,
        GpsHealthIncidentEntity.class,
        NotificationPreferencesDto.class,
        UpdateNotificationPreferencesRequest.class,
        ReleaseAnnouncementResponse.class
})
public class NotificationsNativeConfig {
}

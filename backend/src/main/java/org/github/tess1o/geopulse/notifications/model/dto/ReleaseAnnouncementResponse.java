package org.github.tess1o.geopulse.notifications.model.dto;

import org.github.tess1o.geopulse.home.model.HomeContentResponse;

public record ReleaseAnnouncementResponse(
        boolean show,
        UserNotificationDto notification,
        HomeContentResponse.WhatsNewItem release
) {
}

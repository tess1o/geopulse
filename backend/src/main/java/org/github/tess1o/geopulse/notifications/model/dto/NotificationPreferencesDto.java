package org.github.tess1o.geopulse.notifications.model.dto;

import org.github.tess1o.geopulse.notifications.model.NotificationPreferences;

public record NotificationPreferencesDto(
        boolean gpsHealthEnabled,
        int gpsSilenceMinutes,
        NotificationPreferences.Channel gpsHealth,
        boolean rewindEnabled,
        NotificationPreferences.Channel rewind,
        boolean whatsNewEnabled
) {
}

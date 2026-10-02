package org.github.tess1o.geopulse.notifications.model.dto;

import lombok.Getter;
import lombok.Setter;
import org.github.tess1o.geopulse.notifications.model.NotificationPreferences;

@Getter
@Setter
public class UpdateNotificationPreferencesRequest {
    private boolean gpsHealthEnabled;
    private int gpsSilenceMinutes;
    private NotificationPreferences.Channel gpsHealth;
    private boolean rewindEnabled;
    private NotificationPreferences.Channel rewind;
    private boolean whatsNewEnabled;
}

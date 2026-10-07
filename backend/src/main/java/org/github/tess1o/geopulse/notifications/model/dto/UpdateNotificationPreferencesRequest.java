package org.github.tess1o.geopulse.notifications.model.dto;

import lombok.Getter;
import lombok.Setter;
import org.github.tess1o.geopulse.notifications.model.NotificationPreferences;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
public class UpdateNotificationPreferencesRequest {
    @Schema(examples = "true")
    private boolean gpsHealthEnabled;
    @Schema(examples = "120")
    private int gpsSilenceMinutes;
    private NotificationPreferences.Channel gpsHealth;
    @Schema(examples = "true")
    private boolean rewindEnabled;
    private NotificationPreferences.Channel rewind;
    @Schema(examples = "true")
    private boolean whatsNewEnabled;
}

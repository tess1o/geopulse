package org.github.tess1o.geopulse.notifications.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.github.tess1o.geopulse.geofencing.model.entity.AppriseExternalRoutingMode;

import java.time.Instant;
import java.io.Serializable;

/** Per-user controls for the small set of actionable notifications. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class NotificationPreferences implements Serializable {
    @Builder.Default
    private boolean gpsHealthEnabled = false;
    @Builder.Default
    private int gpsSilenceMinutes = 60;
    @Builder.Default
    private Channel gpsHealth = new Channel();
    @Builder.Default
    private boolean rewindEnabled = false;
    @Builder.Default
    private Channel rewind = new Channel();
    @Builder.Default
    private Boolean whatsNewEnabled = true;
    private Instant gpsMonitoringStartedAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Channel implements Serializable {
        @Builder.Default
        private boolean inAppEnabled = true;
        @Builder.Default
        private boolean appriseEnabled = false;
        @Builder.Default
        private AppriseExternalRoutingMode routingMode = AppriseExternalRoutingMode.URLS;
        private String destination;
        private String appriseConfigKey;
        private String appriseTag;
    }
}

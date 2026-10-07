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
import org.eclipse.microprofile.openapi.annotations.media.Schema;

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
        @Schema(examples = "true")
        private boolean inAppEnabled = true;
        @Builder.Default
        @Schema(examples = "false")
        private boolean appriseEnabled = false;
        @Builder.Default
        @Schema(examples = "URLS")
        private AppriseExternalRoutingMode routingMode = AppriseExternalRoutingMode.URLS;
        @Schema(examples = "tgram://bottoken/ChatID")
        private String destination;
        @Schema(examples = "geopulse")
        private String appriseConfigKey;
        @Schema(examples = "family")
        private String appriseTag;
    }
}

package org.github.tess1o.geopulse.gps.integrations.owntracks.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class StatusMessage {
    @JsonProperty("_type")
    private String type;

    @JsonProperty("iOS")
    private IosStatus iOS;

    private String topic;

    @Data
    public static class IosStatus {
        private String deviceSystemName;
        private boolean localeUsesMetricSystem;
        private String locationManagerAuthorizationStatus;
        private String version;
        private boolean altimeterIsRelativeAltitudeAvailable;
        private boolean pedometerIsDistanceAvailable;
        private String deviceIdentifierForVendor;
        private String deviceSystemVersion;
        private String locale;
        private boolean pedometerIsFloorCountingAvailable;
        private String altimeterAuthorizationStatus;
        private String deviceModel;
        private String deviceUserInterfaceIdiom;
        private boolean pedometerIsStepCountingAvailable;
        private String backgroundRefreshStatus;
    }
}
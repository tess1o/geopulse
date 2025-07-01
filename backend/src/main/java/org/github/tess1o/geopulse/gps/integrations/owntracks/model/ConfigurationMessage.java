package org.github.tess1o.geopulse.gps.integrations.owntracks.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ConfigurationMessage {
    @JsonProperty("_type")
    private String type;
    private Configuration configuration;
    private String topic;

    @Data
    public static class Configuration {
        private String username;
        private int maxHistory;
        private boolean locked;

        @JsonProperty("_type")
        private String type;

        private int monitoring;
        private String deviceId;
        private int positions;
        private boolean ranging;
        private boolean cmd;
        private String encryptionKey;
        private boolean allowRemoteLocation;
        private String pubTopicBase;
        private String tid;
        private String url;
        private int ignoreStaleLocations;
        private String osmCopyright;
        private List<Object> waypoints;  // Can be refined to specific type if waypoint structure is known
        private boolean usePassword;
        private String httpHeaders;
        private boolean auth;
        private int locatorInterval;
        private boolean extendedData;
        private String osmTemplate;
        private int ignoreInaccurateLocations;
        private int locatorDisplacement;
        private int mode;
        private String password;
        private int downgrade;
    }

}
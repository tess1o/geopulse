package org.github.tess1o.geopulse.gps.integrations.owntracks.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OwnTracksLocationMessage {
    private String cog;
    private Double batt;
    private Double lat;
    private Double lon;
    private Double acc;
    private String bs;
    private String[] inrids;
    private String p;
    private Double vel;
    private String vac;
    private String[] inregions;
    private String topic;
    private String t;
    private String conn;
    private String m;
    private Long tst;
    private Double alt;
    @JsonProperty("_type")
    private String type;
    private String tid;
    private String bssid;
    private String ssid;
    @JsonProperty("created_at")
    private Long createdAt;
}
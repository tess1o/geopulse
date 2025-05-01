package org.github.tess1o.geopulse.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationMessage {
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
    private Integer tst;
    private Double alt;
    private String _type;
    private String tid;
    private String bssid;
    private String ssid;
    @JsonProperty("created_at")
    private Integer createdAt;
}

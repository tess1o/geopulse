package org.github.tess1o.geopulse.gps.integrations.colota.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ColotaLocationMessage {
    private Double lat;
    private Double lon;
    private Double acc;
    private Long tst;
    private Double alt;
    private Double vel;
    private Double batt;
    private Integer bs;
    private Double bear;
}

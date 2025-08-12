package org.github.tess1o.geopulse.gps.integrations.dawarich.model.point;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DawarichLocation {
    @JsonProperty("type")
    private String type; // "Feature"

    @JsonProperty("geometry")
    private DawarichGeometry geometry;

    @JsonProperty("properties")
    private DawarichProperties properties;
}

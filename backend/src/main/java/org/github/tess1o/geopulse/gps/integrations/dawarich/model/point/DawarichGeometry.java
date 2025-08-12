package org.github.tess1o.geopulse.gps.integrations.dawarich.model.point;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DawarichGeometry {
    @JsonProperty("type")
    private String type; // "Point"

    @JsonProperty("coordinates")
    private List<Double> coordinates; // [longitude, latitude]

    public Double getLongitude() {
        return coordinates != null && coordinates.size() > 0 ? coordinates.get(0) : null;
    }

    public Double getLatitude() {
        return coordinates != null && coordinates.size() > 1 ? coordinates.get(1) : null;
    }

}

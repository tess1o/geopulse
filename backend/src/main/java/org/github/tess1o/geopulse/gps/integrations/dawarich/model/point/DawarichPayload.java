package org.github.tess1o.geopulse.gps.integrations.dawarich.model.point;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DawarichPayload {
    @JsonProperty("locations")
    private List<DawarichLocation> locations;

}
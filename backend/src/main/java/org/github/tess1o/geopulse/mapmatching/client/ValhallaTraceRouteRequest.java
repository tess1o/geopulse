package org.github.tess1o.geopulse.mapmatching.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValhallaTraceRouteRequest {
    private List<ValhallaLocation> shape;
    private String costing;
    @JsonProperty("shape_match")
    private String shapeMatch;
    private String format;
    @JsonProperty("directions_type")
    private String directionsType;
    @JsonProperty("begin_time")
    private Long beginTime;
    @JsonProperty("use_timestamps")
    private Boolean useTimestamps;
}

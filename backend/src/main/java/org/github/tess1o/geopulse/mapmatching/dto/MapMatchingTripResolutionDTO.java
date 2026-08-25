package org.github.tess1o.geopulse.mapmatching.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.time.Instant;

@Data
@Builder
public class MapMatchingTripResolutionDTO {
    private Long tripId;
    private String status;
    private Long targetId;
    private String error;
    private String source;
    private Instant retryAt;
    private Integer pollAfterMs;
    private List<List<MapMatchedPointDTO>> segments;
}

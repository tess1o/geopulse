package org.github.tess1o.geopulse.mapmatching.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MapMatchingResolutionResponse {
    private boolean enabled;
    private String provider;
    private List<MapMatchingTripResolutionDTO> trips;
}

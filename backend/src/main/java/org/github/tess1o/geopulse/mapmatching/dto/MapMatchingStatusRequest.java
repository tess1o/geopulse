package org.github.tess1o.geopulse.mapmatching.dto;

import lombok.Data;

import java.util.List;

@Data
public class MapMatchingStatusRequest {
    private List<Long> targetIds;
}

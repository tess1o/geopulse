package org.github.tess1o.geopulse.mapmatching.service;

import org.github.tess1o.geopulse.mapmatching.dto.MapMatchingPointDTO;
import org.github.tess1o.geopulse.mapmatching.dto.MapMatchedPointDTO;

import java.util.List;

public interface MapMatchingProvider {
    String providerName();

    List<MapMatchedPointDTO> match(List<MapMatchingPointDTO> points, String profile);

    default List<List<MapMatchedPointDTO>> matchSegments(List<MapMatchingPointDTO> points, String profile) {
        List<MapMatchedPointDTO> matched = match(points, profile);
        return matched == null || matched.isEmpty() ? List.of() : List.of(matched);
    }
}

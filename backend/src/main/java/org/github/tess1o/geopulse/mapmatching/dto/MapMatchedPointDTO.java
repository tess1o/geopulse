package org.github.tess1o.geopulse.mapmatching.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MapMatchedPointDTO {
    private double longitude;
    private double latitude;
}

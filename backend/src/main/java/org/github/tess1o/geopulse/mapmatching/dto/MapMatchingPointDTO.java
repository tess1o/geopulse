package org.github.tess1o.geopulse.mapmatching.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/** Only the inputs needed for route matching and its cache identity. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MapMatchingPointDTO {
    private Instant timestamp;
    private Double latitude;
    private Double longitude;
    private Double accuracy;
}

package org.github.tess1o.geopulse.gps.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawGpsPointLocationDTO {
    private String locationName;
    private String sourceType;
    private Long favoriteId;
    private Long geocodingId;
    private Double anchorLatitude;
    private Double anchorLongitude;
}

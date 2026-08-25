package org.github.tess1o.geopulse.mapmatching.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValhallaLocation {
    private double lat;
    private double lon;
    private Long time;
    private Double accuracy;
    private String type;
}

package org.github.tess1o.geopulse.gps.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GpsPointsRetentionRequest {
    List<GpsPointDTO> points;
}

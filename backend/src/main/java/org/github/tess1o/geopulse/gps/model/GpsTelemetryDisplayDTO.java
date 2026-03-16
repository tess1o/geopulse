package org.github.tess1o.geopulse.gps.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GpsTelemetryDisplayDTO {
    private String key;
    private String label;
    private String value;
    private String unit;
    private String type;
}

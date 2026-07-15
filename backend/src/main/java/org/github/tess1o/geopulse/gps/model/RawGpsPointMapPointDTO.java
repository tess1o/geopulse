package org.github.tess1o.geopulse.gps.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawGpsPointMapPointDTO {
    private Long id;
    private Instant timestamp;
    private Double latitude;
    private Double longitude;
    private Double accuracy;
    private Double battery;
    private Double velocity;
    private Double altitude;
    private String sourceType;
}

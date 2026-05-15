package org.github.tess1o.geopulse.gps.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileAppGpsPointRequest {

    @NotNull(message = "lat is required")
    @DecimalMin(value = "-90.0", message = "lat must be >= -90")
    @DecimalMax(value = "90.0", message = "lat must be <= 90")
    private Double lat;

    @NotNull(message = "lon is required")
    @DecimalMin(value = "-180.0", message = "lon must be >= -180")
    @DecimalMax(value = "180.0", message = "lon must be <= 180")
    private Double lon;

    @NotNull(message = "timestamp is required")
    private Instant timestamp;

    private Double accuracy;
    private Double altitude;
    private Double speed;
    private Double battery;
}

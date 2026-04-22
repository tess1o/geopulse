package org.github.tess1o.geopulse.trips.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripReconstructionWaypointDto {

    @NotNull(message = "Waypoint latitude is required")
    @DecimalMin(value = "-90.0", message = "Waypoint latitude must be >= -90")
    @DecimalMax(value = "90.0", message = "Waypoint latitude must be <= 90")
    private Double latitude;

    @NotNull(message = "Waypoint longitude is required")
    @DecimalMin(value = "-180.0", message = "Waypoint longitude must be >= -180")
    @DecimalMax(value = "180.0", message = "Waypoint longitude must be <= 180")
    private Double longitude;
}

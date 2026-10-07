package org.github.tess1o.geopulse.trips.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripReconstructionSegmentDto {

    @NotBlank(message = "segmentType is required")
    @Schema(examples = "TRIP")
    private String segmentType;

    @NotNull(message = "startTime is required")
    @Schema(examples = "2025-06-01T09:00:00Z")
    private Instant startTime;

    @NotNull(message = "endTime is required")
    @Schema(examples = "2025-06-01T11:30:00Z")
    private Instant endTime;

    @Size(max = 255, message = "locationName cannot exceed 255 characters")
    @Schema(examples = "Train to Lviv")
    private String locationName;

    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90")
    @Schema(examples = "49.8397")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180")
    @Schema(examples = "24.0297")
    private Double longitude;

    @Schema(examples = "TRAIN")
    private String movementType;

    private List<@Valid TripReconstructionWaypointDto> waypoints;
}

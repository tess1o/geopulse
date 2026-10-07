package org.github.tess1o.geopulse.streaming.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class TripStaySplitRequest {
    @NotNull
    @Schema(examples = "2025-06-01T12:10:00Z")
    private Instant stayStartTime;

    @NotNull
    @Schema(examples = "2025-06-01T12:40:00Z")
    private Instant stayEndTime;

    @NotNull
    @Schema(examples = "2025-06-01T12:20:00Z")
    private Instant anchorTimestamp;

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    @Schema(examples = "50.4501")
    private Double latitude;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    @Schema(examples = "30.5234")
    private Double longitude;

    @Size(max = 500)
    @Schema(examples = "Gas station")
    private String locationName;
}

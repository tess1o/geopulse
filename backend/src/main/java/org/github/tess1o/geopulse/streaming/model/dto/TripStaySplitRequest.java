package org.github.tess1o.geopulse.streaming.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class TripStaySplitRequest {
    @NotNull
    private Instant stayStartTime;

    @NotNull
    private Instant stayEndTime;

    @NotNull
    private Instant anchorTimestamp;

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitude;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitude;

    @Size(max = 500)
    private String locationName;
}

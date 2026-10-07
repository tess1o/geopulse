package org.github.tess1o.geopulse.weather.dto;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class WeatherBackfillRequest {
    @Schema(examples = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID userId;
    @Schema(examples = "2025-01-01T00:00:00Z")
    private Instant startTime;
    @Schema(examples = "2025-06-30T23:59:59Z")
    private Instant endTime;
}

package org.github.tess1o.geopulse.weather.dto;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class WeatherBackfillRequest {
    private UUID userId;
    private Instant startTime;
    private Instant endTime;
}

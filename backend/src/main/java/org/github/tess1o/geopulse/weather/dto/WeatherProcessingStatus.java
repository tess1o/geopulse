package org.github.tess1o.geopulse.weather.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherProcessingStatus {
    private boolean running;
    private int waitingWorkers;
    private String phase;
    private String trigger;
    private String reason;
    private UUID userId;
    private Instant rangeStart;
    private Instant rangeEnd;
    private Instant startedAt;
}

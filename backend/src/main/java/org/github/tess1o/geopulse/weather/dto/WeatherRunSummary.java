package org.github.tess1o.geopulse.weather.dto;

import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherRunSummary {
    private String trigger;
    private String reason;
    private String source;
    private String result;
    private String message;
    private Instant startedAt;
    private Instant completedAt;
    private long durationMs;
    private int chunksProcessed;
    private int targetsCreated;
    private int targetsAlreadyKnown;
    private int targetsSkipped;
    private int fetchedTargets;
    private long pendingUserRanges;
}

package org.github.tess1o.geopulse.weather.dto;

import lombok.*;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherStatusResponse {
    private boolean enabled;
    private boolean configured;
    private String provider;
    private int dailyRequestLimit;
    private int ongoingReserve;
    private long requestsUsedToday;
    private long requestsRemainingToday;
    private long samples;
    private Map<String, Long> targetsByStatus;
    private Instant oldestPendingTargetAt;
    private Instant newestPendingTargetAt;
}

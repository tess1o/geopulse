package org.github.tess1o.geopulse.weather.dto;

import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherReconciliationStatus {
    private long pendingUserRanges;
    private long eligibleUserRanges;
    private Instant oldestRangeStart;
    private Instant oldestCursorAt;
    private Instant newestRangeEnd;
    private Instant eligibleThrough;
}

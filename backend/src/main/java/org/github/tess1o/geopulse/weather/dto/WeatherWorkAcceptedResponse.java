package org.github.tess1o.geopulse.weather.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherWorkAcceptedResponse {
    private boolean accepted;
    private boolean alreadyRunning;
    private int queuedUserRanges;
    private String message;
}

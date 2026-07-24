package org.github.tess1o.geopulse.weather.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherTargetQueueResponse {
    private int targetsCreated;
    private int targetsAlreadyKnown;
    private int targetsSkipped;
}

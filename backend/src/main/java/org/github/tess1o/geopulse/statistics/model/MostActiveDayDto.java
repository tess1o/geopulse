package org.github.tess1o.geopulse.statistics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MostActiveDayDto {
    private String date;
    private String day;
    private double distanceTraveled;
    private double travelTime;
    private long locationsVisited;
}

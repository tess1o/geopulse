package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO containing lightweight visit pattern facts for a place.
 * Only populated when enough visits exist for the pattern to be meaningful.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceVisitPatternsDTO {
    private String mostCommonDayOfWeek;
    private long mostCommonDayVisitCount;
    private String mostCommonArrivalPeriod;
    private long mostCommonArrivalPeriodVisitCount;
    private Double averageDaysBetweenVisits;
    private int minimumVisitsRequired;
}

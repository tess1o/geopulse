package org.github.tess1o.geopulse.digest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeriodComparison {
    private double totalDistance;
    private double percentChange;
    private String direction; // "increase", "decrease", "same"
    private int activeDays;
    private int activeDaysChange;
}

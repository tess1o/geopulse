package org.github.tess1o.geopulse.digest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DigestMetrics {
    private double totalDistance; // in meters
    private int activeDays;
    private int citiesVisited;
    private int tripCount;
    private int stayCount;
}

package org.github.tess1o.geopulse.statistics.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BarChartData {
    private String[] labels;
    private double[] data;
}

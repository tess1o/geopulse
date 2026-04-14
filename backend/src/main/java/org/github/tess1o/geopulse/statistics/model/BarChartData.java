package org.github.tess1o.geopulse.statistics.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class BarChartData {
    private String[] labels;
    private double[] data;
    private String[] sortKeys;

    public BarChartData(String[] labels, double[] data) {
        this.labels = labels;
        this.data = data;
        this.sortKeys = null;
    }

    public BarChartData(String[] labels, double[] data, String[] sortKeys) {
        this.labels = labels;
        this.data = data;
        this.sortKeys = sortKeys;
    }
}

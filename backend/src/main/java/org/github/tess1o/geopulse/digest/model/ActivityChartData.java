package org.github.tess1o.geopulse.digest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.statistics.model.BarChartData;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityChartData {
    private BarChartData carChart;
    private BarChartData walkChart;
}

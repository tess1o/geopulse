package org.github.tess1o.geopulse.statistics;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.statistics.model.*;

@RegisterForReflection(targets = {
        ChartGroupMode.class,
        UserStatistics.class,
        MostActiveDayDto.class,
        TopPlace.class,
        RoutesStatistics.class,
        BarChartData.class,
        MostCommonRoute.class,
        HeatmapPlace.class,
})
public class StatisticsNativeConfig {
}

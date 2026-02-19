package org.github.tess1o.geopulse.digest;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.digest.model.*;

@RegisterForReflection(targets = {
        TimeDigest.class,
        ActivityChartData.class,
        DigestHighlight.class,
        DigestMetrics.class,
        HeatmapDataPoint.class,
        HeatmapLayer.class,
        Milestone.class,
        PeriodComparison.class,
        PeriodInfo.class,
        TimeDigest.class,
})
public class DigestNativeConfig {
}

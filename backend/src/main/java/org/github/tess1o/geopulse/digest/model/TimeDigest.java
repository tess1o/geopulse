package org.github.tess1o.geopulse.digest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.statistics.model.TopPlace;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeDigest {
    private PeriodInfo period;
    private DigestMetrics metrics;
    private PeriodComparison comparison;
    private DigestHighlight highlights;
    private List<TopPlace> topPlaces;
    private ActivityChartData activityChart; // car and walk distance charts
    private List<Milestone> milestones;
}

package org.github.tess1o.geopulse.ai.model;

import org.github.tess1o.geopulse.digest.model.DigestHighlight;
import org.github.tess1o.geopulse.digest.model.DigestMetrics;
import org.github.tess1o.geopulse.digest.model.Milestone;
import org.github.tess1o.geopulse.digest.model.PeriodComparison;
import org.github.tess1o.geopulse.digest.model.PeriodInfo;
import org.github.tess1o.geopulse.digest.model.TimeDigest;
import org.github.tess1o.geopulse.statistics.model.TopPlace;

import java.util.List;

/** A monthly or yearly digest without its chart data. */
public record AIPeriodDigestDTO(
        PeriodInfo period,
        DigestMetrics metrics,
        PeriodComparison comparison,
        DigestHighlight highlights,
        List<TopPlace> topPlaces,
        List<Milestone> milestones
) {

    public static AIPeriodDigestDTO from(TimeDigest digest) {
        return new AIPeriodDigestDTO(digest.getPeriod(), digest.getMetrics(), digest.getComparison(),
                digest.getHighlights(), digest.getTopPlaces(), digest.getMilestones());
    }
}

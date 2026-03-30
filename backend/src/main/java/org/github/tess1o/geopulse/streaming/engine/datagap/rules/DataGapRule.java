package org.github.tess1o.geopulse.streaming.engine.datagap.rules;

import org.github.tess1o.geopulse.streaming.engine.datagap.model.DataGapContext;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;

import java.util.List;

public interface DataGapRule {
    int ORDER_STAY_INFERENCE = 100;
    int ORDER_SPARSE_STAY = 200;
    int ORDER_TRIP_INFERENCE = 300;
    int ORDER_DEFAULT_GAP = 900;

    int order();

    boolean apply(DataGapContext context, List<TimelineEvent> gapEvents);
}

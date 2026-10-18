package org.github.tess1o.geopulse.streaming.engine.datagap.rules;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.engine.TimelineEventFinalizationService;
import org.github.tess1o.geopulse.streaming.engine.datagap.model.DataGapContext;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineExplanation;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReason;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReasons;
import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;

import java.util.List;

@Slf4j
@ApplicationScoped
public class DefaultDataGapRule implements DataGapRule {
    private final TimelineEventFinalizationService finalizationService;

    @Inject
    public DefaultDataGapRule(TimelineEventFinalizationService finalizationService) {
        this.finalizationService = finalizationService;
    }

    @Override
    public int order() {
        return ORDER_DEFAULT_GAP;
    }

    @Override
    public boolean apply(DataGapContext context, List<TimelineEvent> gapEvents) {
        TimelineEvent activeEvent = GapRuleSupport.finalizeActiveEvent(
                finalizationService,
                context.userState(),
                context.lastPoint(),
                context.config(),
                context.gapDuration()
        );
        if (activeEvent != null) {
            gapEvents.add(activeEvent);
            log.debug("Finalized active event before gap: {} from {} to {}",
                    activeEvent.getType(), activeEvent.getStartTime(), activeEvent.getEndTime());
        }

        DataGap gap = DataGap.fromTimeRange(
                context.lastPoint().getTimestamp(),
                context.currentPoint().getTimestamp()
        );
        Integer thresholdSeconds = context.config().getDataGapThresholdSeconds();
        gap.setExplanation(new TimelineExplanation(TimelineReason.of(TimelineReasons.NO_POINTS,
                "gapSeconds", context.gapDuration().getSeconds(),
                "thresholdSeconds", thresholdSeconds),
                null, null));
        gapEvents.add(gap);
        log.debug("Created data gap event: {} duration", gap.getDuration());

        context.userState().reset();
        log.debug("User state reset due to data gap");
        return true;
    }
}

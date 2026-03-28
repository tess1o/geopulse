package org.github.tess1o.geopulse.streaming.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;

import java.util.List;

@Slf4j
@ApplicationScoped
class DefaultDataGapRule implements DataGapRule {
    private final TimelineEventFinalizationService finalizationService;

    @Inject
    DefaultDataGapRule(TimelineEventFinalizationService finalizationService) {
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
                context.config()
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
        gapEvents.add(gap);
        log.debug("Created data gap event: {} duration", gap.getDuration());

        context.userState().reset();
        log.debug("User state reset due to data gap");
        return true;
    }
}

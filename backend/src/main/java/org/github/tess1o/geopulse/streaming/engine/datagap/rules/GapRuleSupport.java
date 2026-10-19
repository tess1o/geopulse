package org.github.tess1o.geopulse.streaming.engine.datagap.rules;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.engine.TimelineEventFinalizationService;
import org.github.tess1o.geopulse.streaming.engine.datagap.model.DataGapContext;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineExplanation;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReason;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReasons;
import org.github.tess1o.geopulse.streaming.model.domain.UserState;

import java.time.Duration;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GapRuleSupport {

    static TimelineEvent finalizeActiveEvent(TimelineEventFinalizationService finalizationService,
                                             UserState userState,
                                             GPSPoint lastPoint,
                                             TimelineConfig config,
                                             Duration gapDuration) {
        if (!userState.hasActivePoints()) {
            return null;
        }

        TimelineEvent event;
        switch (userState.getCurrentMode()) {
            case POTENTIAL_STAY:
            case CONFIRMED_STAY:
                event = finalizationService.finalizeStayWithoutLocation(userState, config);
                break;

            case IN_TRIP:
                event = finalizationService.finalizeTripForGap(userState, lastPoint, config);
                break;

            case UNKNOWN:
            default:
                log.debug("No active event to finalize in mode: {}", userState.getCurrentMode());
                return null;
        }
        TimelineExplanation explanation = userState.finishExplanation(dataGapEnd(gapDuration));
        if (event != null) {
            event.setExplanation(explanation);
        }
        return event;
    }

    static TimelineReason dataGapEnd(Duration gapDuration) {
        return TimelineReason.of(TimelineReasons.DATA_GAP, "gapSeconds", gapDuration.getSeconds());
    }

    /**
     * Explains an event the rule inferred from the gap.
     */
    static void explainInferred(TimelineEvent event, UserState userState, String code, DataGapContext context) {
        event.setExplanation(new TimelineExplanation(TimelineReason.of(code,
                "gapSeconds", context.gapDuration().getSeconds(),
                "distanceMeters", Math.round(context.lastPoint().distanceTo(context.currentPoint()))),
                null, null));
    }
}

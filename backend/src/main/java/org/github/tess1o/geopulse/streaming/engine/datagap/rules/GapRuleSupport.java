package org.github.tess1o.geopulse.streaming.engine.datagap.rules;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.engine.TimelineEventFinalizationService;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.UserState;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GapRuleSupport {

    static TimelineEvent finalizeActiveEvent(TimelineEventFinalizationService finalizationService,
                                             UserState userState,
                                             GPSPoint lastPoint,
                                             TimelineConfig config) {
        if (!userState.hasActivePoints()) {
            return null;
        }

        switch (userState.getCurrentMode()) {
            case POTENTIAL_STAY:
            case CONFIRMED_STAY:
                return finalizationService.finalizeStayWithoutLocation(userState, config);

            case IN_TRIP:
                return finalizationService.finalizeTripForGap(userState, lastPoint, config);

            case UNKNOWN:
            default:
                log.debug("No active event to finalize in mode: {}", userState.getCurrentMode());
                return null;
        }
    }
}

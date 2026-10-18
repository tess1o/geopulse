package org.github.tess1o.geopulse.streaming.engine.datagap.rules;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.engine.GapStayInferenceService;
import org.github.tess1o.geopulse.streaming.engine.TimelineEventFinalizationService;
import org.github.tess1o.geopulse.streaming.engine.datagap.model.DataGapContext;
import org.github.tess1o.geopulse.streaming.engine.datagap.model.GapStayInferencePlan;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineExplanation;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReason;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReasons;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.ProcessorMode;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.domain.UserState;

import java.util.List;

@Slf4j
@ApplicationScoped
public class GapStayInferenceRule implements DataGapRule {
    private final GapStayInferenceService gapStayInferenceService;
    private final TimelineEventFinalizationService finalizationService;

    @Inject
    public GapStayInferenceRule(GapStayInferenceService gapStayInferenceService,
                         TimelineEventFinalizationService finalizationService) {
        this.gapStayInferenceService = gapStayInferenceService;
        this.finalizationService = finalizationService;
    }

    @Override
    public int order() {
        return ORDER_STAY_INFERENCE;
    }

    @Override
    public boolean apply(DataGapContext context, List<TimelineEvent> gapEvents) {
        GapStayInferencePlan stayInferencePlan = gapStayInferenceService.tryInfer(
                context.currentPoint(),
                context.userState(),
                context.config(),
                context.gapDuration()
        );
        boolean shouldInferStay = stayInferencePlan.isInferred();
        log.info("Gap stay inference check: shouldInfer={}, enabled={}",
                shouldInferStay, context.config().getGapStayInferenceEnabled());
        if (!shouldInferStay) {
            return false;
        }

        log.info("Gap stay inference applied - skipping gap creation, points are at same location");
        applyStayInferencePlan(stayInferencePlan, context.userState(), context.config(), gapEvents);
        context.userState().getActiveExplanation().note(TimelineReason.of(TimelineReasons.CONTINUED_OVER_GAP,
                "gapSeconds", context.gapDuration().getSeconds()));
        // Don't create gap, don't reset state - let state machine process the point normally.
        return true;
    }

    private void applyStayInferencePlan(GapStayInferencePlan plan, UserState userState,
                                        TimelineConfig config, List<TimelineEvent> gapEvents) {
        TimelineReason stoppedBeforeGap = TimelineReason.of(TimelineReasons.STOPPED_BEFORE_GAP);
        if (plan.hasTripToFinalize()) {
            Trip finalizedTrip = finalizeTripFromPoints(plan.getTripPointsToFinalize(), config);
            TimelineExplanation tripExplanation = userState.finishExplanation(stoppedBeforeGap);
            if (finalizedTrip != null) {
                finalizedTrip.setExplanation(tripExplanation);
                gapEvents.add(finalizedTrip);
            }
        }

        if (plan.hasReplacementStayPoints()) {
            userState.setCurrentMode(ProcessorMode.CONFIRMED_STAY);
            userState.clearActivePoints();
            for (GPSPoint point : plan.getReplacementStayPoints()) {
                userState.addActivePoint(point);
            }
            if (plan.hasTripToFinalize()) {
                userState.startExplanation(stoppedBeforeGap);
            }
        }
    }

    private Trip finalizeTripFromPoints(List<GPSPoint> tripPoints, TimelineConfig config) {
        if (tripPoints == null || tripPoints.size() < 2) {
            return null;
        }

        UserState tripState = new UserState();
        tripState.setCurrentMode(ProcessorMode.IN_TRIP);
        for (GPSPoint point : tripPoints) {
            tripState.addActivePoint(point);
        }
        return finalizationService.finalizeTrip(tripState, config);
    }
}

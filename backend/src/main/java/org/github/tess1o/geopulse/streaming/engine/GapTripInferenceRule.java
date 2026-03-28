package org.github.tess1o.geopulse.streaming.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.service.trips.TravelClassification;
import org.github.tess1o.geopulse.streaming.service.trips.TripGpsStatistics;

import java.time.Duration;
import java.util.List;

@Slf4j
@ApplicationScoped
class GapTripInferenceRule implements DataGapRule {
    private final TimelineEventFinalizationService finalizationService;
    private final TravelClassification travelClassification;

    @Inject
    GapTripInferenceRule(TimelineEventFinalizationService finalizationService,
                         TravelClassification travelClassification) {
        this.finalizationService = finalizationService;
        this.travelClassification = travelClassification;
    }

    @Override
    public int order() {
        return ORDER_TRIP_INFERENCE;
    }

    @Override
    public boolean apply(DataGapContext context, List<TimelineEvent> gapEvents) {
        boolean shouldInferTrip = shouldInferTripDuringGap(context);
        log.info("Gap trip inference check: shouldInfer={}, enabled={}",
                shouldInferTrip, context.config().getGapTripInferenceEnabled());
        if (!shouldInferTrip) {
            return false;
        }

        log.info("Gap trip inference applied - creating inferred trip for gap");

        TimelineEvent activeEvent = GapRuleSupport.finalizeActiveEvent(
                finalizationService,
                context.userState(),
                context.lastPoint(),
                context.config()
        );
        if (activeEvent != null) {
            gapEvents.add(activeEvent);
            log.debug("Finalized active event before inferred trip: {} from {} to {}",
                    activeEvent.getType(), activeEvent.getStartTime(), activeEvent.getEndTime());
        }

        Trip inferredTrip = createInferredTrip(context);
        gapEvents.add(inferredTrip);
        log.debug("Created inferred trip: {} distance, {} duration, type {}",
                inferredTrip.getDistanceMeters(), inferredTrip.getDuration(), inferredTrip.getTripType());

        context.userState().reset();
        log.debug("User state reset after inferred trip");
        return true;
    }

    private boolean shouldInferTripDuringGap(DataGapContext context) {
        Boolean enabled = context.config().getGapTripInferenceEnabled();
        if (enabled == null || !enabled) {
            log.debug("Gap trip inference is disabled (enabled={})", enabled);
            return false;
        }

        Integer minGapHours = context.config().getGapTripInferenceMinGapHours();
        if (minGapHours != null && minGapHours > 0) {
            long gapHours = context.gapDuration().toHours();
            if (gapHours < minGapHours) {
                log.debug("Gap duration {}h is below minimum {}h for trip inference",
                        gapHours, minGapHours);
                return false;
            }
        }

        Integer maxGapHours = context.config().getGapTripInferenceMaxGapHours();
        if (maxGapHours != null && maxGapHours > 0) {
            long gapHours = context.gapDuration().toHours();
            if (gapHours > maxGapHours) {
                log.debug("Gap duration {}h exceeds maximum {}h for trip inference",
                        gapHours, maxGapHours);
                return false;
            }
        }

        double distance = context.lastPoint().distanceTo(context.currentPoint());
        Integer minDistanceMeters = context.config().getGapTripInferenceMinDistanceMeters();
        if (minDistanceMeters == null) {
            minDistanceMeters = 100000; // default 100km
        }

        if (distance < minDistanceMeters) {
            log.debug("Distance {}m is below minimum {}m for trip inference",
                    String.format("%.1f", distance), minDistanceMeters);
            return false;
        }

        log.info("Gap trip inference conditions met: mode={}, gap={}h, distance={}m (min={}m)",
                context.userState().getCurrentMode(),
                context.gapDuration().toHours(),
                String.format("%.1f", distance),
                minDistanceMeters);
        return true;
    }

    private Trip createInferredTrip(DataGapContext context) {
        Duration tripDuration = Duration.between(
                context.lastPoint().getTimestamp(),
                context.currentPoint().getTimestamp()
        );
        double distanceMeters = context.lastPoint().distanceTo(context.currentPoint());
        TripGpsStatistics gpsStatistics = TripGpsStatistics.empty();

        TripType tripType = travelClassification.classifyTravelType(
                gpsStatistics,
                tripDuration,
                Double.valueOf(distanceMeters).longValue(),
                context.config()
        );

        log.debug("Inferred trip classification: type={}, distance={}m, duration={}h, avgSpeed={}km/h",
                tripType,
                String.format("%.0f", distanceMeters),
                String.format("%.2f", tripDuration.toHours() + (tripDuration.toMinutesPart() / 60.0)),
                String.format("%.1f", (distanceMeters / 1000.0) / (tripDuration.getSeconds() / 3600.0)));

        return Trip.builder()
                .startTime(context.lastPoint().getTimestamp())
                .duration(tripDuration)
                .statistics(gpsStatistics)
                .startPoint(context.lastPoint())
                .endPoint(context.currentPoint())
                .distanceMeters(distanceMeters)
                .tripType(tripType)
                .build();
    }
}

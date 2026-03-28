package org.github.tess1o.geopulse.streaming.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.ProcessorMode;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.domain.UserState;

import java.time.Duration;
import java.util.List;

@Slf4j
@ApplicationScoped
class SparseInTripStayGapRule implements DataGapRule {
    private static final Duration MIN_GAP_DURATION_FLOOR = Duration.ofHours(3);
    private static final double MIN_BOUNDARY_DISTANCE_METERS = 150.0;
    private static final double MAX_BOUNDARY_DISTANCE_METERS = 800.0;
    private static final double MAX_IMPLIED_SPEED_KMH = 1.0;

    private final TimelineEventFinalizationService finalizationService;

    @Inject
    SparseInTripStayGapRule(TimelineEventFinalizationService finalizationService) {
        this.finalizationService = finalizationService;
    }

    @Override
    public int order() {
        return ORDER_SPARSE_STAY;
    }

    @Override
    public boolean apply(DataGapContext context, List<TimelineEvent> gapEvents) {
        if (!shouldInferSparseInTripStay(context)) {
            return false;
        }

        log.info("Sparse IN_TRIP gap stay inference applied - creating inferred stay instead of data gap");

        Trip finalizedTrip = finalizeTripBeforeGap(context.userState(), context.config());
        if (finalizedTrip != null) {
            gapEvents.add(finalizedTrip);
            log.debug("Finalized trip before sparse stay inference: {} to {}",
                    finalizedTrip.getStartTime(), finalizedTrip.getEndTime());
        }

        Stay inferredStay = createInferredStayFromGap(context);
        gapEvents.add(inferredStay);
        log.debug("Created inferred stay from sparse gap: {} duration", inferredStay.getDuration());

        // Gap still breaks streaming continuity - restart state machine from current point.
        context.userState().reset();
        log.debug("User state reset after sparse IN_TRIP stay inference");
        return true;
    }

    private boolean shouldInferSparseInTripStay(DataGapContext context) {
        if (!Boolean.TRUE.equals(context.config().getGapStayInferenceEnabled())) {
            return false;
        }
        if (context.userState().getCurrentMode() != ProcessorMode.IN_TRIP) {
            return false;
        }
        if (context.userState().copyActivePoints().size() < 2) {
            log.debug("Sparse IN_TRIP stay inference skipped - fewer than 2 active trip points");
            return false;
        }

        Duration effectiveMinGapDuration = resolveEffectiveMinGapDuration(context.config());
        if (context.gapDuration().compareTo(effectiveMinGapDuration) < 0) {
            log.debug("Sparse IN_TRIP stay inference skipped - gap {} below effective minimum {}",
                    context.gapDuration(), effectiveMinGapDuration);
            return false;
        }

        double boundaryDistanceMeters = context.lastPoint().distanceTo(context.currentPoint());
        if (boundaryDistanceMeters < MIN_BOUNDARY_DISTANCE_METERS) {
            log.debug("Sparse IN_TRIP stay inference skipped - distance {}m below minimum {}m",
                    String.format("%.1f", boundaryDistanceMeters), String.format("%.1f", MIN_BOUNDARY_DISTANCE_METERS));
            return false;
        }
        if (boundaryDistanceMeters > MAX_BOUNDARY_DISTANCE_METERS) {
            log.debug("Sparse IN_TRIP stay inference skipped - distance {}m exceeds maximum {}m",
                    String.format("%.1f", boundaryDistanceMeters), String.format("%.1f", MAX_BOUNDARY_DISTANCE_METERS));
            return false;
        }

        double gapHours = context.gapDuration().getSeconds() / 3600.0;
        if (gapHours <= 0.0) {
            return false;
        }

        double impliedSpeedKmh = (boundaryDistanceMeters / 1000.0) / gapHours;
        if (impliedSpeedKmh > MAX_IMPLIED_SPEED_KMH) {
            log.debug("Sparse IN_TRIP stay inference skipped - implied speed {}km/h exceeds maximum {}km/h",
                    String.format("%.3f", impliedSpeedKmh), String.format("%.1f", MAX_IMPLIED_SPEED_KMH));
            return false;
        }

        log.info("Sparse IN_TRIP stay inference conditions met: gap={}h, distance={}m, impliedSpeed={}km/h",
                String.format("%.2f", gapHours),
                String.format("%.1f", boundaryDistanceMeters),
                String.format("%.3f", impliedSpeedKmh));
        return true;
    }

    private Duration resolveEffectiveMinGapDuration(TimelineConfig config) {
        Integer dataGapThresholdSeconds = config.getDataGapThresholdSeconds();
        Duration configuredGapThreshold = dataGapThresholdSeconds != null && dataGapThresholdSeconds > 0
                ? Duration.ofSeconds(dataGapThresholdSeconds)
                : Duration.ZERO;
        return configuredGapThreshold.compareTo(MIN_GAP_DURATION_FLOOR) > 0
                ? configuredGapThreshold
                : MIN_GAP_DURATION_FLOOR;
    }

    private Trip finalizeTripBeforeGap(UserState userState, TimelineConfig config) {
        if (!userState.hasActivePoints() || userState.getCurrentMode() != ProcessorMode.IN_TRIP) {
            return null;
        }
        return finalizationService.finalizeTrip(userState, config);
    }

    private Stay createInferredStayFromGap(DataGapContext context) {
        Duration stayDuration = Duration.between(context.lastPoint().getTimestamp(), context.currentPoint().getTimestamp());
        double inferredLatitude = (context.lastPoint().getLatitude() + context.currentPoint().getLatitude()) / 2.0;
        double inferredLongitude = (context.lastPoint().getLongitude() + context.currentPoint().getLongitude()) / 2.0;

        return Stay.builder()
                .startTime(context.lastPoint().getTimestamp())
                .duration(stayDuration)
                .latitude(inferredLatitude)
                .longitude(inferredLongitude)
                .locationName(null)
                .favoriteId(null)
                .geocodingId(null)
                .build();
    }
}

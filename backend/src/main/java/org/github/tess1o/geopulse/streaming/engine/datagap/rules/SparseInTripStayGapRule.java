package org.github.tess1o.geopulse.streaming.engine.datagap.rules;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.engine.TimelineEventFinalizationService;
import org.github.tess1o.geopulse.streaming.engine.datagap.model.DataGapContext;
import org.github.tess1o.geopulse.streaming.model.domain.ProcessorMode;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.domain.UserState;

import java.time.Duration;
import java.util.List;

@Slf4j
@ApplicationScoped
public class SparseInTripStayGapRule implements DataGapRule {
    @ConfigProperty(name = "geopulse.timeline.gap_stay_inference.sparse_in_trip.min_gap_duration_floor_hours",
            defaultValue = "3")
    long minGapDurationFloorHours = 3L;

    @ConfigProperty(name = "geopulse.timeline.gap_stay_inference.sparse_in_trip.min_boundary_distance_meters",
            defaultValue = "150.0")
    double minBoundaryDistanceMeters = 150.0d;

    @ConfigProperty(name = "geopulse.timeline.gap_stay_inference.sparse_in_trip.max_boundary_distance_meters",
            defaultValue = "800.0")
    double maxBoundaryDistanceMeters = 800.0d;

    @ConfigProperty(name = "geopulse.timeline.gap_stay_inference.sparse_in_trip.max_implied_speed_kmh",
            defaultValue = "1.0")
    double maxImpliedSpeedKmh = 1.0d;

    private final TimelineEventFinalizationService finalizationService;

    @Inject
    public SparseInTripStayGapRule(TimelineEventFinalizationService finalizationService) {
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
        if (boundaryDistanceMeters < minBoundaryDistanceMeters) {
            log.debug("Sparse IN_TRIP stay inference skipped - distance {}m below minimum {}m",
                    String.format("%.1f", boundaryDistanceMeters), String.format("%.1f", minBoundaryDistanceMeters));
            return false;
        }
        if (boundaryDistanceMeters > maxBoundaryDistanceMeters) {
            log.debug("Sparse IN_TRIP stay inference skipped - distance {}m exceeds maximum {}m",
                    String.format("%.1f", boundaryDistanceMeters), String.format("%.1f", maxBoundaryDistanceMeters));
            return false;
        }

        double gapHours = context.gapDuration().getSeconds() / 3600.0;
        if (gapHours <= 0.0) {
            return false;
        }

        double impliedSpeedKmh = (boundaryDistanceMeters / 1000.0) / gapHours;
        if (impliedSpeedKmh > maxImpliedSpeedKmh) {
            log.debug("Sparse IN_TRIP stay inference skipped - implied speed {}km/h exceeds maximum {}km/h",
                    String.format("%.3f", impliedSpeedKmh), String.format("%.1f", maxImpliedSpeedKmh));
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
        Duration minGapDurationFloor = Duration.ofHours(Math.max(0L, minGapDurationFloorHours));
        return configuredGapThreshold.compareTo(minGapDurationFloor) > 0
                ? configuredGapThreshold
                : minGapDurationFloor;
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

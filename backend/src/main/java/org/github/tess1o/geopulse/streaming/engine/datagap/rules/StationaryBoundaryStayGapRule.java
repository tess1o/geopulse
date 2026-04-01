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
public class StationaryBoundaryStayGapRule implements DataGapRule {
    private static final int DEFAULT_STAY_RADIUS_METERS = 50;
    private static final double DEFAULT_STOP_SPEED_THRESHOLD = 2.0;
    private static final int DEFAULT_MIN_TRIP_POINTS = 3;

    @ConfigProperty(name = "geopulse.timeline.gap_stay_inference.stationary_boundary_in_trip.enabled",
            defaultValue = "true")
    boolean enabled = true;

    @ConfigProperty(name = "geopulse.timeline.gap_stay_inference.stationary_boundary_in_trip.min_gap_duration_floor_hours",
            defaultValue = "3")
    long minGapDurationFloorHours = 3L;

    @ConfigProperty(name = "geopulse.timeline.gap_stay_inference.stationary_boundary_in_trip.max_boundary_distance_meters",
            defaultValue = "100.0")
    double maxBoundaryDistanceMeters = 100.0d;

    @ConfigProperty(name = "geopulse.timeline.gap_stay_inference.stationary_boundary_in_trip.max_implied_speed_kmh",
            defaultValue = "1.0")
    double maxImpliedSpeedKmh = 1.0d;

    private final TimelineEventFinalizationService finalizationService;

    @Inject
    public StationaryBoundaryStayGapRule(TimelineEventFinalizationService finalizationService) {
        this.finalizationService = finalizationService;
    }

    // Explicit constructor for non-CDI tests.
    public StationaryBoundaryStayGapRule(TimelineEventFinalizationService finalizationService,
                                         boolean enabled,
                                         long minGapDurationFloorHours,
                                         double maxBoundaryDistanceMeters,
                                         double maxImpliedSpeedKmh) {
        this.finalizationService = finalizationService;
        this.enabled = enabled;
        this.minGapDurationFloorHours = minGapDurationFloorHours;
        this.maxBoundaryDistanceMeters = maxBoundaryDistanceMeters;
        this.maxImpliedSpeedKmh = maxImpliedSpeedKmh;
    }

    @Override
    public int order() {
        return ORDER_STATIONARY_BOUNDARY_STAY;
    }

    @Override
    public boolean apply(DataGapContext context, List<TimelineEvent> gapEvents) {
        if (!shouldInferStationaryBoundaryStay(context)) {
            return false;
        }

        log.info("Stationary boundary IN_TRIP gap stay inference applied - creating inferred stay instead of data gap");

        Trip finalizedTrip = finalizeTripBeforeGap(context.userState(), context.config());
        if (finalizedTrip != null) {
            gapEvents.add(finalizedTrip);
            log.debug("Finalized trip before stationary boundary stay inference: {} to {}",
                    finalizedTrip.getStartTime(), finalizedTrip.getEndTime());
        }

        Stay inferredStay = createInferredStayFromGap(context);
        gapEvents.add(inferredStay);
        log.debug("Created inferred stationary-boundary stay from gap: {} duration", inferredStay.getDuration());

        // Gap still breaks streaming continuity - restart state machine from current point.
        context.userState().reset();
        log.debug("User state reset after stationary boundary stay inference");
        return true;
    }

    private boolean shouldInferStationaryBoundaryStay(DataGapContext context) {
        if (!Boolean.TRUE.equals(context.config().getGapStayInferenceEnabled())) {
            return false;
        }
        if (!enabled) {
            log.debug("Stationary boundary IN_TRIP stay inference is disabled (enabled={})", enabled);
            return false;
        }
        if (context.userState().getCurrentMode() != ProcessorMode.IN_TRIP) {
            return false;
        }

        int minTripPoints = resolveMinTripPoints(context.config());
        int activePointsCount = context.userState().copyActivePoints().size();
        if (activePointsCount < minTripPoints) {
            log.debug("Stationary boundary IN_TRIP stay inference skipped - active trip points {} below minimum {}",
                    activePointsCount, minTripPoints);
            return false;
        }

        Duration effectiveMinGapDuration = resolveEffectiveMinGapDuration(context.config());
        if (context.gapDuration().compareTo(effectiveMinGapDuration) < 0) {
            log.debug("Stationary boundary IN_TRIP stay inference skipped - gap {} below effective minimum {}",
                    context.gapDuration(), effectiveMinGapDuration);
            return false;
        }

        int stayRadiusMeters = resolveStayRadiusMeters(context.config());
        double effectiveMaxBoundaryDistanceMeters = Math.min(maxBoundaryDistanceMeters, (double) stayRadiusMeters);
        double boundaryDistanceMeters = context.lastPoint().distanceTo(context.currentPoint());
        if (boundaryDistanceMeters > effectiveMaxBoundaryDistanceMeters) {
            log.debug("Stationary boundary IN_TRIP stay inference skipped - boundary distance {}m exceeds maximum {}m "
                            + "(configMax={}m, stayRadius={}m)",
                    String.format("%.1f", boundaryDistanceMeters),
                    String.format("%.1f", effectiveMaxBoundaryDistanceMeters),
                    String.format("%.1f", maxBoundaryDistanceMeters),
                    stayRadiusMeters);
            return false;
        }

        double stopSpeedThreshold = resolveStopSpeedThreshold(context.config());
        if (context.lastPoint().getSpeed() > stopSpeedThreshold) {
            log.debug("Stationary boundary IN_TRIP stay inference skipped - last point speed {}m/s exceeds threshold {}m/s",
                    String.format("%.2f", context.lastPoint().getSpeed()),
                    String.format("%.2f", stopSpeedThreshold));
            return false;
        }
        if (context.currentPoint().getSpeed() > stopSpeedThreshold) {
            log.debug("Stationary boundary IN_TRIP stay inference skipped - current point speed {}m/s exceeds threshold {}m/s",
                    String.format("%.2f", context.currentPoint().getSpeed()),
                    String.format("%.2f", stopSpeedThreshold));
            return false;
        }

        double gapHours = context.gapDuration().toSeconds() / 3600.0;
        if (gapHours <= 0.0) {
            return false;
        }
        double impliedSpeedKmh = (boundaryDistanceMeters / 1000.0) / gapHours;
        if (impliedSpeedKmh > maxImpliedSpeedKmh) {
            log.debug("Stationary boundary IN_TRIP stay inference skipped - implied speed {}km/h exceeds maximum {}km/h",
                    String.format("%.3f", impliedSpeedKmh), String.format("%.1f", maxImpliedSpeedKmh));
            return false;
        }

        log.info("Stationary boundary IN_TRIP stay inference conditions met: gap={}h, distance={}m, impliedSpeed={}km/h, "
                        + "speedThreshold={}m/s",
                String.format("%.2f", gapHours),
                String.format("%.1f", boundaryDistanceMeters),
                String.format("%.3f", impliedSpeedKmh),
                String.format("%.2f", stopSpeedThreshold));
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

    private int resolveStayRadiusMeters(TimelineConfig config) {
        Integer configuredRadius = config.getStaypointRadiusMeters();
        return configuredRadius != null ? configuredRadius : DEFAULT_STAY_RADIUS_METERS;
    }

    private double resolveStopSpeedThreshold(TimelineConfig config) {
        Double configuredThreshold = config.getStaypointVelocityThreshold();
        return configuredThreshold != null ? configuredThreshold : DEFAULT_STOP_SPEED_THRESHOLD;
    }

    private int resolveMinTripPoints(TimelineConfig config) {
        Integer configuredMinPoints = config.getTripArrivalMinPoints();
        return configuredMinPoints != null ? configuredMinPoints : DEFAULT_MIN_TRIP_POINTS;
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

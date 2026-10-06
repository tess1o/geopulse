package org.github.tess1o.geopulse.trips.service;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.mapmatching.service.MapMatchingConfiguration;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.trips.model.dto.TripPlanRouteDto;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemTravelMode;
import org.github.tess1o.geopulse.trips.repository.TripPlanItemRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The line through a trip's planned stops, in plan order. Each leg between consecutive located
 * stops is routed along roads or paths when Valhalla is configured, and drawn straight otherwise -
 * or when the leg is explicitly straight, too long to route, or has no route at all.
 */
@ApplicationScoped
@Slf4j
public class TripPlanRouteService {

    /** Automatic mode: walk legs up to this distance... */
    static final double AUTO_WALK_MAX_METERS = 2_000;
    /** ...drive up to this one, and beyond it assume a flight/ferry/train and draw a straight line. */
    static final double AUTO_DRIVE_MAX_METERS = 1_000_000;

    /** Concurrent Valhalla requests per plan, so a long plan loads quickly without flooding the server. */
    private static final int ROUTING_CONCURRENCY = 4;

    private final TripAccessService tripAccessService;
    private final TripPlanItemRepository tripPlanItemRepository;
    private final MapMatchingConfiguration mapMatchingConfiguration;
    private final TripPlanLegRouter legRouter;
    private final ExecutorService routingExecutor = Executors.newFixedThreadPool(ROUTING_CONCURRENCY, runnable -> {
        Thread thread = new Thread(runnable, "trip-plan-routing");
        thread.setDaemon(true);
        return thread;
    });

    public TripPlanRouteService(TripAccessService tripAccessService,
                                TripPlanItemRepository tripPlanItemRepository,
                                MapMatchingConfiguration mapMatchingConfiguration,
                                TripPlanLegRouter legRouter) {
        this.tripAccessService = tripAccessService;
        this.tripPlanItemRepository = tripPlanItemRepository;
        this.mapMatchingConfiguration = mapMatchingConfiguration;
        this.legRouter = legRouter;
    }

    @PreDestroy
    void shutdown() {
        routingExecutor.shutdownNow();
    }

    public TripPlanRouteDto getPlanRoute(UUID userId, Long tripId) {
        tripAccessService.requireReadAccess(userId, tripId);

        List<TripPlanItemEntity> located = tripPlanItemRepository.findByTripId(tripId).stream()
                .filter(item -> item.getLatitude() != null && item.getLongitude() != null)
                .toList();
        boolean routingAvailable = mapMatchingConfiguration.isAvailable();
        String baseUrl = routingAvailable ? mapMatchingConfiguration.valhallaBaseUrl() : null;

        List<CompletableFuture<TripPlanRouteDto.Leg>> futures = new ArrayList<>();
        for (int i = 1; i < located.size(); i++) {
            TripPlanItemEntity from = located.get(i - 1);
            TripPlanItemEntity to = located.get(i);
            double straightMeters = GeoUtils.haversine(from.getLatitude(), from.getLongitude(),
                    to.getLatitude(), to.getLongitude());
            TripPlanItemTravelMode requested = to.getTravelMode();
            TripPlanItemTravelMode resolved = resolveMode(requested, straightMeters);
            String costing = costingFor(resolved);

            if (!routingAvailable || costing == null) {
                futures.add(CompletableFuture.completedFuture(straightLeg(from, to, requested, resolved, straightMeters)));
                continue;
            }

            TripPlanLegRouter.LegKey key = TripPlanLegRouter.LegKey.of(baseUrl, costing,
                    from.getLatitude(), from.getLongitude(), to.getLatitude(), to.getLongitude());
            futures.add(CompletableFuture
                    .supplyAsync(() -> legRouter.route(key), routingExecutor)
                    .handle((routed, error) -> {
                        if (error != null) {
                            log.debug("Routing plan leg {} -> {} failed, drawing it straight: {}",
                                    from.getId(), to.getId(), error.getMessage());
                        }
                        if (error != null || routed == null || !routed.routed()) {
                            // Shown as a straight line in the requested mode's place.
                            return straightLeg(from, to, requested, TripPlanItemTravelMode.STRAIGHT, straightMeters);
                        }
                        return TripPlanRouteDto.Leg.builder()
                                .fromItemId(from.getId())
                                .toItemId(to.getId())
                                .requestedMode(requested)
                                .resolvedMode(resolved)
                                .routed(true)
                                .coordinates(routed.coordinates())
                                .distanceMeters(routed.distanceMeters() != null ? routed.distanceMeters() : straightMeters)
                                .durationSeconds(routed.durationSeconds())
                                .build();
                    }));
        }

        List<TripPlanRouteDto.Leg> legs = futures.stream().map(CompletableFuture::join).toList();
        return TripPlanRouteDto.builder()
                .routingAvailable(routingAvailable)
                .legs(legs)
                .build();
    }

    /** The destination stop's own mode wins; otherwise the mode is picked from the distance. */
    static TripPlanItemTravelMode resolveMode(TripPlanItemTravelMode requested, double straightMeters) {
        if (requested != null) {
            return requested;
        }
        if (straightMeters <= AUTO_WALK_MAX_METERS) {
            return TripPlanItemTravelMode.WALK;
        }
        if (straightMeters <= AUTO_DRIVE_MAX_METERS) {
            return TripPlanItemTravelMode.DRIVE;
        }
        return TripPlanItemTravelMode.STRAIGHT;
    }

    /** Valhalla costing for a mode; null for a straight line. */
    static String costingFor(TripPlanItemTravelMode mode) {
        return switch (mode) {
            case WALK -> "pedestrian";
            case BICYCLE -> "bicycle";
            case DRIVE -> "auto";
            case STRAIGHT -> null;
        };
    }

    private static TripPlanRouteDto.Leg straightLeg(TripPlanItemEntity from, TripPlanItemEntity to,
                                                    TripPlanItemTravelMode requested,
                                                    TripPlanItemTravelMode resolved,
                                                    double straightMeters) {
        return TripPlanRouteDto.Leg.builder()
                .fromItemId(from.getId())
                .toItemId(to.getId())
                .requestedMode(requested)
                .resolvedMode(resolved)
                .routed(false)
                .coordinates(List.of(
                        new double[]{from.getLatitude(), from.getLongitude()},
                        new double[]{to.getLatitude(), to.getLongitude()}))
                .distanceMeters(straightMeters)
                .build();
    }
}

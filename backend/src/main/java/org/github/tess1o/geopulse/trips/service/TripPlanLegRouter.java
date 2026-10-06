package org.github.tess1o.geopulse.trips.service;

import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.service.simplification.GpsPathSimplifier;
import org.github.tess1o.geopulse.mapmatching.client.ValhallaRestClient;
import org.github.tess1o.geopulse.mapmatching.client.ValhallaRestClients;
import org.github.tess1o.geopulse.mapmatching.client.ValhallaRouteRequest;
import org.github.tess1o.geopulse.mapmatching.client.ValhallaRouteResponse;
import org.github.tess1o.geopulse.mapmatching.client.ValhallaShapeDecoder;
import org.github.tess1o.geopulse.mapmatching.service.MapMatchingConfiguration;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;

import java.time.Instant;
import java.util.List;

/**
 * Routes a single plan leg through Valhalla's {@code /route}, the server already configured for
 * map matching. Results are cached per leg: a plan is re-read on every change, but most of its
 * legs stay the same.
 */
@ApplicationScoped
@Slf4j
public class TripPlanLegRouter {

    public static final String CACHE_NAME = "trip-plan-route-legs";

    /** Routed shapes are dense; this keeps a long drive to a few hundred points without visible change. */
    private static final double SIMPLIFY_TOLERANCE_METERS = 8.0;

    private final MapMatchingConfiguration configuration;

    public TripPlanLegRouter(MapMatchingConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * @return the routed leg, or {@link RoutedLeg#UNROUTABLE} when Valhalla answers that no route
     * exists (an ocean between the stops, a leg beyond the costing's distance limit). That answer is
     * cached like a route. Transient failures throw instead, so they are retried on the next read.
     */
    @CacheResult(cacheName = CACHE_NAME)
    public RoutedLeg route(LegKey key) {
        ValhallaRouteRequest request = ValhallaRouteRequest.builder()
                .locations(List.of(
                        new ValhallaRouteRequest.Location(key.fromLatitude(), key.fromLongitude()),
                        new ValhallaRouteRequest.Location(key.toLatitude(), key.toLongitude())))
                .costing(key.costing())
                .directionsType("none")
                .build();

        ValhallaRouteResponse response;
        try {
            ValhallaRestClient client = ValhallaRestClients.create(configuration);
            response = client.route(request);
        } catch (WebApplicationException e) {
            int status = e.getResponse() != null ? e.getResponse().getStatus() : 0;
            if (status == 400) {
                log.debug("Valhalla found no {} route for plan leg {}", key.costing(), key);
                return RoutedLeg.UNROUTABLE;
            }
            throw e;
        }

        ValhallaRouteResponse.Leg leg = response != null && response.getTrip() != null
                && response.getTrip().getLegs() != null && !response.getTrip().getLegs().isEmpty()
                ? response.getTrip().getLegs().getFirst()
                : null;
        if (leg == null || leg.getShape() == null || leg.getShape().isBlank()) {
            return RoutedLeg.UNROUTABLE;
        }

        List<RoutePoint> points = ValhallaShapeDecoder.decode(leg.getShape()).stream()
                .map(lonLat -> new RoutePoint(lonLat.get(1), lonLat.get(0)))
                .toList();
        if (points.size() < 2) {
            return RoutedLeg.UNROUTABLE;
        }

        List<double[]> coordinates = GpsPathSimplifier.simplifyPath(points, SIMPLIFY_TOLERANCE_METERS).stream()
                .map(point -> new double[]{point.latitude(), point.longitude()})
                .toList();
        ValhallaRouteResponse.Summary summary = leg.getSummary();
        Double distanceMeters = summary != null && summary.getLength() != null ? summary.getLength() * 1000.0 : null;
        Double durationSeconds = summary != null ? summary.getTime() : null;
        return new RoutedLeg(true, coordinates, distanceMeters, durationSeconds);
    }

    /**
     * Cache key. Coordinates are rounded (about a metre) so a stop nudged by float noise still hits
     * the cache; the base URL is part of it so pointing GeoPulse at another Valhalla takes effect.
     */
    public record LegKey(String baseUrl, String costing,
                         double fromLatitude, double fromLongitude,
                         double toLatitude, double toLongitude) {

        public static LegKey of(String baseUrl, String costing,
                                double fromLatitude, double fromLongitude,
                                double toLatitude, double toLongitude) {
            return new LegKey(baseUrl, costing,
                    round(fromLatitude), round(fromLongitude), round(toLatitude), round(toLongitude));
        }

        private static double round(double value) {
            return Math.round(value * 100_000d) / 100_000d;
        }
    }

    public record RoutedLeg(boolean routed, List<double[]> coordinates, Double distanceMeters, Double durationSeconds) {
        public static final RoutedLeg UNROUTABLE = new RoutedLeg(false, List.of(), null, null);
    }

    private record RoutePoint(double latitude, double longitude) implements GpsPoint {
        @Override
        public double getLatitude() {
            return latitude;
        }

        @Override
        public double getLongitude() {
            return longitude;
        }

        @Override
        public Instant getTimestamp() {
            return null;
        }
    }
}

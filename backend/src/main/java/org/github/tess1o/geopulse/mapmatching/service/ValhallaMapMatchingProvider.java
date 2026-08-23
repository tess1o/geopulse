package org.github.tess1o.geopulse.mapmatching.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.mapmatching.client.ValhallaLocation;
import org.github.tess1o.geopulse.mapmatching.client.ValhallaRestClient;
import org.github.tess1o.geopulse.mapmatching.client.ValhallaTraceRouteRequest;
import org.github.tess1o.geopulse.mapmatching.client.ValhallaTraceRouteResponse;
import org.github.tess1o.geopulse.mapmatching.dto.MapMatchedPointDTO;
import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@ApplicationScoped
public class ValhallaMapMatchingProvider implements MapMatchingProvider {

    private final MapMatchingConfiguration configuration;

    @Inject
    GeoPulseWorkloadMetrics workloadMetrics;

    public ValhallaMapMatchingProvider(MapMatchingConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String providerName() {
        return "valhalla";
    }

    @Override
    public List<MapMatchedPointDTO> match(List<GpsPointEntity> points, String profile) {
        return matchSegments(points, profile).stream()
                .flatMap(List::stream)
                .toList();
    }

    @Override
    public List<List<MapMatchedPointDTO>> matchSegments(List<GpsPointEntity> points, String profile) {
        if (!configuration.valhallaConfigured()) {
            throw new IllegalStateException("Valhalla base URL is not configured");
        }
        if (points == null || points.size() < 2) {
            return List.of();
        }

        ValhallaRestClient client = buildClient();
        ValhallaTraceRouteRequest request = buildRequest(points, profile);
        if (request == null) {
            return List.of();
        }

        long started = workloadMetrics == null ? System.nanoTime() : workloadMetrics.start();
        ValhallaTraceRouteResponse response;
        try {
            response = traceRoute(client, request);
            recordValhallaRequest(started, profile, "success");
        } catch (RuntimeException e) {
            recordValhallaRequest(started, profile, "failure");
            throw e;
        }
        return extractCoordinateSegments(response).stream()
                .map(this::toMatchedPoints)
                .filter(segment -> segment.size() >= 2)
                .toList();
    }

    ValhallaTraceRouteRequest buildRequest(List<GpsPointEntity> points, String profile) {
        List<ValhallaLocation> shape = points.stream()
                        .filter(point -> point.getCoordinates() != null)
                        .map(point -> ValhallaLocation.builder()
                                .lat(point.getCoordinates().getY())
                                .lon(point.getCoordinates().getX())
                                .time(point.getTimestamp() != null ? point.getTimestamp().getEpochSecond() : null)
                                .accuracy(point.getAccuracy())
                                .type("through")
                                .build())
                        .toList();

        if (shape.size() < 2) {
            return null;
        }
        shape.get(0).setType("break");
        shape.get(shape.size() - 1).setType("break");

        return ValhallaTraceRouteRequest.builder()
                .shape(shape)
                .costing(profile)
                .shapeMatch("map_snap")
                .format("geojson")
                .directionsType("none")
                .beginTime(shape.get(0).getTime())
                .useTimestamps(shape.stream().allMatch(location -> location.getTime() != null))
                .build();
    }

    private ValhallaRestClient buildClient() {
        return RestClientBuilder.newBuilder()
                .baseUri(URI.create(configuration.valhallaBaseUrl()))
                .connectTimeout(Math.max(1, configuration.getConnectTimeoutSeconds()), TimeUnit.SECONDS)
                .readTimeout(Math.max(1, configuration.getReadTimeoutSeconds()), TimeUnit.SECONDS)
                .build(ValhallaRestClient.class);
    }

    private ValhallaTraceRouteResponse traceRoute(ValhallaRestClient client, ValhallaTraceRouteRequest request) {
        try {
            return client.traceRoute(request);
        } catch (WebApplicationException e) {
            int status = e.getResponse() != null ? e.getResponse().getStatus() : 0;
            String body = readErrorBody(e);
            throw new ValhallaHttpException(status, body, e);
        }
    }

    private String readErrorBody(WebApplicationException e) {
        if (e.getResponse() == null || !e.getResponse().hasEntity()) {
            return "";
        }
        try {
            return e.getResponse().readEntity(String.class);
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    List<List<List<Double>>> extractCoordinateSegments(ValhallaTraceRouteResponse response) {
        if (response == null) {
            return List.of();
        }

        List<List<List<Double>>> segments = new ArrayList<>();
        List<List<Double>> primary = extractPrimaryCoordinates(response);
        if (primary.size() >= 2) {
            segments.add(primary);
        }
        if (response.getAlternates() != null) {
            response.getAlternates().stream()
                    .filter(alternate -> alternate != null && alternate.getTrip() != null)
                    .map(alternate -> extractTripCoordinates(alternate.getTrip()))
                    .filter(coordinates -> coordinates.size() >= 2)
                    .forEach(segments::add);
        }
        return segments;
    }

    private List<List<Double>> extractPrimaryCoordinates(ValhallaTraceRouteResponse response) {
        if (response == null) {
            return List.of();
        }
        if (response.getGeometry() != null && response.getGeometry().getCoordinates() != null) {
            return response.getGeometry().getCoordinates();
        }
        if (response.getFeatures() != null) {
            List<List<Double>> coordinates = response.getFeatures().stream()
                    .filter(feature -> feature.getGeometry() != null)
                    .filter(feature -> feature.getGeometry().getCoordinates() != null)
                    .flatMap(feature -> feature.getGeometry().getCoordinates().stream())
                    .toList();
            if (!coordinates.isEmpty()) {
                return coordinates;
            }
        }
        return extractTripCoordinates(response.getTrip());
    }

    private List<List<Double>> extractTripCoordinates(ValhallaTraceRouteResponse.ValhallaTrip trip) {
        if (trip != null && trip.getLegs() != null) {
            return trip.getLegs().stream()
                    .filter(leg -> leg.getShape() != null && !leg.getShape().isBlank())
                    .flatMap(leg -> decodeValhallaShape(leg.getShape()).stream())
                    .toList();
        }
        return List.of();
    }

    private List<List<Double>> decodeValhallaShape(String encoded) {
        List<List<Double>> coordinates = new ArrayList<>();
        int index = 0;
        int lat = 0;
        int lon = 0;

        while (index < encoded.length()) {
            DecodeResult latResult = decodeValue(encoded, index);
            index = latResult.nextIndex();
            lat += latResult.value();

            DecodeResult lonResult = decodeValue(encoded, index);
            index = lonResult.nextIndex();
            lon += lonResult.value();

            coordinates.add(List.of(lon / 1_000_000.0, lat / 1_000_000.0));
        }

        return coordinates;
    }

    private DecodeResult decodeValue(String encoded, int startIndex) {
        int result = 1;
        int shift = 0;
        int index = startIndex;
        int value;

        do {
            value = encoded.charAt(index++) - 63 - 1;
            result += value << shift;
            shift += 5;
        } while (value >= 0x1f && index < encoded.length());

        return new DecodeResult((result & 1) != 0 ? ~(result >> 1) : result >> 1, index);
    }

    private record DecodeResult(int value, int nextIndex) {
    }

    private List<MapMatchedPointDTO> toMatchedPoints(List<List<Double>> coordinates) {
        List<MapMatchedPointDTO> matched = new ArrayList<>(coordinates.size());
        for (List<Double> coordinate : coordinates) {
            if (coordinate == null || coordinate.size() < 2) {
                continue;
            }
            matched.add(MapMatchedPointDTO.builder()
                    .longitude(coordinate.get(0))
                    .latitude(coordinate.get(1))
                    .build());
        }
        return matched;
    }

    private void recordValhallaRequest(long started, String profile, String result) {
        if (workloadMetrics != null) {
            workloadMetrics.recordTimer("geopulse.map_matching.valhalla.duration", started,
                    "component", "map_matching", "profile", profile, "result", result);
            workloadMetrics.increment("geopulse.map_matching.valhalla.requests",
                    "component", "map_matching", "profile", profile, "result", result);
        }
    }
}

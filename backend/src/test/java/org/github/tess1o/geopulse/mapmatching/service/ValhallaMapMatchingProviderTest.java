package org.github.tess1o.geopulse.mapmatching.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.mapmatching.client.ValhallaTraceRouteRequest;
import org.github.tess1o.geopulse.mapmatching.client.ValhallaTraceRouteResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class ValhallaMapMatchingProviderTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    void serializesContinuousTraceWithDocumentedAccuracyField() throws Exception {
        ValhallaMapMatchingProvider provider = new ValhallaMapMatchingProvider(null);
        ValhallaTraceRouteRequest request = provider.buildRequest(List.of(
                point(30.1, 50.1, "2026-01-01T10:00:00Z", 4.5),
                point(30.2, 50.2, "2026-01-01T10:01:00Z", 5.5),
                point(30.3, 50.3, "2026-01-01T10:02:00Z", 6.5)
        ), "auto");

        assertNotNull(request);
        assertEquals("break", request.getShape().getFirst().getType());
        assertEquals("through", request.getShape().get(1).getType());
        assertEquals("break", request.getShape().getLast().getType());
        assertTrue(request.getUseTimestamps());

        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(request));
        assertEquals(5.5, json.at("/shape/1/accuracy").asDouble());
        assertTrue(json.at("/shape/1/gps_accuracy").isMissingNode());
        assertEquals("map_snap", json.get("shape_match").asText());
        assertEquals("none", json.get("directions_type").asText());
    }

    @Test
    void preservesDisconnectedValhallaAlternateFragmentsAsSeparateSegments() {
        ValhallaMapMatchingProvider provider = new ValhallaMapMatchingProvider(null);
        ValhallaTraceRouteResponse response = new ValhallaTraceRouteResponse();
        ValhallaTraceRouteResponse.ValhallaGeometry primaryGeometry = new ValhallaTraceRouteResponse.ValhallaGeometry();
        primaryGeometry.setCoordinates(List.of(
                List.of(25.612151, 49.540552),
                List.of(25.597602, 49.544325)
        ));
        response.setGeometry(primaryGeometry);

        ValhallaTraceRouteResponse.ValhallaLeg alternateLeg = new ValhallaTraceRouteResponse.ValhallaLeg();
        // First two coordinates from the alternate fragment returned for the
        // 2026-08-16 17:16 Kyiv-time regression trace.
        alternateLeg.setShape("}w|n}A}kkyo@d@}B");
        ValhallaTraceRouteResponse.ValhallaTrip alternateTrip = new ValhallaTraceRouteResponse.ValhallaTrip();
        alternateTrip.setLegs(List.of(alternateLeg));
        ValhallaTraceRouteResponse.ValhallaAlternate alternate = new ValhallaTraceRouteResponse.ValhallaAlternate();
        alternate.setTrip(alternateTrip);
        response.setAlternates(List.of(alternate));

        List<List<List<Double>>> segments = provider.extractCoordinateSegments(response);

        assertEquals(2, segments.size());
        assertEquals(primaryGeometry.getCoordinates(), segments.getFirst());
        assertEquals(2, segments.get(1).size());
    }

    @Test
    void preservesValhallaErrorMetadataAndClassifiesRetryability() {
        ValhallaHttpException exactMatchFailure = new ValhallaHttpException(400,
                "{\"error_code\":443,\"error\":\"Exact route match algorithm failed to find path\"}", null);
        ValhallaHttpException noHttpResponse = new ValhallaHttpException(0, "", null);
        ValhallaHttpException rateLimited = new ValhallaHttpException(429, "rate limited", null);
        ValhallaHttpException unavailable = new ValhallaHttpException(503, "unavailable", null);

        assertEquals(400, exactMatchFailure.getHttpStatus());
        assertEquals(443, exactMatchFailure.getErrorCode());
        assertFalse(exactMatchFailure.isRetryable());
        assertTrue(exactMatchFailure.getMessage().contains("Exact route match algorithm failed"));
        assertTrue(noHttpResponse.isRetryable());
        assertTrue(rateLimited.isRetryable());
        assertTrue(unavailable.isRetryable());
    }

    private GpsPointEntity point(double longitude, double latitude, String timestamp, double accuracy) {
        GpsPointEntity point = new GpsPointEntity();
        point.setCoordinates(GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude)));
        point.setTimestamp(Instant.parse(timestamp));
        point.setAccuracy(accuracy);
        return point;
    }
}

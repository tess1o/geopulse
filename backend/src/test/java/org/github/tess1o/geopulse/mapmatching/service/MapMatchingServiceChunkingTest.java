package org.github.tess1o.geopulse.mapmatching.service;

import org.github.tess1o.geopulse.mapmatching.dto.MapMatchingPointDTO;
import org.github.tess1o.geopulse.mapmatching.dto.MapMatchedPointDTO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class MapMatchingServiceChunkingTest {


    private final MapMatchingService service = new MapMatchingService(
            null, null, null, null, null, null, null, null, null, null);

    @Test
    void capsChunksAndOverlapsOnlyAtContinuousBoundaries() {
        List<MapMatchingPointDTO> points = List.of(
                point("2026-01-01T10:00:00Z"),
                point("2026-01-01T10:01:00Z"),
                point("2026-01-01T10:02:00Z"),
                point("2026-01-01T10:03:00Z"),
                point("2026-01-01T10:04:00Z")
        );

        List<List<MapMatchingPointDTO>> chunks = service.chunkPoints(points, 3);

        assertEquals(List.of(3, 3), chunks.stream().map(List::size).toList());
        assertSame(chunks.getFirst().getLast(), chunks.getLast().getFirst());
    }

    @Test
    void preservesDiscontinuitiesAsSeparateSegmentsAndDropsIsolatedPoints() {
        MapMatchingPointDTO isolated = point("2026-01-01T09:00:00Z");
        MapMatchingPointDTO first = point("2026-01-01T10:00:00Z");
        MapMatchingPointDTO second = point("2026-01-01T10:01:00Z");

        List<List<MapMatchingPointDTO>> chunks = service.chunkPoints(List.of(isolated, first, second), 100);

        assertEquals(1, chunks.size());
        assertEquals(List.of(first, second), chunks.getFirst());
    }

    @Test
    void retriesOnlyTransientValhallaHttpFailures() {
        assertFalse(service.isRetryableFailure(new ValhallaHttpException(400,
                "{\"error_code\":443}", null)));
        assertTrue(service.isRetryableFailure(new ValhallaHttpException(408, "timeout", null)));
        assertTrue(service.isRetryableFailure(new ValhallaHttpException(429, "rate limited", null)));
        assertTrue(service.isRetryableFailure(new ValhallaHttpException(500, "unavailable", null)));
        assertTrue(service.isRetryableFailure(new IllegalStateException("connection refused")));
    }

    @Test
    void rejectsTinyMatchedFragmentsForLongContinuousTrip() {
        MapMatchingService service = serviceWithQualityConfig();
        List<MapMatchingPointDTO> input = List.of(
                point("2026-08-23T10:00:00Z", 25.60, 49.55),
                point("2026-08-23T10:30:00Z", 25.90, 49.35),
                point("2026-08-23T11:00:00Z", 26.20, 49.25)
        );
        List<List<MapMatchedPointDTO>> fragments = List.of(
                List.of(matched(25.60, 49.55), matched(25.61, 49.55)),
                List.of(matched(26.19, 49.25), matched(26.20, 49.25))
        );

        assertFalse(service.hasAcceptableMatchedCoverage(input, fragments));
    }

    @Test
    void acceptsContinuousMatchedGeometryForLongTrip() {
        MapMatchingService service = serviceWithQualityConfig();
        List<MapMatchingPointDTO> input = List.of(
                point("2026-08-23T10:00:00Z", 25.60, 49.55),
                point("2026-08-23T10:30:00Z", 25.90, 49.35),
                point("2026-08-23T11:00:00Z", 26.20, 49.25)
        );
        List<List<MapMatchedPointDTO>> matched = List.of(List.of(
                matched(25.60, 49.55),
                matched(25.90, 49.35),
                matched(26.20, 49.25)
        ));

        assertTrue(service.hasAcceptableMatchedCoverage(input, matched));
    }

    private MapMatchingService serviceWithQualityConfig() {
        MapMatchingConfiguration configuration = mock(MapMatchingConfiguration.class);
        when(configuration.getQualityMinRawDistanceMeters()).thenReturn(500);
        when(configuration.getQualityMinDistanceCoveragePercent()).thenReturn(35);
        when(configuration.getQualityMaxDiscontinuityPercent()).thenReturn(10);
        when(configuration.getQualityMaxShortDiscontinuityMeters()).thenReturn(100);
        return new MapMatchingService(
                configuration, null, null, null, null, null, null, null, null, null);
    }

    private MapMatchingPointDTO point(String timestamp) {
        MapMatchingPointDTO point = new MapMatchingPointDTO();
        point.setTimestamp(Instant.parse(timestamp));
        return point;
    }

    private MapMatchingPointDTO point(String timestamp, double longitude, double latitude) {
        MapMatchingPointDTO point = point(timestamp);
        point.setLongitude(longitude);
        point.setLatitude(latitude);
        return point;
    }

    private MapMatchedPointDTO matched(double longitude, double latitude) {
        return MapMatchedPointDTO.builder()
                .longitude(longitude)
                .latitude(latitude)
                .build();
    }
}

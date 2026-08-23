package org.github.tess1o.geopulse.mapmatching.service;

import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class MapMatchingServiceChunkingTest {

    private final MapMatchingService service = new MapMatchingService(
            null, null, null, null, null, null, null, null, null, null);

    @Test
    void capsChunksAndOverlapsOnlyAtContinuousBoundaries() {
        List<GpsPointEntity> points = List.of(
                point("2026-01-01T10:00:00Z"),
                point("2026-01-01T10:01:00Z"),
                point("2026-01-01T10:02:00Z"),
                point("2026-01-01T10:03:00Z"),
                point("2026-01-01T10:04:00Z")
        );

        List<List<GpsPointEntity>> chunks = service.chunkPoints(points, 3);

        assertEquals(List.of(3, 3), chunks.stream().map(List::size).toList());
        assertSame(chunks.getFirst().getLast(), chunks.getLast().getFirst());
    }

    @Test
    void preservesDiscontinuitiesAsSeparateSegmentsAndDropsIsolatedPoints() {
        GpsPointEntity isolated = point("2026-01-01T09:00:00Z");
        GpsPointEntity first = point("2026-01-01T10:00:00Z");
        GpsPointEntity second = point("2026-01-01T10:01:00Z");

        List<List<GpsPointEntity>> chunks = service.chunkPoints(List.of(isolated, first, second), 100);

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

    private GpsPointEntity point(String timestamp) {
        GpsPointEntity point = new GpsPointEntity();
        point.setTimestamp(Instant.parse(timestamp));
        return point;
    }
}

package org.github.tess1o.geopulse.mapmatching.service;

import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Tag("unit")
class MapMatchingHashServiceTest {

    @Test
    void cacheIdentityChangesWithInputAndConfiguration() {
        MapMatchingHashService service = new MapMatchingHashService();
        GpsPointEntity point = new GpsPointEntity();
        point.setId(1L);
        point.setTimestamp(Instant.parse("2026-01-01T10:00:00Z"));
        point.setCoordinates(new GeometryFactory().createPoint(new Coordinate(30.1, 50.1)));
        point.setAccuracy(5.0);

        String originalInput = service.inputHash(List.of(point), 50.0);
        point.setAccuracy(7.0);

        assertNotEquals(originalInput, service.inputHash(List.of(point), 50.0));
        assertNotEquals(service.configHash("url=a|algorithm=v1"), service.configHash("url=b|algorithm=v2"));
    }

    @Test
    void inputHashIgnoresDatabasePointIds() {
        MapMatchingHashService service = new MapMatchingHashService();
        GpsPointEntity first = point(1L);
        GpsPointEntity recreated = point(99L);

        assertEquals(service.inputHash(List.of(first), 50.0), service.inputHash(List.of(recreated), 50.0));
    }

    private GpsPointEntity point(Long id) {
        GpsPointEntity point = new GpsPointEntity();
        point.setId(id);
        point.setTimestamp(Instant.parse("2026-01-01T10:00:00Z"));
        point.setCoordinates(new GeometryFactory().createPoint(new Coordinate(30.1, 50.1)));
        point.setAccuracy(5.0);
        return point;
    }
}

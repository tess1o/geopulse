package org.github.tess1o.geopulse.streaming.service;

import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Distance shown for the related favorite on the place details page.
 *
 * <p>The area is a ~111 m x ~72 m box at 50°N, where a degree of longitude is only ~0.64 of a degree
 * of latitude, so east-west gaps expose any planar degree-to-meter conversion.
 */
@Tag("unit")
class PlaceDetailsServiceDistanceTest {

    private static final double SOUTH = 50.000;
    private static final double NORTH = 50.001;
    private static final double WEST = 10.000;
    private static final double EAST = 10.001;
    private static final double MID_LAT = 50.0005;
    private static final double MID_LON = 10.0005;

    private final PlaceDetailsService service = new PlaceDetailsService();
    private final FavoritesEntity area = FavoritesEntity.builder()
            .type(FavoriteLocationType.AREA)
            .geometry(GeoUtils.buildBoundingBoxPolygon(SOUTH, NORTH, WEST, EAST))
            .build();

    @Test
    void pointEastOfAreaUsesSphericalDistance() {
        double pointLon = EAST + 0.00014; // ~10 m east of the edge at 50°N
        Point point = GeoUtils.createPoint(pointLon, MID_LAT);

        double expected = GeoUtils.haversine(MID_LAT, EAST, MID_LAT, pointLon);

        // The old planar conversion reported ~15.6 m here.
        assertEquals(10.0, expected, 0.1);
        assertEquals(expected, service.calculateDistance(point, area), 0.01);
    }

    @Test
    void pointNorthOfAreaUsesSphericalDistance() {
        double pointLat = NORTH + 0.0001; // ~11 m north of the edge
        Point point = GeoUtils.createPoint(MID_LON, pointLat);

        double expected = GeoUtils.haversine(NORTH, MID_LON, pointLat, MID_LON);

        assertEquals(expected, service.calculateDistance(point, area), 0.01);
    }

    @Test
    void pointInsideAreaIsZero() {
        Point point = GeoUtils.createPoint(MID_LON, MID_LAT);

        assertEquals(0.0, service.calculateDistance(point, area));
    }
}

package org.github.tess1o.geopulse.streaming.engine;

import org.github.tess1o.geopulse.favorites.model.FavoriteAreaDto;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;

import java.util.Comparator;
import java.util.List;

/**
 * How stay detection uses favorite areas: a point outside the stay radius still belongs to the stay when it is in the
 * same favorite area as the first stay point.
 */
final class FavoriteAreas {

    private FavoriteAreas() {
    }

    /**
     * Whether the stay has points outside its radius that belong to it only because they share a favorite area with
     * its first point. Decided from the stay's own points, so it comes out the same however the points arrived.
     */
    static boolean keepsPointsOutsideRadius(List<GPSPoint> stayPoints, GPSPoint centre, double radiusMeters,
                                            List<FavoriteAreaDto> favoriteAreas) {
        FavoriteAreaDto centreArea = containing(stayPoints.isEmpty() ? centre : stayPoints.getFirst(), favoriteAreas);
        if (centreArea == null) {
            return false;
        }
        return stayPoints.stream().anyMatch(point ->
                centre.distanceTo(point) > radiusMeters + StreamingTimelineProcessor.DISTANCE_EPSILON_METERS
                        && sameArea(containing(point, favoriteAreas), centreArea));
    }

    static boolean sameArea(FavoriteAreaDto first, FavoriteAreaDto second) {
        return first != null && second != null && first.getId() == second.getId();
    }

    /**
     * Where areas overlap, the most specific one wins: the smallest, then the one saved first. Place lookups order
     * covering areas the same way (see FavoritesRepository), so detection and naming agree, whatever order the areas
     * were saved or updated in.
     */
    static final Comparator<FavoriteAreaDto> MOST_SPECIFIC_FIRST = Comparator
            .comparingDouble((FavoriteAreaDto area) -> area.preparedGeometry().getGeometry().getArea())
            .thenComparingLong(FavoriteAreaDto::getId);

    /** The areas in the order {@link #containing} tries them. */
    static List<FavoriteAreaDto> mostSpecificFirst(List<FavoriteAreaDto> favoriteAreas) {
        return favoriteAreas.stream().sorted(MOST_SPECIFIC_FIRST).toList();
    }

    /**
     * The favorite area that contains the point, using polygon coverage, or null if none does. With the areas in
     * {@link #mostSpecificFirst} order, the most specific one.
     */
    static FavoriteAreaDto containing(GPSPoint point, List<FavoriteAreaDto> favoriteAreas) {
        if (point == null || favoriteAreas == null || favoriteAreas.isEmpty()) {
            return null;
        }

        // Bounding boxes first, on the rounded coordinates createPoint would use: most areas are
        // ruled out without building a JTS point (whose envelope checks allocate on every call).
        double lon = GeoUtils.roundCoordinate(point.getLongitude());
        double lat = GeoUtils.roundCoordinate(point.getLatitude());
        org.locationtech.jts.geom.Point location = null;
        for (FavoriteAreaDto area : favoriteAreas) {
            if (!area.boundsCover(lon, lat)) {
                continue;
            }
            if (location == null) {
                location = GeoUtils.createPoint(point.getLongitude(), point.getLatitude());
            }
            if (area.preparedGeometry().covers(location)) return area;
        }

        return null;
    }
}

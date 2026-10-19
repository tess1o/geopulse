package org.github.tess1o.geopulse.streaming.engine;

import org.github.tess1o.geopulse.favorites.model.FavoriteAreaDto;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;

import java.util.List;

/**
 * How stay detection uses favorite areas: a point outside the stay radius still belongs to the stay when it is in the
 * same favorite area as the stay centre.
 */
final class FavoriteAreas {

    private FavoriteAreas() {
    }

    /**
     * Whether the stay has points outside its radius that belong to it only because they share a favorite area with
     * its centre. Decided from the stay's own points, so it comes out the same however the points arrived.
     */
    static boolean keepsPointsOutsideRadius(List<GPSPoint> stayPoints, GPSPoint centre, double radiusMeters,
                                            List<FavoriteAreaDto> favoriteAreas) {
        FavoriteAreaDto centreArea = containing(centre, favoriteAreas);
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
     * The favorite area that contains the point, using rectangular bounding box containment, or null if none does.
     */
    static FavoriteAreaDto containing(GPSPoint point, List<FavoriteAreaDto> favoriteAreas) {
        if (point == null || favoriteAreas == null || favoriteAreas.isEmpty()) {
            return null;
        }

        double pointLat = point.getLatitude();
        double pointLon = point.getLongitude();

        for (FavoriteAreaDto area : favoriteAreas) {
            if (pointLat >= area.getSouthWestLat() && pointLat <= area.getNorthEastLat() &&
                    pointLon >= area.getSouthWestLon() && pointLon <= area.getNorthEastLon()) {
                return area;
            }
        }

        return null;
    }
}

package org.github.tess1o.geopulse.streaming.edits;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionWaypointDto;
import java.util.List;

/** Coordinates supplied by the user, never sensor observations. */
public final class ManualRouteGeometry {
    private ManualRouteGeometry() {}
    public static List<TripReconstructionWaypointDto> read(Object value, ObjectMapper json) {
        List<TripReconstructionWaypointDto> route = json.convertValue(value, new TypeReference<>() {});
        validate(route);
        return route;
    }

    public static void validate(List<TripReconstructionWaypointDto> route) {
        if (route == null || route.size() < 2 || route.size() > 1000)
            throw new IllegalArgumentException("A manual route requires 2 to 1000 points");
        for (var point : route) {
            if (point == null || point.getLatitude() == null || point.getLongitude() == null)
                throw new IllegalArgumentException("A manual route requires valid coordinates");
            GeoUtils.validateCoordinates(point.getLatitude(), point.getLongitude(), "manual route point");
        }
    }

    public static double distance(List<TripReconstructionWaypointDto> route) {
        double distance = 0;
        for (int i = 1; i < route.size(); i++) {
            var first = route.get(i - 1); var second = route.get(i);
            distance += GeoUtils.haversine(first.getLatitude(), first.getLongitude(), second.getLatitude(), second.getLongitude());
        }
        return distance;
    }
}

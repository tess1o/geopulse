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

    public static List<TripReconstructionWaypointDto> slice(List<TripReconstructionWaypointDto> route, double from, double to) {
        double total = distance(route);
        double lower = Math.max(0, Math.min(1, from)) * total;
        double upper = Math.max(0, Math.min(1, to)) * total;
        var result = new java.util.ArrayList<TripReconstructionWaypointDto>();
        result.add(pointAt(route, lower));
        double travelled = 0;
        for (int i = 1; i < route.size(); i++) {
            travelled += GeoUtils.haversine(route.get(i-1).getLatitude(), route.get(i-1).getLongitude(), route.get(i).getLatitude(), route.get(i).getLongitude());
            if (travelled > lower && travelled < upper) result.add(route.get(i));
        }
        result.add(pointAt(route, upper));
        return result;
    }

    private static TripReconstructionWaypointDto pointAt(List<TripReconstructionWaypointDto> route, double distance) {
        for (int i = 1; i < route.size(); i++) {
            var a = route.get(i-1); var b = route.get(i);
            double length = GeoUtils.haversine(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
            if (distance <= length && length > 0) {
                double fraction = distance / length;
                return new TripReconstructionWaypointDto(a.getLatitude() + fraction * (b.getLatitude() - a.getLatitude()),
                        Math.IEEEremainder(a.getLongitude() + fraction * Math.IEEEremainder(b.getLongitude() - a.getLongitude(), 360), 360));
            }
            distance -= length;
        }
        var last = route.getLast();
        return new TripReconstructionWaypointDto(last.getLatitude(), last.getLongitude());
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

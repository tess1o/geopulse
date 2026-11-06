package org.github.tess1o.geopulse.favorites.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.valid.IsValidOp;

import java.util.Arrays;
import java.util.List;

/** GeoJSON for a single, closed favorite polygon in longitude/latitude order. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AreaGeometry {
    private String type;
    private List<List<List<Double>>> coordinates;

    public Polygon toPolygon() {
        if (!"Polygon".equals(type) || coordinates == null || coordinates.size() != 1
                || coordinates.getFirst() == null) {
            throw new IllegalArgumentException("Area geometry must be a Polygon with one ring and no holes");
        }
        var ring = coordinates.getFirst();
        if (ring.size() < 4 || ring.size() > 501) {
            throw new IllegalArgumentException("Area must have between 3 and 500 vertices");
        }
        Coordinate[] points = new Coordinate[ring.size()];
        for (int i = 0; i < ring.size(); i++) {
            var position = ring.get(i);
            if (position == null || position.size() != 2 || position.get(0) == null || position.get(1) == null
                    || !Double.isFinite(position.get(0)) || !Double.isFinite(position.get(1))
                    || Math.abs(position.get(0)) > 180 || Math.abs(position.get(1)) > 90) {
                throw new IllegalArgumentException("Area coordinates must be finite longitude/latitude pairs in range");
            }
            points[i] = new Coordinate(position.get(0), position.get(1));
            if (i > 0 && points[i].equals2D(points[i - 1])) {
                throw new IllegalArgumentException("Adjacent area vertices must be distinct");
            }
            if (i > 0 && Math.abs(points[i].x - points[i - 1].x) > 180) {
                throw new IllegalArgumentException("Areas crossing the antimeridian are not supported");
            }
        }
        if (!points[0].equals2D(points[points.length - 1])) {
            throw new IllegalArgumentException("Area polygon must be closed");
        }
        Polygon polygon = new GeometryFactory(new PrecisionModel(), 4326).createPolygon(points);
        if (!new IsValidOp(polygon).isValid() || polygon.getArea() <= 0) {
            throw new IllegalArgumentException("Area must have positive area and no crossing edges");
        }
        return polygon;
    }

    public static Polygon resolve(AreaGeometry geometry, Double north, Double east, Double south, Double west) {
        boolean hasBounds = north != null || east != null || south != null || west != null;
        if (geometry != null) {
            if (hasBounds) throw new IllegalArgumentException("Provide geometry or rectangle bounds, not both");
            return geometry.toPolygon();
        }
        if (north == null || east == null || south == null || west == null) {
            throw new IllegalArgumentException("Provide geometry or all four rectangle bounds");
        }
        if (!Double.isFinite(north) || !Double.isFinite(east) || !Double.isFinite(south) || !Double.isFinite(west)
                || Math.abs(north) > 90 || Math.abs(south) > 90 || Math.abs(east) > 180 || Math.abs(west) > 180
                || north <= south || east <= west) {
            throw new IllegalArgumentException("Invalid rectangle bounds: north/east must exceed south/west within geographic ranges");
        }
        return GeoUtils.buildBoundingBoxPolygon(south, north, west, east);
    }

    public static AreaGeometry from(Geometry geometry) {
        return new AreaGeometry("Polygon", List.of(Arrays.stream(geometry.getCoordinates())
                .map(c -> List.of(c.x, c.y)).toList()));
    }
}

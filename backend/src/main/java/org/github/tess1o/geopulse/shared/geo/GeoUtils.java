package org.github.tess1o.geopulse.shared.geo;

import org.locationtech.jts.geom.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Utility class for geographic calculations and spatial operations using JTS (Java Topology Suite).
 * 
 * This class provides methods for:
 * - Distance calculations using the Haversine formula
 * - Creation of geometric objects (Points, Polygons) with proper SRID (4326 - WGS84)
 * - Trip distance calculations along GPS paths
 * - Bounding box creation for spatial queries
 * 
 * All spatial objects created use SRID 4326 (WGS84 coordinate system) which is the standard
 * for GPS coordinates (latitude/longitude).
 * 
 * Thread Safety: This class is thread-safe as it only contains static methods and immutable constants.
 * 
 * @author GeoPulse Team
 * @version 1.0
 * @since 1.0
 */
public final class GeoUtils {

    /**
     * Earth's radius in meters using the WGS84 ellipsoid approximation.
     * This value is used for Haversine distance calculations.
     */
    private static final double EARTH_RADIUS_METERS = 6371000;
    
    /**
     * Shared GeometryFactory instance configured for WGS84 coordinate system (SRID 4326).
     * Uses high precision model for accurate geometric calculations.
     */
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    /**
     * Decimal scale for coordinate precision when creating Point geometries.
     * 8 decimal places provides approximately 1.1 meter precision at the equator.
     */
    private static final int POINT_SCALE = 8;
    
    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private GeoUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Calculate the great-circle distance between two points on Earth using the Haversine formula.
     * 
     * The Haversine formula determines the great-circle distance between two points on a sphere
     * given their latitude and longitude. This implementation assumes Earth is a perfect sphere
     * with radius 6,371,000 meters, which provides good accuracy for most applications
     * (error typically less than 0.5%).
     * 
     * Formula: a = sin²(Δφ/2) + cos φ1 ⋅ cos φ2 ⋅ sin²(Δλ/2)
     *          c = 2 ⋅ atan2( √a, √(1−a) )
     *          d = R ⋅ c
     * 
     * Where:
     * - φ is latitude, λ is longitude
     * - R is earth's radius (6,371,000 meters)
     * - Δφ is the difference in latitude
     * - Δλ is the difference in longitude
     * 
     * @param lat1 latitude of first point in decimal degrees (range: -90 to 90)
     * @param lon1 longitude of first point in decimal degrees (range: -180 to 180)
     * @param lat2 latitude of second point in decimal degrees (range: -90 to 90)
     * @param lon2 longitude of second point in decimal degrees (range: -180 to 180)
     * @return the distance between the two points in meters (always positive)
     * @throws IllegalArgumentException if coordinates are outside valid ranges or NaN/Infinite
     */
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        // Validate coordinate ranges
        validateCoordinates(lat1, lon1, "first point");
        validateCoordinates(lat2, lon2, "second point");
        
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Create a JTS Point geometry from string coordinates.
     * 
     * This method parses string representations of longitude and latitude
     * and creates a Point geometry with proper precision and SRID.
     * 
     * @param lon longitude as string (range: -180 to 180)
     * @param lat latitude as string (range: -90 to 90)
     * @return a Point geometry with SRID 4326 (WGS84)
     * @throws NumberFormatException if either coordinate string cannot be parsed as double
     * @throws IllegalArgumentException if coordinates are outside valid ranges or null
     * @see #createPoint(Double, Double)
     */
    public static Point createPoint(String lon, String lat) {
        if (lon == null || lat == null) {
            throw new IllegalArgumentException("Coordinate strings cannot be null");
        }
        
        try {
            double lonValue = Double.parseDouble(lon);
            double latValue = Double.parseDouble(lat);
            return createPoint(lonValue, latValue);
        } catch (NumberFormatException e) {
            NumberFormatException invalidCoordinates = new NumberFormatException(
                    "Invalid coordinate format - longitude: '" + lon + "', latitude: '" + lat + "'");
            invalidCoordinates.initCause(e);
            throw invalidCoordinates;
        }
    }

    /**
     * Create a JTS Point geometry from double coordinates with precision rounding.
     * 
     * This method creates a Point geometry with coordinates rounded to 8 decimal places
     * for consistent precision (~1.1 meter accuracy). The resulting Point uses SRID 4326
     * (WGS84 coordinate system) which is standard for GPS coordinates.
     * 
     * Coordinate precision:
     * - 8 decimal places ≈ 1.1 meter precision at equator
     * - Uses HALF_UP rounding mode for consistent behavior
     * 
     * @param lon longitude in decimal degrees (range: -180 to 180)
     * @param lat latitude in decimal degrees (range: -90 to 90)
     * @return a Point geometry with SRID 4326 and rounded coordinates
     * @throws IllegalArgumentException if coordinates are outside valid ranges, null, or NaN/Infinite
     */
    public static Point createPoint(Double lon, Double lat) {
        if (lon == null || lat == null) {
            throw new IllegalArgumentException("Coordinates cannot be null");
        }
        
        validateCoordinates(lat, lon, "point");
        
        return GEOMETRY_FACTORY.createPoint(
                new Coordinate(roundCoordinate(lon), roundCoordinate(lat))
        );
    }

    /**
     * {@code BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP).doubleValue()}, without
     * allocating in the common case. Timeline generation rounds every centroid and every point it
     * tests against a favorite area, millions of times per rebuild.
     * <p>
     * For |value| &lt; 1000, {@code |value| * 1e8} is an integer plus a fraction known to within about
     * 1.3e-5 (half an ulp between the double and its shortest decimal, which {@code BigDecimal.valueOf}
     * starts from, plus the multiplication's rounding). Unless the fraction is within 1e-4 of one
     * half, that decides the HALF_UP direction, and {@code n / 1e8} (two exact doubles) is correctly
     * rounded, as is {@code BigDecimal.doubleValue()} of the same decimal. Near-ties, zero, NaN and
     * large magnitudes take the exact BigDecimal path.
     */
    public static double roundCoordinate(double value) {
        double magnitude = Math.abs(value);
        if (magnitude > 0 && magnitude < 1000) {
            double scaled = magnitude * 1e8;
            double floor = Math.floor(scaled);
            double fraction = scaled - floor;
            if (Math.abs(fraction - 0.5) > 1e-4) {
                double rounded = (fraction > 0.5 ? floor + 1 : floor) / 1e8;
                return value < 0 ? -rounded : rounded;
            }
        }
        return BigDecimal.valueOf(value).setScale(POINT_SCALE, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Create a rectangular polygon from Leaflet-style bounds (North-East, South-West format).
     * 
     * This method creates a rectangular polygon suitable for spatial queries from bounds
     * typically provided by frontend mapping libraries like Leaflet. The coordinates follow
     * the Leaflet convention where bounds are specified as North-East and South-West corners.
     * 
     * The resulting polygon follows the right-hand rule for coordinate ordering (counter-clockwise)
     * and is properly closed by repeating the first coordinate at the end.
     * 
     * @param northLat northern boundary latitude in decimal degrees (range: -90 to 90)
     * @param eastLon eastern boundary longitude in decimal degrees (range: -180 to 180)
     * @param southLat southern boundary latitude in decimal degrees (range: -90 to 90, must be < northLat)
     * @param westLon western boundary longitude in decimal degrees (range: -180 to 180, must be < eastLon)
     * @return a rectangular Polygon geometry with SRID 4326
     * @throws IllegalArgumentException if coordinates are invalid or bounds are inconsistent
     */
    public static Polygon createRectangleFromLeafletBounds(double northLat, double eastLon, double southLat, double westLon) {
        // Validate individual coordinates
        validateCoordinates(northLat, eastLon, "north-east corner");
        validateCoordinates(southLat, westLon, "south-west corner");
        
        // Validate bounds consistency
        if (southLat >= northLat) {
            throw new IllegalArgumentException("South latitude (" + southLat + ") must be less than north latitude (" + northLat + ")");
        }
        if (westLon >= eastLon) {
            throw new IllegalArgumentException("West longitude (" + westLon + ") must be less than east longitude (" + eastLon + ")");
        }
        
        Coordinate[] coordinates = new Coordinate[] {
                new Coordinate(westLon, northLat), // top-left
                new Coordinate(eastLon, northLat), // top-right
                new Coordinate(eastLon, southLat), // bottom-right
                new Coordinate(westLon, southLat), // bottom-left
                new Coordinate(westLon, northLat)  // close polygon
        };
        LinearRing ring = GEOMETRY_FACTORY.createLinearRing(coordinates);
        return GEOMETRY_FACTORY.createPolygon(ring, null);
    }

    /**
     * Create a rectangular bounding box polygon from cardinal direction bounds.
     * 
     * This method creates a rectangular polygon from bounds specified in the more traditional
     * geographic format (south, north, west, east). This is often used for spatial database
     * queries and GIS operations.
     * 
     * Note: This method provides the same functionality as createRectangleFromLeafletBounds()
     * but with a different parameter order. Consider using createRectangleFromLeafletBounds()
     * for new code to maintain consistency.
     * 
     * @param south southern boundary latitude in decimal degrees (range: -90 to 90)
     * @param north northern boundary latitude in decimal degrees (range: -90 to 90, must be > south)
     * @param west western boundary longitude in decimal degrees (range: -180 to 180)
     * @param east eastern boundary longitude in decimal degrees (range: -180 to 180, must be > west)
     * @return a rectangular Polygon geometry with SRID 4326
     * @throws IllegalArgumentException if coordinates are invalid or bounds are inconsistent
     */
    public static Polygon buildBoundingBoxPolygon(double south, double north, double west, double east) {
        // Delegate to the main method with reordered parameters
        return createRectangleFromLeafletBounds(north, east, south, west);
    }

    public static LineString convertGpsPointsToLineString(List<? extends GpsPoint> points) {
        if (points == null || points.size() < 2) {
            return null;
        }

        Coordinate[] coordinates = points.stream()
                .map(point -> new Coordinate(point.getLongitude(), point.getLatitude()))
                .toArray(Coordinate[]::new);

        return GEOMETRY_FACTORY.createLineString(coordinates);
    }
    
    /**
     * Validate latitude and longitude coordinates.
     * 
     * @param lat latitude in decimal degrees
     * @param lon longitude in decimal degrees  
     * @param context description of the coordinates being validated (for error messages)
     * @throws IllegalArgumentException if coordinates are outside valid ranges or NaN/Infinite
     */
    public static void validateCoordinates(double lat, double lon, String context) {
        if (Double.isNaN(lat) || Double.isInfinite(lat)) {
            throw new IllegalArgumentException("Latitude for " + context + " must be a valid number, got: " + lat);
        }
        if (Double.isNaN(lon) || Double.isInfinite(lon)) {
            throw new IllegalArgumentException("Longitude for " + context + " must be a valid number, got: " + lon);
        }
        if (lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("Latitude for " + context + " must be between -90 and 90 degrees, got: " + lat);
        }
        if (lon < -180.0 || lon > 180.0) {
            throw new IllegalArgumentException("Longitude for " + context + " must be between -180 and 180 degrees, got: " + lon);
        }
    }
}

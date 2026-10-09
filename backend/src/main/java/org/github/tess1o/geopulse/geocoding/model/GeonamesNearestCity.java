package org.github.tess1o.geopulse.geocoding.model;

/**
 * Nearest GeoNames city to a coordinate, as stored (the timezone is the raw IANA id from the dataset).
 */
public record GeonamesNearestCity(
        Long geonameId,
        String name,
        String countryCode,
        String timezone,
        double distanceMeters
) {
}

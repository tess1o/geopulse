package org.github.tess1o.geopulse.geocoding.model;

/**
 * Normalized city/country resolved from GeoNames by coordinates.
 */
public record GeonamesNormalizedLocation(
        Long geonameId,
        String city,
        String country,
        String countryCode,
        Double distanceMeters
) {
}

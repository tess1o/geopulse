package org.github.tess1o.geopulse.geocoding.model;

/**
 * Parsed row from GeoNames countryInfo dataset.
 */
public record GeonamesCountryRecord(
        String isoAlpha2,
        String isoAlpha3,
        Integer isoNumeric,
        String fipsCode,
        String countryName,
        String capital,
        Double areaSqKm,
        Long population,
        String continent,
        String tld,
        String currencyCode,
        String currencyName,
        String phone,
        String postalCodeFormat,
        String postalCodeRegex,
        String languages,
        Long geonameId,
        String neighbors,
        String equivalentFipsCode
) {
}

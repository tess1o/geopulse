package org.github.tess1o.geopulse.geocoding.model;

import java.time.LocalDate;

/**
 * Parsed row from GeoNames "cities500" dataset.
 */
public record GeonamesCityRecord(
        Long geonameId,
        String name,
        String asciiName,
        String alternateNames,
        Double latitude,
        Double longitude,
        String featureClass,
        String featureCode,
        String countryCode,
        String cc2,
        String admin1Code,
        String admin2Code,
        String admin3Code,
        String admin4Code,
        Long population,
        Integer elevation,
        Integer dem,
        String timezone,
        LocalDate modificationDate
) {
}

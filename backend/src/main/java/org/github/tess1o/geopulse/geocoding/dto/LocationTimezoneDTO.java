package org.github.tess1o.geopulse.geocoding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Local timezone of a coordinate, resolved from the nearest GeoNames city.
 *
 * <p>Only returned when the client asks for location timezones. Besides the zone it carries where the zone came
 * from, so a wrong time can be traced to a specific nearest city.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Local timezone of a location, resolved from the nearest GeoNames city.")
public class LocationTimezoneDTO {

    public enum Status {
        /** {@link #timezone} is set. */
        RESOLVED,
        /** The nearest city is farther than the configured maximum distance (open sea, polar regions). */
        BEYOND_MAX_DISTANCE,
        /** The GeoNames city table is empty, for example because the import is disabled. */
        NO_GEONAMES_DATA,
        /** The nearest city has a timezone id the server does not recognise. */
        INVALID_TIMEZONE
    }

    @Schema(description = "IANA timezone id. Absent when the timezone could not be resolved; clients then fall back "
            + "to the profile timezone.", examples = "Europe/Warsaw")
    private String timezone;

    @Schema(description = "Whether the timezone was resolved, and why not if it wasn't.")
    private Status status;

    @Schema(description = "Nearest GeoNames city the timezone was taken from.", examples = "Przemyśl")
    private String nearestCity;

    @Schema(description = "ISO 3166-1 alpha-2 country code of the nearest city.", examples = "PL")
    private String countryCode;

    @Schema(description = "Approximate distance to the nearest city, in kilometers.", examples = "12.4")
    private Double distanceKm;
}

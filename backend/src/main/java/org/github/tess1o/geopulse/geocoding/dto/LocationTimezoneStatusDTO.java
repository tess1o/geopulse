package org.github.tess1o.geopulse.geocoding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Whether location-local times are available on this server.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LocationTimezoneStatusDTO(
        @Schema(description = "The feature is enabled on this server (`geopulse.timezone.location.enabled`).")
        boolean enabled,
        @Schema(description = "Enabled, and GeoNames cities are loaded so timezones can be resolved.")
        boolean available,
        @Schema(description = "ISO 3166-1 alpha-2 country of your profile timezone, used to tell places abroad from "
                + "home. Absent when it can't be determined (for example a UTC profile timezone).", examples = "UA")
        String profileCountryCode
) {
}

package org.github.tess1o.geopulse.geocoding.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.geocoding.dto.LocationTimezoneStatusDTO;
import org.github.tess1o.geopulse.geocoding.service.LocationTimezoneService;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;

@Path("/location-timezones")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = ApiTags.GEOCODING)
public class LocationTimezoneResource {

    @Inject
    LocationTimezoneService locationTimezoneService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    @Path("/status")
    @Operation(summary = "Get location timezone availability",
            description = "Whether this server can show times in the local timezone of each location: the feature "
                    + "is enabled and GeoNames cities are loaded.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public LocationTimezoneStatusDTO getStatus() {
        boolean available = locationTimezoneService.isAvailable();
        String profileCountryCode = available
                ? locationTimezoneService.findCountryForTimezone(currentUserService.getCurrentUser().getTimezone())
                        .orElse(null)
                : null;
        return new LocationTimezoneStatusDTO(locationTimezoneService.isEnabled(), available, profileCountryCode);
    }
}

package org.github.tess1o.geopulse.trips.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.trips.model.dto.PlanSearchResponseDto;
import org.github.tess1o.geopulse.shared.api.ApiPaths;
import org.github.tess1o.geopulse.trips.service.TripPlanSearchService;

import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TRIP_SEARCH;

@Path(ApiPaths.TRIP_PLANNING + "/searches")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = ApiTags.TRIP_PLANNING)
public class PlanSearchResource {

    private final CurrentUserService currentUserService;
    private final TripPlanSearchService tripPlanSearchService;

    public PlanSearchResource(CurrentUserService currentUserService,
                              TripPlanSearchService tripPlanSearchService) {
        this.currentUserService = currentUserService;
        this.tripPlanSearchService = tripPlanSearchService;
    }

    @GET
    @Operation(summary = "Search places to plan",
            description = "Searches by name for places to add to a trip plan: the user's favorites, previously "
                    + "geocoded locations, and results from the external geocoding provider. Pass coordinates to "
                    + "prefer results near them. The response says whether the external search worked, so a "
                    + "provider problem is not mistaken for no results.")
    public PlanSearchResponseDto search(
            @Parameter(description = "Search text, at least 2 characters.", example = "Louvre")
            @QueryParam("q") String query,
            @Parameter(description = "Latitude to bias results toward. Use together with `longitude`.")
            @QueryParam("latitude") Double latitude,
            @Parameter(description = "Longitude to bias results toward.")
            @QueryParam("longitude") Double longitude,
            @Parameter(description = "Maximum number of results.")
            @QueryParam("limit") Integer limit) {
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.length() < 2) {
            throw new GeoPulseException(INVALID_TRIP_SEARCH, "q must be at least 2 characters", Map.of("minLength", 2));
        }

        if ((latitude == null) != (longitude == null)) {
            throw new GeoPulseException(INVALID_TRIP_SEARCH, "lat and lon must be provided together");
        }

        if (latitude != null && (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)) {
            throw new GeoPulseException(INVALID_TRIP_SEARCH, "Invalid lat/lon values");
        }

        return tripPlanSearchService.search(
                currentUserService.getCurrentUserId(), safeQuery, latitude, longitude, limit);
    }
}

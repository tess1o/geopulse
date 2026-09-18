package org.github.tess1o.geopulse.trips.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.trips.model.dto.PlanSearchResultDto;
import org.github.tess1o.geopulse.shared.api.ApiPaths;
import org.github.tess1o.geopulse.trips.service.TripPlanSearchService;

import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TRIP_SEARCH;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path(ApiPaths.TRIP_PLANNING + "/searches")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Trips and Planning", description = "Search trip plan candidates.")
public class PlanSearchResource {

    private final CurrentUserService currentUserService;
    private final TripPlanSearchService tripPlanSearchService;

    public PlanSearchResource(CurrentUserService currentUserService,
                              TripPlanSearchService tripPlanSearchService) {
        this.currentUserService = currentUserService;
        this.tripPlanSearchService = tripPlanSearchService;
    }

    @GET
    public List<PlanSearchResultDto> search(@QueryParam("q") String query,
                           @QueryParam("latitude") Double latitude,
                           @QueryParam("longitude") Double longitude,
                           @QueryParam("limit") Integer limit) {
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.length() < 2) {
            throw problem(INVALID_TRIP_SEARCH, "q must be at least 2 characters", Map.of("minLength", 2));
        }

        if ((latitude == null) != (longitude == null)) {
            throw problem(INVALID_TRIP_SEARCH, "lat and lon must be provided together");
        }

        if (latitude != null && (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)) {
            throw problem(INVALID_TRIP_SEARCH, "Invalid lat/lon values");
        }

        return tripPlanSearchService.search(
                currentUserService.getCurrentUserId(), safeQuery, latitude, longitude, limit);
    }
}

package org.github.tess1o.geopulse.trips.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.trips.model.dto.PlanSearchResultDto;
import org.github.tess1o.geopulse.trips.service.TripPlanSearchService;

import java.util.List;
import java.util.UUID;

@Path("/api/trips/plan-search")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
public class PlanSearchResource {

    private final CurrentUserService currentUserService;
    private final TripPlanSearchService tripPlanSearchService;

    public PlanSearchResource(CurrentUserService currentUserService,
                              TripPlanSearchService tripPlanSearchService) {
        this.currentUserService = currentUserService;
        this.tripPlanSearchService = tripPlanSearchService;
    }

    @GET
    public Response search(@QueryParam("q") String query,
                           @QueryParam("lat") Double latitude,
                           @QueryParam("lon") Double longitude,
                           @QueryParam("limit") Integer limit) {
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.length() < 2) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("q must be at least 2 characters"))
                    .build();
        }

        if ((latitude == null) != (longitude == null)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("lat and lon must be provided together"))
                    .build();
        }

        if (latitude != null && (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid lat/lon values"))
                    .build();
        }

        try {
            UUID userId = currentUserService.getCurrentUserId();
            List<PlanSearchResultDto> results = tripPlanSearchService.search(userId, safeQuery, latitude, longitude, limit);
            return Response.ok(ApiResponse.success(results)).build();
        } catch (Exception e) {
            log.error("Failed plan-search for query='{}'", safeQuery, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to search places"))
                    .build();
        }
    }
}

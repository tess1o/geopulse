package org.github.tess1o.geopulse.weather.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.weather.dto.WeatherBackfillRequest;
import org.github.tess1o.geopulse.weather.service.WeatherService;

@Path("/api/admin/weather")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@RequestScoped
@Slf4j
public class AdminWeatherResource {

    @Inject
    WeatherService weatherService;

    @POST
    @Path("/backfill")
    public Response backfill(WeatherBackfillRequest request) {
        try {
            return Response.ok(ApiResponse.success(weatherService.discoverAdminBackfillTargets(request))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to discover admin weather backfill targets", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to discover weather backfill targets"))
                    .build();
        }
    }

    @GET
    @Path("/status")
    public Response status() {
        return Response.ok(ApiResponse.success(weatherService.status())).build();
    }
}

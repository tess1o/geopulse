package org.github.tess1o.geopulse.weather.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.security.SecurityRoles;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.weather.dto.WeatherBackfillRequest;
import org.github.tess1o.geopulse.weather.dto.WeatherWorkAcceptedResponse;
import org.github.tess1o.geopulse.weather.service.WeatherPipelineWorker;
import org.github.tess1o.geopulse.weather.service.WeatherStatusService;
import org.github.tess1o.geopulse.weather.service.WeatherService;

@Path("/api/admin/weather")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Slf4j
@Tag(name = "Admin: Weather", description = "Backfill weather samples")
public class AdminWeatherResource {

    @Inject
    WeatherService weatherService;

    @Inject
    WeatherPipelineWorker weatherPipelineWorker;

    @Inject
    WeatherStatusService weatherStatusService;

    @POST
    @Path("/backfill")
    @RolesAllowed(SecurityRoles.ADMIN)
    public Response backfill(WeatherBackfillRequest request) {
        try {
            int queued = weatherService.queueAdminBackfill(request);
            WeatherWorkAcceptedResponse accepted = weatherPipelineWorker.wake("admin backfill");
            accepted.setQueuedUserRanges(queued);
            return Response.status(Response.Status.ACCEPTED).entity(ApiResponse.success(accepted)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to queue admin weather backfill range", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to queue weather backfill range"))
                    .build();
        }
    }

    @GET
    @Path("/status")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.DEMO_ADMIN_READ})
    public Response status() {
        return Response.ok(ApiResponse.success(weatherStatusService.status())).build();
    }

    @POST
    @Path("/process-now")
    @RolesAllowed(SecurityRoles.ADMIN)
    public Response processNow() {
        return Response.status(Response.Status.ACCEPTED)
                .entity(ApiResponse.success(weatherPipelineWorker.wake("admin resume processing")))
                .build();
    }
}

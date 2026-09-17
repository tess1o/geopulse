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
import org.github.tess1o.geopulse.weather.dto.WeatherBackfillRequest;
import org.github.tess1o.geopulse.weather.dto.WeatherWorkAcceptedResponse;
import org.github.tess1o.geopulse.weather.dto.WeatherStatusResponse;
import org.github.tess1o.geopulse.weather.service.WeatherPipelineWorker;
import org.github.tess1o.geopulse.weather.service.WeatherStatusService;
import org.jboss.resteasy.reactive.RestResponse;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;
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
    public RestResponse<WeatherWorkAcceptedResponse> backfill(WeatherBackfillRequest request) {
        try {
            int queued = weatherService.queueAdminBackfill(request);
            WeatherWorkAcceptedResponse accepted = weatherPipelineWorker.wake("admin backfill");
            accepted.setQueuedUserRanges(queued);
            return RestResponse.status(Response.Status.ACCEPTED, accepted);
        } catch (IllegalArgumentException e) {
            throw problem(WEATHER_BACKFILL_INVALID, e.getMessage());
        } catch (NotFoundException e) {
            throw problem(WEATHER_TARGET_NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to queue admin weather backfill range", e);
            throw problem(INTERNAL_ERROR, "Failed to queue weather backfill range");
        }
    }

    @GET
    @Path("/status")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.DEMO_ADMIN_READ})
    public WeatherStatusResponse status() {
        return weatherStatusService.status();
    }

    @POST
    @Path("/process-now")
    @RolesAllowed(SecurityRoles.ADMIN)
    public RestResponse<WeatherWorkAcceptedResponse> processNow() {
        return RestResponse.status(Response.Status.ACCEPTED,
                weatherPipelineWorker.wake("admin resume processing"));
    }
}

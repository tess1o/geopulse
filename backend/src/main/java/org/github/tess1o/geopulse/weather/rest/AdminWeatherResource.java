package org.github.tess1o.geopulse.weather.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.auth.security.SecurityRoles;
import org.github.tess1o.geopulse.weather.dto.WeatherBackfillRequest;
import org.github.tess1o.geopulse.weather.dto.WeatherWorkAcceptedResponse;
import org.github.tess1o.geopulse.weather.dto.WeatherStatusResponse;
import org.github.tess1o.geopulse.weather.service.WeatherPipelineWorker;
import org.github.tess1o.geopulse.weather.service.WeatherStatusService;
import org.jboss.resteasy.reactive.RestResponse;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;
import org.github.tess1o.geopulse.weather.service.WeatherService;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/admin/weather")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Slf4j
@Tag(name = ApiTags.ADMIN_WEATHER)
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
    @Operation(summary = "Backfill weather data",
            description = "Queues fetching historical weather for GPS data in a time range, for one user (`userId`) "
                    + "or for all users, and wakes the weather worker. Returns `202 Accepted` with the number of "
                    + "queued user ranges.")
    public RestResponse<WeatherWorkAcceptedResponse> backfill(WeatherBackfillRequest request) {
        try {
            int queued = weatherService.queueAdminBackfill(request);
            WeatherWorkAcceptedResponse accepted = weatherPipelineWorker.wake("admin backfill");
            accepted.setQueuedUserRanges(queued);
            return RestResponse.status(Response.Status.ACCEPTED, accepted);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(WEATHER_BACKFILL_INVALID, WEATHER_BACKFILL_INVALID.title(), e);
        } catch (NotFoundException e) {
            throw new GeoPulseException(WEATHER_TARGET_NOT_FOUND, WEATHER_TARGET_NOT_FOUND.title(), e);
        }
    }

    @GET
    @Path("/status")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.DEMO_ADMIN_READ})
    @Operation(summary = "Get weather pipeline status",
            description = "Returns the state of the weather pipeline: provider, daily request quota and usage, "
                    + "queued work by status, and why fetching is blocked, if it is.")
    public WeatherStatusResponse status() {
        return weatherStatusService.status();
    }

    @POST
    @Path("/process-now")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Operation(summary = "Process the weather queue now",
            description = "Wakes the weather worker to process queued work immediately, for example after a provider "
                    + "quota resets.")
    public RestResponse<WeatherWorkAcceptedResponse> processNow() {
        return RestResponse.status(Response.Status.ACCEPTED,
                weatherPipelineWorker.wake("admin resume processing"));
    }
}

package org.github.tess1o.geopulse.weather.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.weather.service.WeatherService;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@Path("/api/weather")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@RequestScoped
@Slf4j
public class WeatherResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    WeatherService weatherService;

    @GET
    @Path("/samples")
    public Response getSamples(@QueryParam("startTime") String startTime,
                               @QueryParam("endTime") String endTime,
                               @QueryParam("minLat") Double minLat,
                               @QueryParam("minLon") Double minLon,
                               @QueryParam("maxLat") Double maxLat,
                               @QueryParam("maxLon") Double maxLon) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            Instant start = startTime != null ? Instant.parse(startTime) : Instant.EPOCH;
            Instant end = endTime != null ? Instant.parse(endTime) : Instant.now();
            if (start.isAfter(end)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("Start time must be before end time"))
                        .build();
            }

            return Response.ok(ApiResponse.success(weatherService.findSamples(
                    userId, start, end, minLat, minLon, maxLat, maxLon))).build();
        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid time format. Use ISO-8601 format."))
                    .build();
        } catch (Exception e) {
            log.error("Failed to load weather samples for user {}", userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to load weather samples"))
                    .build();
        }
    }

    @GET
    @Path("/status")
    public Response getStatus() {
        return Response.ok(ApiResponse.success(weatherService.status())).build();
    }
}

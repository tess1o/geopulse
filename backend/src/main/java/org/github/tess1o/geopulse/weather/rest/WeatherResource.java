package org.github.tess1o.geopulse.weather.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.weather.dto.WeatherIntegrationStatusResponse;
import org.github.tess1o.geopulse.weather.dto.WeatherSamplesResponse;
import org.github.tess1o.geopulse.weather.service.WeatherService;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_DATE_RANGE;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/weather")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@RequestScoped
@Tag(name = "User: Weather", description = "Get Weather samples")
public class WeatherResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    WeatherService weatherService;

    @GET
    @Path("/samples")
    public WeatherSamplesResponse getSamples(@QueryParam("from") String startTime,
                                             @QueryParam("to") String endTime,
                                             @QueryParam("minLat") Double minLat,
                                             @QueryParam("minLon") Double minLon,
                                             @QueryParam("maxLat") Double maxLat,
                                             @QueryParam("maxLon") Double maxLon) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            Instant start = startTime != null ? Instant.parse(startTime) : Instant.EPOCH;
            Instant end = endTime != null ? Instant.parse(endTime) : Instant.now();
            if (start.isAfter(end)) {
                throw problem(INVALID_DATE_RANGE, "Start time must be before end time");
            }

            return weatherService.findSamples(userId, start, end, minLat, minLon, maxLat, maxLon);
        } catch (DateTimeParseException e) {
            throw problem(INVALID_DATE_RANGE, "Times must use ISO-8601 format",
                    Map.of("fields", "startTime,endTime"));
        }
    }

    @GET
    @Path("/status")
    public WeatherIntegrationStatusResponse getStatus() {
        return weatherService.integrationStatus();
    }
}

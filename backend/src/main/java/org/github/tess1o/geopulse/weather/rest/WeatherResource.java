package org.github.tess1o.geopulse.weather.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.weather.dto.WeatherIntegrationStatusResponse;
import org.github.tess1o.geopulse.weather.dto.WeatherSamplesResponse;
import org.github.tess1o.geopulse.weather.service.WeatherService;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_DATE_RANGE;

@Path("/weather")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@RequestScoped
@Tag(name = ApiTags.WEATHER)
public class WeatherResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    WeatherService weatherService;

    @GET
    @Path("/samples")
    @Operation(summary = "Get weather samples",
            description = "Returns the weather samples recorded for the signed-in user's GPS data in a time range, "
                    + "optionally inside a bounding box, with units and provider attribution. Empty when the "
                    + "weather integration is disabled.")
    public WeatherSamplesResponse getSamples(
            @Parameter(description = "Start of the time range, as an ISO-8601 instant. Defaults to the earliest data.",
                    example = "2025-06-01T00:00:00Z")
            @QueryParam("from") String startTime,
            @Parameter(description = "End of the time range, as an ISO-8601 instant. Defaults to now.",
                    example = "2025-06-30T23:59:59Z")
            @QueryParam("to") String endTime,
            @Parameter(description = "Southern edge of the bounding box, in degrees.")
            @QueryParam("minLat") Double minLat,
            @Parameter(description = "Western edge of the bounding box, in degrees.")
            @QueryParam("minLon") Double minLon,
            @Parameter(description = "Northern edge of the bounding box, in degrees.")
            @QueryParam("maxLat") Double maxLat,
            @Parameter(description = "Eastern edge of the bounding box, in degrees.")
            @QueryParam("maxLon") Double maxLon) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            Instant start = startTime != null ? Instant.parse(startTime) : Instant.EPOCH;
            Instant end = endTime != null ? Instant.parse(endTime) : Instant.now();
            if (start.isAfter(end)) {
                throw new GeoPulseException(INVALID_DATE_RANGE, "Start time must be before end time");
            }

            return weatherService.findSamples(userId, start, end, minLat, minLon, maxLat, maxLon);
        } catch (DateTimeParseException e) {
            throw new GeoPulseException(INVALID_DATE_RANGE, "Times must use ISO-8601 format",
                    Map.of("fields", "startTime,endTime"), e);
        }
    }

    @GET
    @Path("/status")
    @Operation(summary = "Get weather integration status",
            description = "Returns whether the weather integration is enabled and configured on this server, and "
                    + "which provider it uses.")
    public WeatherIntegrationStatusResponse getStatus() {
        return weatherService.integrationStatus();
    }
}

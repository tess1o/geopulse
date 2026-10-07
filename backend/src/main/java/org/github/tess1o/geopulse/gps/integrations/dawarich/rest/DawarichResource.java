package org.github.tess1o.geopulse.gps.integrations.dawarich.rest;

import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.AUTHENTICATION_REQUIRED;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.point.DawarichPayload;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.stats.DawarichMonthlyDistanceKm;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.stats.DawarichStatsResponse;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.stats.DawarichYearlyStats;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.gps.service.auth.GpsIntegrationAuthenticatorRegistry;
import org.github.tess1o.geopulse.shared.api.ApiPaths;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.github.tess1o.geopulse.shared.openapi.ApiSecuritySchemes;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.github.tess1o.geopulse.shared.openapi.GpsIngestExamples;


@Path(ApiPaths.GPS_INGEST + "/dawarich")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = ApiTags.GPS_TRACKER_INGEST)
@SecurityRequirement(name = ApiSecuritySchemes.GPS_SOURCE_TOKEN)
public class DawarichResource {

    private final GpsPointService gpsPointService;
    private final GpsIntegrationAuthenticatorRegistry authRegistry;


    public DawarichResource(GpsPointService gpsPointService, GpsIntegrationAuthenticatorRegistry authRegistry) {
        this.gpsPointService = gpsPointService;
        this.authRegistry = authRegistry;
    }

    @GET
    @Path("/health")
    @Operation(summary = "Check Dawarich API health",
            description = "Dawarich-compatible health check that clients call to test the server connection. Always "
                    + "returns `200`; the `X-Dawarich-Response` header says whether the supplied token is valid.")
    @APIResponse(responseCode = "200", description = "Dawarich-compatible health response",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(type = SchemaType.OBJECT)))
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public Response handleDawarichHealth(Request request, @HeaderParam("Authorization") String authHeader) {
        log.debug("Received Dawarich health request");
        var authenticated = authRegistry.authenticate(GpsSourceType.DAWARICH, authHeader);
        String dawarichResponse = authenticated.isPresent() ? "Hey, I'm alive and authenticated!" : "Hey, I'm alive!";
        return Response
                .status(Response.Status.OK)
                .header("X-Dawarich-Response", dawarichResponse)
                .header("X-Dawarich-Version", "0.30.8")
                .entity("{\"status\": \"ok\"}")
                .build();
    }

    @POST
    @Path("/points")
    @Operation(summary = "Ingest Dawarich points",
            description = "Receives a batch of points in the Dawarich `/api/v1/points` format and stores them as GPS "
                    + "points for the user who owns the Dawarich GPS source. Lets apps that support Dawarich send "
                    + "data to GeoPulse.")
    @APIResponse(responseCode = "200", description = "Points accepted")
    public Response handleDawarichGet(@RequestBody(content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(name = "points", value = GpsIngestExamples.DAWARICH)))
                                      DawarichPayload payload, @HeaderParam("Authorization") String authHeader) {
        long started = System.nanoTime();
        var authResult = authRegistry.authenticate(GpsSourceType.DAWARICH, authHeader);
        if (authResult.isEmpty()) {
            throw new GeoPulseException(AUTHENTICATION_REQUIRED, "Authentication required");
        }
        UUID userId = authResult.get().getUserId();
        var config = authResult.get().getConfig();
        var summary = gpsPointService.saveDarawichGpsPoints(payload, userId, GpsSourceType.DAWARICH, config);
        if (summary != null) summary.logCompletion(GpsSourceType.DAWARICH, started);
        return Response.ok().build();
    }

    @GET
    @Path("/stats")
    @SecurityRequirement(name = ApiSecuritySchemes.GPS_SOURCE_QUERY_KEY)
    @Operation(summary = "Get Dawarich stats",
            description = "Dawarich-compatible statistics endpoint used by some clients to verify the token. The "
                    + "returned numbers are placeholders, not real statistics.")
    @APIResponseSchema(value = DawarichStatsResponse.class, responseCode = "200",
            responseDescription = "Dawarich-compatible statistics")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public Response handleDawarichStats(@Parameter(description = "Token of the Dawarich GPS source.")
                                        @QueryParam("api_key") String apiKey) {
        var authResult = authRegistry.authenticate(GpsSourceType.DAWARICH, apiKey);
        if (authResult.isEmpty()) {
            throw new GeoPulseException(AUTHENTICATION_REQUIRED, "Authentication required");
        }

        //TODO: implement proper stats, probably use stats service
        DawarichStatsResponse statsResponse = DawarichStatsResponse.builder()
                .totalDistanceKm(1000)
                .totalCitiesVisited(2)
                .totalReverseGeocodedPoints(200)
                .totalCountriesVisited(1)
                .yearlyStats(List.of(
                        DawarichYearlyStats.builder()
                                .year(2025)
                                .totalCitiesVisited(1)
                                .totalCountriesVisited(2)
                                .totalDistanceKm(100)
                                .monthlyDistanceKm(DawarichMonthlyDistanceKm.builder()
                                        .april(200)
                                        .june(100)
                                        .build())
                                .build()
                ))
                .build();

        return Response
                .status(Response.Status.OK)
                .header("Content-Type", "application/json")
                .entity(statsResponse)
                .build();
    }
}

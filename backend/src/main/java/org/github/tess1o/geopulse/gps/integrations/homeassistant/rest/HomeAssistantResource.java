package org.github.tess1o.geopulse.gps.integrations.homeassistant.rest;

import org.github.tess1o.geopulse.gpssource.service.GpsSourceIngestStatusService;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.AUTHENTICATION_REQUIRED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.PAYLOAD_TOO_LARGE;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.homeassistant.model.HomeAssistantGpsData;
import org.github.tess1o.geopulse.gps.model.GpsIngestSummaryResponse;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.gps.service.auth.GpsIntegrationAuthenticatorRegistry;
import org.github.tess1o.geopulse.shared.api.ApiPaths;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.github.tess1o.geopulse.shared.openapi.ApiSecuritySchemes;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.github.tess1o.geopulse.shared.openapi.GpsIngestExamples;

@Path(ApiPaths.GPS_INGEST + "/home-assistant")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = ApiTags.GPS_TRACKER_INGEST)
@SecurityRequirement(name = ApiSecuritySchemes.GPS_SOURCE_TOKEN)
public class HomeAssistantResource {
    private final GpsPointService gpsPointService;
    private final GpsIntegrationAuthenticatorRegistry authRegistry;
    private final GpsSourceIngestStatusService ingestStatusService;

    public HomeAssistantResource(GpsPointService gpsPointService, GpsIntegrationAuthenticatorRegistry authRegistry,
                                 GpsSourceIngestStatusService ingestStatusService) {
        this.gpsPointService = gpsPointService;
        this.authRegistry = authRegistry;
        this.ingestStatusService = ingestStatusService;
    }

    @POST
    @Operation(summary = "Ingest Home Assistant location",
            description = "Receives a location update sent by a Home Assistant automation (REST command) and stores "
                    + "it as a GPS point for the user who owns the Home Assistant GPS source.")
    @APIResponse(responseCode = "200", description = "Location accepted")
    public Response handleHA(@RequestBody(content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(name = "location", value = GpsIngestExamples.HOME_ASSISTANT)))
                             HomeAssistantGpsData data, @HeaderParam("Authorization") String authToken) {
        long started = System.nanoTime();
        var authResult = authRegistry.authenticate(GpsSourceType.HOME_ASSISTANT, authToken);
        if (authResult.isEmpty()) {
            throw new GeoPulseException(AUTHENTICATION_REQUIRED, "Authentication required");
        }

        UUID userId = authResult.get().getUserId();
        var config = authResult.get().getConfig();
        var summary = gpsPointService.saveHomeAssitantGpsPoint(data, userId, GpsSourceType.HOME_ASSISTANT, config);
        if (summary != null) summary.logCompletion(GpsSourceType.HOME_ASSISTANT, started);
        ingestStatusService.recordIngest(config, summary);
        return Response.ok().build();
    }

    @POST
    @Path("/batch")
    @Operation(summary = "Ingest Home Assistant locations in bulk",
            description = "Receives up to " + GpsPointService.HOME_ASSISTANT_BATCH_MAX_POINTS + " location updates in "
                    + "one request, for example points a Home Assistant integration queued while GeoPulse was "
                    + "unreachable. Every item uses the single-location format and must include a timestamp; items "
                    + "without one are skipped. Returns how many points were stored and skipped.")
    @APIResponse(responseCode = "200", description = "Batch processed",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = GpsIngestSummaryResponse.class)))
    @APIResponse(responseCode = "413", description = "More than " + GpsPointService.HOME_ASSISTANT_BATCH_MAX_POINTS
            + " locations in one request")
    public GpsIngestSummaryResponse handleHABatch(@RequestBody(content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(name = "locations", value = GpsIngestExamples.HOME_ASSISTANT_BATCH)))
                                                  List<HomeAssistantGpsData> data,
                                                  @HeaderParam("Authorization") String authToken) {
        long started = System.nanoTime();
        var authResult = authRegistry.authenticate(GpsSourceType.HOME_ASSISTANT, authToken);
        if (authResult.isEmpty()) {
            throw new GeoPulseException(AUTHENTICATION_REQUIRED, "Authentication required");
        }
        if (data != null && data.size() > GpsPointService.HOME_ASSISTANT_BATCH_MAX_POINTS) {
            throw new GeoPulseException(PAYLOAD_TOO_LARGE, "Too many locations in one request",
                    Map.of("maxPoints", GpsPointService.HOME_ASSISTANT_BATCH_MAX_POINTS, "received", data.size()));
        }

        UUID userId = authResult.get().getUserId();
        var config = authResult.get().getConfig();
        var summary = gpsPointService.saveHomeAssistantGpsPoints(data, userId, GpsSourceType.HOME_ASSISTANT, config);
        summary.logCompletion(GpsSourceType.HOME_ASSISTANT, started);
        ingestStatusService.recordIngest(config, summary);
        return GpsIngestSummaryResponse.from(summary);
    }
}
